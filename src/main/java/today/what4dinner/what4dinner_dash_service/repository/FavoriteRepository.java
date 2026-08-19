package today.what4dinner.what4dinner_dash_service.repository;

import today.what4dinner.what4dinner_dash_service.dto.RecipeSummary;
import today.what4dinner.what4dinner_dash_service.model.Recipe;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

/**
 * Read access to the {@code favorites} join table. That table has a composite primary key
 * {@code (user_id, recipe_id)} and no surrogate id, so it cannot be a Spring Data JDBC
 * aggregate — this extends the plain {@link Repository} marker instead of
 * {@code CrudRepository} and exposes only explicit {@code @Query} methods.
 */
public interface FavoriteRepository extends Repository<Recipe, UUID> {

    @Query("""
            SELECT r.id, r.title, r.description, r.status
            FROM favorites f
            JOIN recipes r ON r.id = f.recipe_id
            WHERE f.user_id = :userId
            ORDER BY f.created_at DESC
            """)
    List<RecipeSummary> findFavoriteSummariesByUserId(@Param("userId") UUID userId);
}
