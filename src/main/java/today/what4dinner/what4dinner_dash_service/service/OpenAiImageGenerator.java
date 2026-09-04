package today.what4dinner.what4dinner_dash_service.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import today.what4dinner.what4dinner_dash_service.dto.GeneratedImage;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * {@code DishImageGenerator} backed by OpenAI's images API.
 *
 * <p>Plain {@link HttpClient} rather than a vendor SDK: one JSON POST does not justify another
 * dependency, and {@code XiaohongshuFetcher} already established the pattern in this codebase.
 * Unlike that class there is no SSRF guard here, and none is needed — the endpoint is a
 * compile-time constant, not user input. The bounded read and explicit timeout still apply.
 *
 * <p>Constructed by {@code ImageGenConfig}, which returns {@code null} when no API key is set,
 * so an unconfigured deployment answers 503 instead of failing to start.
 */
public class OpenAiImageGenerator implements DishImageGenerator {

    private static final Logger log = LoggerFactory.getLogger(OpenAiImageGenerator.class);

    private static final URI ENDPOINT = URI.create("https://api.openai.com/v1/images/generations");

    /** Generous: a single image routinely takes the better part of a minute. */
    private static final int MAX_RESPONSE_BYTES = 32 * 1024 * 1024;

    private static final Map<String, String> CONTENT_TYPES = Map.of(
            "png", "image/png",
            "jpeg", "image/jpeg",
            "webp", "image/webp");

    private static final JsonMapper jsonMapper = JsonMapper.builder().build();

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private final String apiKey;

    private final String model;

    private final String size;

    private final String quality;

    private final String outputFormat;

    private final Duration timeout;

    public OpenAiImageGenerator(String apiKey, String model, String size, String quality,
                                String outputFormat, long timeoutSeconds) {
        this.apiKey = apiKey;
        this.model = model;
        this.size = size;
        this.quality = quality;
        this.outputFormat = outputFormat;
        this.timeout = Duration.ofSeconds(timeoutSeconds);
    }

    @Override
    public String modelName() {
        return model;
    }

    @Override
    public GeneratedImage generate(String prompt) {
        // Serialised rather than concatenated: the prompt is assembled from model output and
        // recipe text, so it will contain quotes and newlines that must not break the body.
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("prompt", prompt);
        body.put("n", 1);
        body.put("size", size);
        body.put("quality", quality);
        body.put("output_format", outputFormat);

        HttpRequest request = HttpRequest.newBuilder(ENDPOINT)
                .timeout(timeout)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(
                        jsonMapper.writeValueAsString(body), StandardCharsets.UTF_8))
                .build();

        HttpResponse<InputStream> response;
        try {
            response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Could not reach the image model: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Image generation interrupted", e);
        }

        byte[] raw = readBounded(response);
        if (response.statusCode() != 200) {
            // The body carries the provider's reason; it is the only useful thing in a failure
            // and it ends up on the image row, so keep it rather than flattening to a status.
            String detail = new String(raw, StandardCharsets.UTF_8);
            log.warn("Image model returned HTTP {}: {}", response.statusCode(),
                    detail.length() > 500 ? detail.substring(0, 500) : detail);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Image model returned HTTP " + response.statusCode() + ": " + summarise(raw));
        }

        JsonNode node = jsonMapper.readTree(raw).path("data").path(0).path("b64_json");
        if (node.isMissingNode() || node.isNull() || !node.isString()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Image model returned no image data");
        }
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(node.stringValue());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Image model returned unreadable image data", e);
        }
        String contentType = CONTENT_TYPES.get(outputFormat);
        if (contentType == null) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Unsupported image-gen output format: " + outputFormat);
        }
        log.info("Generated a {} byte {} image with {}", bytes.length, contentType, model);
        return new GeneratedImage(bytes, contentType);
    }

    /** Pulls the provider's own error message out, so the row says why rather than just "502". */
    private static String summarise(byte[] raw) {
        try {
            JsonNode message = jsonMapper.readTree(raw).path("error").path("message");
            if (message.isString()) {
                return message.stringValue();
            }
        } catch (RuntimeException ignored) {
            // Not JSON, or not the shape we expected - fall through to the generic text.
        }
        return "no details";
    }

    private static byte[] readBounded(HttpResponse<InputStream> response) {
        try (InputStream in = response.body()) {
            byte[] body = in.readNBytes(MAX_RESPONSE_BYTES + 1);
            if (body.length > MAX_RESPONSE_BYTES) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                        "Image model response was larger than " + MAX_RESPONSE_BYTES + " bytes");
            }
            return body;
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Could not read the image model response: " + e.getMessage(), e);
        }
    }
}
