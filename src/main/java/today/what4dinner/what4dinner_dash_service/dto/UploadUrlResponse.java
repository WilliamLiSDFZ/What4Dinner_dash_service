package today.what4dinner.what4dinner_dash_service.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

/**
 * A short-lived signed upload target. The client must PUT the bytes to {@code uploadUrl}
 * with exactly {@code requiredHeaders}, then report {@code objectName} back when creating
 * the recipe / ingredient it belongs to.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class UploadUrlResponse {

    /** Server-generated object key. Store this; it is what the other endpoints expect. */
    private String objectName;

    private String uploadUrl;

    private String method;

    private Map<String, String> requiredHeaders;

    private Instant expiresAt;
}
