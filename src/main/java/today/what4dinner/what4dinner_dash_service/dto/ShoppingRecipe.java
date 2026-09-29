package today.what4dinner.what4dinner_dash_service.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

/**
 * A recipe on the shopping list, with its own ingredients. Assembled in the service,
 * never used as a query return type (a {@code List} field would break projection).
 *
 * <p>{@code coverUrl} is a signed GET URL, or null when the recipe has no usable image.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ShoppingRecipe {

    private UUID id;

    private String title;

    private String description;

    private String status;

    private String coverUrl;

    private List<ShoppingRecipeIngredient> ingredients;
}
