package today.what4dinner.what4dinner_dash_service.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Lightweight projection of a recipe, returned by {@code GET /v1/recipe},
 * {@code GET /v1/favorite}, and the {@code 201} body of {@code POST /v1/recipe}.
 * Field names match the selected column names so Spring Data JDBC maps them by name.
 *
 * <p>{@code favorited} and {@code liked} are the calling user's own state;
 * {@code likeCount} is the total across every user.
 *
 * <p>{@code coverUrl} is the exception to "field names match column names": nothing selects
 * it, so it stays null until a service attaches it. On the {@code POST} response it is always
 * null, since a recipe has no photos at the moment it is created.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class RecipeSummary {

    private UUID id;

    private String title;

    private String description;

    private String status;

    /** This user's own favorite state. */
    private Boolean favorited;

    /** This user's own like state. */
    private Boolean liked;

    /** Total likes across all users. */
    private long likeCount;

    /**
     * Signed GET URL of the cover photo, or null when the recipe has no usable image.
     *
     * <p>Not selected by any query — it is filled in afterwards by {@code RecipeCoverResolver},
     * because a signed URL is computed, not stored. The object key never appears here.
     */
    private String coverUrl;
}
