package today.what4dinner.what4dinner_dash_service.repository;

import today.what4dinner.what4dinner_dash_service.model.Recipe;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

/**
 * Access to the {@code recipe_likes} join table. That table has a composite primary key
 * {@code (user_id, recipe_id)} and no surrogate id, so it cannot be a Spring Data JDBC
 * aggregate — this extends the plain {@link Repository} marker instead of
 * {@code CrudRepository} and exposes only explicit {@code @Query} methods.
 */
public interface LikeRepository extends Repository<Recipe, UUID> {

    /** Total likes on a recipe, across all users. Served by {@code idx_likes_recipe}. */
    @Query("SELECT count(*) FROM recipe_likes WHERE recipe_id = :recipeId")
    long countByRecipeId(@Param("recipeId") UUID recipeId);

    /** Whether this one user has liked the recipe. Served by the primary key. */
    @Query("SELECT count(*) FROM recipe_likes WHERE user_id = :userId AND recipe_id = :recipeId")
    long countByUserIdAndRecipeId(@Param("userId") UUID userId, @Param("recipeId") UUID recipeId);

    /**
     * Idempotent — re-liking an already-liked recipe is a no-op thanks to the table's
     * composite primary key, so a double-tapped button cannot double-count.
     */
    @Modifying
    @Query("""
            INSERT INTO recipe_likes (user_id, recipe_id)
            VALUES (:userId, :recipeId)
            ON CONFLICT (user_id, recipe_id) DO NOTHING
            """)
    void addLike(@Param("userId") UUID userId, @Param("recipeId") UUID recipeId);

    /** Idempotent — deleting a row that is not there affects no rows. */
    @Modifying
    @Query("DELETE FROM recipe_likes WHERE user_id = :userId AND recipe_id = :recipeId")
    void removeLike(@Param("userId") UUID userId, @Param("recipeId") UUID recipeId);
}
