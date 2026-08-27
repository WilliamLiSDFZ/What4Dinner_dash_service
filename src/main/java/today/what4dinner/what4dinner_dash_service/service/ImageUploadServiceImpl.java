package today.what4dinner.what4dinner_dash_service.service;

import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.HttpMethod;
import com.google.cloud.storage.Storage;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import today.what4dinner.what4dinner_dash_service.dto.UploadUrlResponse;
import today.what4dinner.what4dinner_dash_service.repository.UserRepository;

import java.net.URL;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class ImageUploadServiceImpl implements ImageUploadService {

    /** Allowed purposes → the path segment used for them. Fixed; never client text. */
    private static final Map<String, String> PURPOSE_SEGMENTS = Map.of(
            "recipe", "recipe",
            "recipe-raw", "recipe-raw",
            "family-background", "family-background",
            "ingredient", "ingredient",
            "step", "step");

    /** Allowed content types → file extension. Fixed; never client text. */
    private static final Map<String, String> EXTENSIONS = Map.of(
            "image/jpeg", "jpg",
            "image/png", "png",
            "image/webp", "webp",
            "image/heic", "heic");

    private final ObjectProvider<Storage> storageProvider;

    private final UserRepository userRepository;

    @Value("${gcs.bucket:}")
    private String bucket;

    @Value("${gcs.signed-url-minutes:15}")
    private long signedUrlMinutes;

    public ImageUploadServiceImpl(ObjectProvider<Storage> storageProvider, UserRepository userRepository) {
        this.storageProvider = storageProvider;
        this.userRepository = userRepository;
    }

    @Override
    public UploadUrlResponse createUploadUrl(UUID userId, String purpose, String contentType) {
        String segment = PURPOSE_SEGMENTS.get(purpose);
        if (segment == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "purpose must be one of " + PURPOSE_SEGMENTS.keySet());
        }
        String extension = EXTENSIONS.get(contentType);
        if (extension == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "contentType must be one of " + EXTENSIONS.keySet());
        }
        Storage storage = storageProvider.getIfAvailable();
        if (storage == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Image upload is not configured");
        }
        UUID familyId = userRepository.findFamilyIdById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unknown user"));

        // Every part is server-controlled: the family from the JWT, a fixed segment from the
        // allowlist, a fresh UUID, and an extension from the allowlist. No client text is
        // concatenated in, so path traversal and cross-family overwrites cannot occur.
        String objectName = "family/%s/%s/%s.%s".formatted(familyId, segment, UUID.randomUUID(), extension);

        BlobInfo blobInfo = BlobInfo.newBuilder(BlobId.of(bucket, objectName))
                .setContentType(contentType)
                .build();
        // Content-Type must go through withExtHeaders, not withContentType(): the latter only
        // feeds the V2 payload, so under V4 it would leave the header unsigned.
        URL url = storage.signUrl(blobInfo, signedUrlMinutes, TimeUnit.MINUTES,
                Storage.SignUrlOption.httpMethod(HttpMethod.PUT),
                Storage.SignUrlOption.withExtHeaders(Map.of("Content-Type", contentType)),
                Storage.SignUrlOption.withV4Signature());

        return new UploadUrlResponse(
                objectName,
                url.toString(),
                "PUT",
                Map.of("Content-Type", contentType),
                Instant.now().plus(Duration.ofMinutes(signedUrlMinutes)));
    }

    @Override
    public byte[] readBytes(String objectName) {
        Storage storage = storageProvider.getIfAvailable();
        if (storage == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Image storage is not configured");
        }
        Blob blob = storage.get(BlobId.of(bucket, objectName));
        if (blob == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Image not found: " + objectName);
        }
        return blob.getContent();
    }

    @Override
    public String createReadUrl(String objectName) {
        if (objectName == null || objectName.isBlank()) {
            return null;
        }
        Storage storage = storageProvider.getIfAvailable();
        if (storage == null) {
            // Degrade rather than fail: the response this feeds is still useful without an image.
            return null;
        }
        // A GET carries no body, so only the host header needs signing - no withExtHeaders here.
        return storage.signUrl(
                        BlobInfo.newBuilder(BlobId.of(bucket, objectName)).build(),
                        signedUrlMinutes, TimeUnit.MINUTES,
                        Storage.SignUrlOption.httpMethod(HttpMethod.GET),
                        Storage.SignUrlOption.withV4Signature())
                .toString();
    }
}
