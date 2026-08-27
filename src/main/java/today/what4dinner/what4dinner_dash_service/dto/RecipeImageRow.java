package today.what4dinner.what4dinner_dash_service.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Internal flat projection of a {@code recipe_images} row. Kept separate from
 * {@link RecipeImageDetail} so the raw {@code storage_key} never reaches a response — the
 * service converts it to a signed URL first.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class RecipeImageRow {

    private UUID id;

    private String storageKey;

    private Boolean isPrimary;

    private Integer displayOrder;
}
