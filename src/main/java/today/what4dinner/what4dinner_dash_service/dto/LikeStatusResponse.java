package today.what4dinner.what4dinner_dash_service.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Like state of a recipe: {@code liked} is the calling user's own state, while
 * {@code likeCount} is the total across every user.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class LikeStatusResponse {

    private UUID recipeId;

    private Boolean liked;

    private long likeCount;
}
