package today.what4dinner.what4dinner_dash_service.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * One ingredient on the shopping list, listed once however many selected recipes use it.
 * {@code checked} is true only when every recipe's row for this ingredient is checked.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ShoppingIngredient {

    private UUID ingredientId;

    private String name;

    private boolean checked;
}
