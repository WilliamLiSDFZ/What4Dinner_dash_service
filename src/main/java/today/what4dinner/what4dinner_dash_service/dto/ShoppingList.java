package today.what4dinner.what4dinner_dash_service.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Response of {@code GET /v1/shopping-list}. Assembled in the service, never used as a
 * query return type (a {@code List} field would break Spring Data JDBC projection).
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ShoppingList {

    private List<ShoppingRecipe> recipes;

    private List<ShoppingIngredient> ingredients;
}
