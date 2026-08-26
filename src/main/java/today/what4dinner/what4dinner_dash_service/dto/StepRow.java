package today.what4dinner.what4dinner_dash_service.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Internal flat projection of one step — no collection fields, for the same reason as
 * {@link RecipeDetailRow}. {@link RecipeStepDetail} is built from this plus the grouped
 * ingredient and image rows.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class StepRow {

    private UUID id;

    private Integer stepOrder;

    private String instruction;

    private Boolean isOptional;
}
