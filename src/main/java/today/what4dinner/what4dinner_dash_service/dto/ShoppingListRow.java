package today.what4dinner.what4dinner_dash_service.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * One {@code family_shopping_list} row — a (recipe, ingredient) pair — with the recipe header
 * and ingredient name joined in. Flat on purpose: the service groups these into {@link ShoppingList}.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ShoppingListRow {

    private UUID recipeId;

    private String title;

    private String description;

    private String status;

    private UUID ingredientId;

    private String name;

    private boolean checked;
}
