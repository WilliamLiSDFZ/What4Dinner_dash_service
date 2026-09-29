package today.what4dinner.what4dinner_dash_service.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/** One ingredient of a recipe on the shopping list. Checked state lives on {@link ShoppingIngredient}. */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ShoppingRecipeIngredient {

    private UUID ingredientId;

    private String name;
}
