package today.what4dinner.what4dinner_dash_service.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Body of {@code POST /v1/recipe/generate}. The keys are the original recipe photos, which
 * are stored in {@code recipe_raw_images} and sent to the model for analysis.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class GenerateRecipeRequest {

    /** Required, non-empty. Keys from {@code POST /v1/image/upload-url}, purpose {@code recipe-raw}. */
    private List<String> imageKeys;
}
