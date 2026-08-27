package today.what4dinner.what4dinner_dash_service.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * A recipe-level photo. Carries a short-lived **signed URL** rather than the object key —
 * the bucket enforces public-access prevention, so a key could not be rendered, and the
 * internal key is not exposed.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class RecipeImageDetail {

    private UUID id;

    private String url;

    /** At most one image per recipe is the cover. */
    private Boolean isPrimary;

    private Integer displayOrder;
}
