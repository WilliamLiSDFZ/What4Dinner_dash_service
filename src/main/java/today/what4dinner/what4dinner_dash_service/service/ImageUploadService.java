package today.what4dinner.what4dinner_dash_service.service;

import today.what4dinner.what4dinner_dash_service.dto.UploadUrlResponse;

import java.util.UUID;

public interface ImageUploadService {

    /**
     * Mints a short-lived signed PUT URL plus a server-generated object key.
     *
     * @param userId      caller, used to resolve the owning family
     * @param purpose     one of the allowed purposes; used only as a lookup key
     * @param contentType one of the allowed image types; used only as a lookup key
     * @throws org.springframework.web.server.ResponseStatusException 400 for an unknown
     *         purpose or content type; 503 if GCS is not configured
     */
    UploadUrlResponse createUploadUrl(UUID userId, String purpose, String contentType);
}
