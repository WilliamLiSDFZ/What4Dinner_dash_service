package today.what4dinner.what4dinner_dash_service.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * One ingredient used by a step, inside {@link CreateRecipeRequest}.
 * The ingredient must already exist in the caller's family.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class CreateStepIngredientRequest {

    /** Required. Must be an {@code ingredients.id} belonging to the caller's family. */
    private UUID ingredientId;

    /** Optional numeric amount. Must not be negative. */
    private Double amount;

    /** Optional free-text amount, e.g. "两个" or "a pinch". */
    private String amountText;

    private String unit;

    private Boolean isOptional;

    private String prepNote;
}
