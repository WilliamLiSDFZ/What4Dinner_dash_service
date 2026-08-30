package today.what4dinner.what4dinner_dash_service.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import today.what4dinner.what4dinner_dash_service.dto.FetchedPost;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pulls the text and photos of a Xiaohongshu post so the AI pipeline can turn it into a recipe.
 *
 * <p>Only Xiaohongshu, deliberately. A fetcher interface with a per-site registry would be one
 * implementation pretending to be an abstraction; the second site is when the shape becomes
 * knowable.
 *
 * <p>No headless browser, no cookies, no signed X-s/X-t headers are needed: the post title and
 * the entire body come back in the OpenGraph meta tags, and the photo URLs sit in the inline
 * {@code __INITIAL_STATE__} blob. The photo CDN serves those URLs to anonymous clients.
 *
 * <p><b>Every outbound URL here originates from user input</b>, so each one is re-validated
 * against a host allowlist immediately before the request — including after every redirect hop.
 */
@Service
public class XiaohongshuFetcher {

    private static final Logger log = LoggerFactory.getLogger(XiaohongshuFetcher.class);

    /** Hosts allowed to serve a post page. */
    private static final Set<String> PAGE_HOSTS = Set.of("xiaohongshu.com", "xhslink.com");

    /** Hosts allowed to serve photo bytes. */
    private static final Set<String> IMAGE_HOSTS = Set.of("xhscdn.com", "xiaohongshu.com");

    /** The app share sheet emits a short link; the web one emits the full URL. Both land here. */
    private static final Pattern URL_IN_TEXT = Pattern.compile("https?://[^\\s一-鿿]+");

    /** Trailing punctuation a share blob tends to glue onto the end of the link. */
    private static final Pattern TRAILING_JUNK = Pattern.compile("[\\s,.;:!?'\")\\]}>，。；：！？、）】》]+$");

    private static final Pattern OG_TITLE =
            Pattern.compile("<meta[^>]+property=\"og:title\"[^>]+content=\"(.*?)\"", Pattern.DOTALL);

    private static final Pattern OG_DESCRIPTION =
            Pattern.compile("<meta[^>]+property=\"og:description\"[^>]+content=\"(.*?)\"", Pattern.DOTALL);

    /**
     * The full-size variant of each photo. {@code WB_PRV} is the low-resolution preview of the
     * same image, so matching only {@code WB_DFT} keeps one URL per photo without deduping.
     */
    private static final Pattern IMAGE_URL =
            Pattern.compile("\"imageScene\":\"WB_DFT\",\"url\":\"(.*?)\"");

    /** Response content types we will accept a photo as, mapped to what GCS gets told. */
    private static final Map<String, String> IMAGE_TYPES = Map.of(
            "image/jpeg", "image/jpeg",
            "image/jpg", "image/jpeg",
            "image/png", "image/png",
            "image/webp", "image/webp");

    private static final int MAX_REDIRECTS = 5;

    private static final int MAX_HTML_BYTES = 5 * 1024 * 1024;

    private final HttpClient http;

    @Value("${share-import.timeout-seconds:15}")
    private long timeoutSeconds;

    @Value("${share-import.max-images:10}")
    private int maxImages;

    @Value("${share-import.max-image-bytes:8388608}")
    private int maxImageBytes;

    @Value("${share-import.user-agent:Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) "
            + "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36}")
    private String userAgent;

    public XiaohongshuFetcher() {
        // NEVER, not NORMAL: a redirect followed by the client is a redirect the allowlist never
        // sees. Every hop is walked by hand below so it can be re-validated.
        this.http = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    /**
     * @param shareText a share link, or the whole share blob with a link somewhere inside it
     * @throws ResponseStatusException 400 if no usable link is present; 422 if the post cannot
     *         be read (expired token, deleted post, or a page shape we no longer recognise)
     */
    public FetchedPost fetch(String shareText) {
        if (shareText == null || shareText.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "shareText is required");
        }
        Page page = get(extractUrl(shareText), PAGE_HOSTS, MAX_HTML_BYTES);
        String html = new String(page.body, StandardCharsets.UTF_8);

        // Xiaohongshu answers 200 for a note it will not show you and redirects to its own 404
        // page, so the status code proves nothing. The OpenGraph block is the real signal.
        String title = firstGroup(OG_TITLE, html);
        String text = firstGroup(OG_DESCRIPTION, html);
        if (text == null || text.isBlank()) {
            log.warn("No og:description at {} - post unreadable", page.uri);
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Could not read that post. The share link may have expired - open it in "
                  + "Xiaohongshu and share it again.");
        }

        List<FetchedPost.FetchedImage> images = downloadImages(imageUrls(html));
        log.info("Fetched post from {}: {} chars of text, {} photo(s)",
                page.uri.getHost(), text.length(), images.size());
        return new FetchedPost(
                stripSiteSuffix(unescapeHtml(title)),
                unescapeHtml(text),
                images);
    }

    /**
     * Checks the link is present and points somewhere we support, without touching the network,
     * so a typo can be answered with a synchronous 400 instead of a task that fails later.
     *
     * <p>Deliberately no DNS here — that belongs on the fetch, off the request thread.
     */
    public void requireSupportedLink(String shareText) {
        if (shareText == null || shareText.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "shareText is required");
        }
        String url = extractUrl(shareText);
        URI uri;
        try {
            uri = new URI(url);
        } catch (URISyntaxException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Malformed link");
        }
        String scheme = uri.getScheme();
        if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Only http and https links are supported");
        }
        String host = uri.getHost();
        if (host == null || !hostAllowed(host.toLowerCase(), PAGE_HOSTS)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Only Xiaohongshu links are supported");
        }
    }

    // ---------------------------------------------------------------- parsing

    String extractUrl(String shareText) {
        Matcher matcher = URL_IN_TEXT.matcher(shareText);
        if (!matcher.find()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "No link found in shareText");
        }
        return TRAILING_JUNK.matcher(matcher.group()).replaceAll("");
    }

    List<String> imageUrls(String html) {
        List<String> urls = new ArrayList<>();
        Matcher matcher = IMAGE_URL.matcher(html);
        while (matcher.find() && urls.size() < maxImages) {
            // The state blob escapes every slash, so the raw match is not yet a URL.
            urls.add(matcher.group(1).replace("\\u002F", "/").replace("\\/", "/"));
        }
        return urls;
    }

    private List<FetchedPost.FetchedImage> downloadImages(List<String> urls) {
        List<FetchedPost.FetchedImage> images = new ArrayList<>();
        for (String url : urls) {
            // One unreadable photo must not cost the whole import: the text alone still makes a
            // recipe, and a missing photo only costs that step its picture.
            try {
                Page page = get(url, IMAGE_HOSTS, maxImageBytes);
                String type = IMAGE_TYPES.get(page.contentType);
                if (type == null) {
                    log.warn("Skipping photo with unusable content type {}", page.contentType);
                    continue;
                }
                images.add(new FetchedPost.FetchedImage(page.body, type));
            } catch (Exception e) {
                log.warn("Skipping photo that failed to download: {}", e.toString());
            }
        }
        return images;
    }

    private static String firstGroup(Pattern pattern, String html) {
        Matcher matcher = pattern.matcher(html);
        return matcher.find() ? matcher.group(1) : null;
    }

    private static String stripSiteSuffix(String title) {
        if (title == null) {
            return null;
        }
        String trimmed = title.trim();
        int marker = trimmed.lastIndexOf(" - 小红书");
        return marker > 0 ? trimmed.substring(0, marker).trim() : trimmed;
    }

    /** Enough of an unescape for attribute text; there is no HTML library on the classpath. */
    static String unescapeHtml(String value) {
        if (value == null || value.indexOf('&') < 0) {
            return value;
        }
        StringBuilder out = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            int end = c == '&' ? value.indexOf(';', i + 1) : -1;
            if (end < 0 || end - i > 10) {
                out.append(c);
                continue;
            }
            String entity = value.substring(i + 1, end);
            String replacement = switch (entity) {
                case "amp" -> "&";
                case "lt" -> "<";
                case "gt" -> ">";
                case "quot" -> "\"";
                case "apos", "#39" -> "'";
                case "nbsp" -> " ";
                default -> numericEntity(entity);
            };
            if (replacement == null) {
                out.append(c);
            } else {
                out.append(replacement);
                i = end;
            }
        }
        return out.toString();
    }

    private static String numericEntity(String entity) {
        try {
            if (entity.startsWith("#x") || entity.startsWith("#X")) {
                return Character.toString(Integer.parseInt(entity.substring(2), 16));
            }
            if (entity.startsWith("#")) {
                return Character.toString(Integer.parseInt(entity.substring(1)));
            }
        } catch (RuntimeException ignored) {
            // Not a numeric entity after all - fall through and leave the text as it was.
        }
        return null;
    }

    // ---------------------------------------------------------------- fetching

    private record Page(URI uri, byte[] body, String contentType) { }

    /** Walks redirects by hand so the allowlist is applied to every hop, not just the first. */
    private Page get(String startUrl, Set<String> allowedHosts, int maxBytes) {
        URI uri = requireAllowed(startUrl, allowedHosts);
        for (int hop = 0; hop <= MAX_REDIRECTS; hop++) {
            HttpResponse<InputStream> response = send(uri);
            int status = response.statusCode();
            if (status >= 300 && status < 400) {
                String location = response.headers().firstValue("Location")
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                                "Link redirected without a target"));
                drain(response);
                uri = requireAllowed(uri.resolve(location).toString(), allowedHosts);
                continue;
            }
            if (status != 200) {
                drain(response);
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "Link returned HTTP " + status);
            }
            String contentType = response.headers().firstValue("Content-Type")
                    .map(v -> v.split(";")[0].trim().toLowerCase())
                    .orElse("");
            return new Page(uri, readBounded(response, maxBytes), contentType);
        }
        throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Too many redirects");
    }

    private HttpResponse<InputStream> send(URI uri) {
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .header("User-Agent", userAgent)
                .header("Accept", "text/html,application/xhtml+xml,image/webp,image/*,*/*;q=0.8")
                .header("Accept-Language", "zh-CN,zh;q=0.9")
                .GET()
                .build();
        try {
            return http.send(request, HttpResponse.BodyHandlers.ofInputStream());
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Could not reach that link: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Fetch interrupted", e);
        }
    }

    /** Reads at most {@code maxBytes}, so a hostile or broken response cannot exhaust the heap. */
    private static byte[] readBounded(HttpResponse<InputStream> response, int maxBytes) {
        try (InputStream in = response.body()) {
            byte[] body = in.readNBytes(maxBytes + 1);
            if (body.length > maxBytes) {
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "Response larger than " + maxBytes + " bytes");
            }
            return body;
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Could not read response: " + e.getMessage(), e);
        }
    }

    private static void drain(HttpResponse<InputStream> response) {
        try (InputStream in = response.body()) {
            in.readNBytes(8192);
        } catch (IOException ignored) {
            // Nothing to do; the connection is being discarded anyway.
        }
    }

    // ---------------------------------------------------------------- SSRF guard

    /**
     * The whole SSRF defence, applied to the initial URL and again to every redirect target.
     *
     * <p>The host allowlist is the load-bearing part: this service runs on GCP, where reaching
     * {@code 169.254.169.254} would hand out a service-account token. The address checks are a
     * second line for the case where an allowlisted name resolves somewhere it should not.
     */
    private static URI requireAllowed(String url, Set<String> allowedHosts) {
        URI uri;
        try {
            uri = new URI(url);
        } catch (URISyntaxException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Malformed link");
        }
        String scheme = uri.getScheme();
        if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only http and https links are supported");
        }
        String host = uri.getHost();
        if (host == null || !hostAllowed(host.toLowerCase(), allowedHosts)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Only Xiaohongshu links are supported");
        }
        for (InetAddress address : resolve(host)) {
            if (address.isLoopbackAddress() || address.isLinkLocalAddress()
                    || address.isSiteLocalAddress() || address.isAnyLocalAddress()
                    || address.isMulticastAddress() || isUniqueLocal(address)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Link resolves to a non-public address");
            }
        }
        return uri;
    }

    /** Suffix match on a label boundary, so {@code notxiaohongshu.com} does not slip through. */
    private static boolean hostAllowed(String host, Set<String> allowedHosts) {
        for (String allowed : allowedHosts) {
            if (host.equals(allowed) || host.endsWith("." + allowed)) {
                return true;
            }
        }
        return false;
    }

    private static InetAddress[] resolve(String host) {
        try {
            return InetAddress.getAllByName(host);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Could not resolve " + host);
        }
    }

    /** fc00::/7 — {@link InetAddress} has no predicate for it. */
    private static boolean isUniqueLocal(InetAddress address) {
        byte[] bytes = address.getAddress();
        return bytes.length == 16 && (bytes[0] & 0xFE) == 0xFC;
    }
}
