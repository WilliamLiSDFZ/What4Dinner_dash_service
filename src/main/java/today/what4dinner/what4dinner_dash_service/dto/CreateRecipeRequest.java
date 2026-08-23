package today.what4dinner.what4dinner_dash_service.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Body of {@code POST /v1/recipe}. Creates the recipe, its steps, and each step's
 * ingredients in one transaction.
 *
 * <p>The owning user and family come from the JWT, never from the request. The flat
 * {@code recipe_ingredients} list is derived server-side from the steps, so it is not
 * part of this payload.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class CreateRecipeRequest {

    /** Required, trimmed. */
    private String title;

    private String description;

    /** Optional. Must not be negative. */
    private Integer prepTimeMinutes;

    /** Optional. Must not be negative. */
    private Integer cookTimeMinutes;

    /** Optional, defaults to false. */
    private Boolean isPublic;

    /** Ordered steps. May be null or empty for a header-only recipe. */
    private List<CreateRecipeStepRequest> steps;
}
