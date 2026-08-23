package today.what4dinner.what4dinner_dash_service.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * One step inside {@link CreateRecipeRequest}. {@code step_order} is not sent by the
 * client — the server assigns it from the step's position in the array.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class CreateRecipeStepRequest {

    private String instruction;

    private Boolean isOptional;

    /** Ingredients used by this step. May be null or empty. */
    private List<CreateStepIngredientRequest> ingredients;
}
