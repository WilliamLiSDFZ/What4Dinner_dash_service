package today.what4dinner.what4dinner_dash_service.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** One image produced by a {@code DishImageGenerator}, ready to hand to GCS. */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class GeneratedImage {

    private byte[] bytes;

    /** Always one of the content types {@code ImageUploadService} accepts. */
    private String contentType;
}
