package today.what4dinner.what4dinner_dash_service.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/** Response of {@code PATCH /v1/shopping-list/ingredients/{ingredientId}}. */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ShoppingIngredientCheckResponse {

    private UUID ingredientId;

    private boolean checked;
}
