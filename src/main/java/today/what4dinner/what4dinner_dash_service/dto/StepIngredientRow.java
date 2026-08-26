package today.what4dinner.what4dinner_dash_service.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Internal flat projection: every step ingredient of a recipe in one query, carrying the
 * owning {@code stepId} so the service can group them. Never serialized — the response
 * shape is {@link StepIngredientDetail} nested inside {@link RecipeStepDetail}.
 *
 * <p>Exists so a recipe loads in a fixed number of queries instead of one per step.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class StepIngredientRow {

    private UUID stepId;

    private UUID ingredientId;

    private String name;

    private Double amount;

    private String amountText;

    private String unit;

    private Boolean isOptional;

    private String prepNote;
}
