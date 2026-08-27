package today.what4dinner.what4dinner_dash_service.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Full recipe returned by {@code GET /v1/recipe/{recipeId}} — header, per-user
 * favorite/like state, and the ordered steps with their ingredients and images.
 *
 * <p>{@code favorited} and {@code liked} are the calling user's own state;
 * {@code likeCount} is the total across every user.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class RecipeDetail {

    private UUID id;

    private String title;

    private String description;

    private Integer prepTimeMinutes;

    private Integer cookTimeMinutes;

    private String status;

    private Boolean isPublic;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private Boolean favorited;

    private Boolean liked;

    private long likeCount;

    /** Recipe-level photos, ordered by {@code displayOrder}. Separate from step images. */
    private List<RecipeImageDetail> images;

    /** Ordered by {@code step_order}. */
    private List<RecipeStepDetail> steps;
}
