package today.what4dinner.what4dinner_dash_service.repository;

import today.what4dinner.what4dinner_dash_service.dto.RecipeSummary;
import today.what4dinner.what4dinner_dash_service.model.Recipe;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

/**
 * Access to the {@code favorites} join table. That table has a composite primary key
 * {@code (user_id, recipe_id)} and no surrogate id, so it cannot be a Spring Data JDBC
 * aggregate — this extends the plain {@link Repository} marker instead of
 * {@code CrudRepository} and exposes only explicit {@code @Query} methods.
 */
public interface FavoriteRepository extends Repository<Recipe, UUID> {

    /**
     * {@code favorited} is necessarily true for every row here, but it is selected anyway
     * so this endpoint returns the same populated shape as {@code GET /v1/recipe} rather
     * than nulls.
     */
    @Query("""
            SELECT r.id, r.title, r.description, r.status,
                   true                                                             AS favorited,
                   EXISTS (SELECT 1 FROM recipe_likes l
                           WHERE l.recipe_id = r.id AND l.user_id = :userId)        AS liked,
                   (SELECT count(*) FROM recipe_likes lc WHERE lc.recipe_id = r.id) AS like_count
            FROM favorites f
            JOIN recipes r ON r.id = f.recipe_id
            WHERE f.user_id = :userId
            ORDER BY f.created_at DESC
            """)
    List<RecipeSummary> findFavoriteSummariesByUserId(@Param("userId") UUID userId);

    /**
     * Idempotent — re-favoriting an already-favorited recipe is a no-op thanks to the
     * table's composite primary key.
     */
    @Modifying
    @Query("""
            INSERT INTO favorites (user_id, recipe_id)
            VALUES (:userId, :recipeId)
            ON CONFLICT (user_id, recipe_id) DO NOTHING
            """)
    void addFavorite(@Param("userId") UUID userId, @Param("recipeId") UUID recipeId);

    /** Idempotent — deleting a row that is not there affects no rows. */
    @Modifying
    @Query("DELETE FROM favorites WHERE user_id = :userId AND recipe_id = :recipeId")
    void removeFavorite(@Param("userId") UUID userId, @Param("recipeId") UUID recipeId);
}
