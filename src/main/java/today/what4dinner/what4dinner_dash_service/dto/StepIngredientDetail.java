package today.what4dinner.what4dinner_dash_service.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/** One ingredient of a step, as returned by {@code GET /v1/recipe/{recipeId}}. */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class StepIngredientDetail {

    private UUID ingredientId;

    /** Joined from {@code ingredients.canonical_name} so no second call is needed. */
    private String name;

    private Double amount;

    private String amountText;

    private String unit;

    private Boolean isOptional;

    private String prepNote;
}
