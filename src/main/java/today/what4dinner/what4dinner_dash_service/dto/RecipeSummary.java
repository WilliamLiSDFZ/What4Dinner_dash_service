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
}
