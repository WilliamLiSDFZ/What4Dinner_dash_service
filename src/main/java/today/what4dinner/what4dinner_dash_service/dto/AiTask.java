package today.what4dinner.what4dinner_dash_service.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * State of one AI generation task, as stored in Redis and returned by
 * {@code GET /v1/recipe/generate/{taskId}}.
 *
 * <p>{@code status} is one of {@code pending}, {@code processing}, {@code done},
 * {@code failed}. The last two are terminal.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class AiTask {

    private UUID taskId;

    /** The recipe row created up front; it exists from submission, initially {@code pending}. */
    private UUID recipeId;

    private String status;

    /** Populated only when {@code status} is {@code failed}. */
    private String errorMessage;
}
