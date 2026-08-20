package today.what4dinner.what4dinner_dash_service.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Body of {@code POST /v1/image/upload-url}. Both fields are used only as lookup keys
 * against fixed allowlists — neither is ever interpolated into the object key.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class UploadUrlRequest {

    /** One of: recipe, recipe-raw, family-background, ingredient. */
    private String purpose;

    /** One of: image/jpeg, image/png, image/webp, image/heic. */
    private String contentType;
}
