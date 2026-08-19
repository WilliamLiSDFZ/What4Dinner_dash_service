package today.what4dinner.what4dinner_dash_service.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Body of {@code PATCH /v1/favorite/{recipeId}} — the desired favorite state.
 * Boxed {@code Boolean} so an absent field arrives as null and can be rejected
 * rather than silently defaulting to {@code false}.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class FavoriteStatusRequest {

    private Boolean favorited;
}
