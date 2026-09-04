package today.what4dinner.what4dinner_dash_service.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * One recipe's cover image key, as selected for a whole list in a single query.
 *
 * <p>Kept separate from {@link RecipeSummary} for the same reason {@code RecipeImageRow} is
 * kept separate from {@code RecipeImageDetail}: the raw {@code storage_key} must never reach
 * a response. The service turns it into a signed URL and only the URL is handed out.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class RecipeCoverRow {

    private UUID recipeId;

    private String storageKey;
}
