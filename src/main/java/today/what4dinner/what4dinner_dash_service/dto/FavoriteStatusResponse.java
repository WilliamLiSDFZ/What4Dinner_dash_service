package today.what4dinner.what4dinner_dash_service.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Result of {@code PATCH /v1/favorite/{recipeId}} — the recipe and its resulting
 * favorite state.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class FavoriteStatusResponse {

    private UUID recipeId;

    private Boolean favorited;
}
