package today.what4dinner.what4dinner_dash_service.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Body of {@code POST /v1/ingredient}. The family comes from the JWT, never from the
 * request.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class CreateIngredientRequest {

    private String name;

    /** Optional {@code categories.id}. */
    private UUID categoryId;
}
