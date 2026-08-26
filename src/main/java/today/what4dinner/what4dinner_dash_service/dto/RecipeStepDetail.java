package today.what4dinner.what4dinner_dash_service.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

/** One step of a recipe, as returned by {@code GET /v1/recipe/{recipeId}}. */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class RecipeStepDetail {

    private UUID id;

    private Integer stepOrder;

    private String instruction;

    private Boolean isOptional;

    private List<StepIngredientDetail> ingredients;

    /**
     * Short-lived signed GET URLs, not object keys — the bucket enforces public-access
     * prevention, so a raw key could not be rendered. Empty when the step has no images.
     */
    private List<String> images;
}
