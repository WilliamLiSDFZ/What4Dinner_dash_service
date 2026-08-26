package today.what4dinner.what4dinner_dash_service.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Internal flat projection of the recipe header. Deliberately has **no collection field**:
 * Spring Data JDBC treats a collection property on a projection as an entity child
 * relationship and tries to build a back-reference query for it, failing with
 * "We need at least one condition". The nested {@link RecipeDetail} is assembled in the
 * service from this plus the child row queries.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class RecipeDetailRow {

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
}
