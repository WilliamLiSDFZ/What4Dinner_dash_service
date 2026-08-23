package today.what4dinner.what4dinner_dash_service.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Body of {@code PATCH /v1/like/{recipeId}} — the desired like state.
 * Boxed {@code Boolean} so an absent field arrives as null and can be rejected
 * rather than silently defaulting to {@code false} and removing the like.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class LikeStatusRequest {

    private Boolean liked;
}
