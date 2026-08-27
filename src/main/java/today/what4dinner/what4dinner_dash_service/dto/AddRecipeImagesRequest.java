package today.what4dinner.what4dinner_dash_service.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Body of {@code POST /v1/recipe/{recipeId}/image}. Attaches user-uploaded photos to the
 * recipe itself — written to {@code recipe_images} with {@code source = 'user'}.
 *
 * <p>Unrelated to {@code recipe_raw_images}, which is reserved for the original recipe
 * screenshots the AI pipeline analyses.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class AddRecipeImagesRequest {

    /** Required, non-empty. Keys from {@code POST /v1/image/upload-url}. */
    private List<String> imageKeys;

    /**
     * Optional 0-based index into {@code imageKeys} selecting the cover. Omit it and no
     * image becomes the cover; any existing cover is left alone.
     */
    private Integer primaryIndex;
}
