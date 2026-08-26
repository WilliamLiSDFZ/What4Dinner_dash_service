package today.what4dinner.what4dinner_dash_service.repository;

import today.what4dinner.what4dinner_dash_service.dto.RecipeDetailRow;
import today.what4dinner.what4dinner_dash_service.dto.RecipeSummary;
import today.what4dinner.what4dinner_dash_service.dto.StepImageRow;
import today.what4dinner.what4dinner_dash_service.dto.StepIngredientRow;
import today.what4dinner.what4dinner_dash_service.dto.StepRow;
import today.what4dinner.what4dinner_dash_service.model.Recipe;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RecipeRepository extends CrudRepository<Recipe, UUID> {

    /**
     * EXISTS rather than a LEFT JOIN: it cannot fan a recipe out into one row per like,
     * and it short-circuits. Both per-user lookups hit the composite primary keys of
     * {@code favorites} / {@code recipe_likes}; the count uses {@code idx_likes_recipe}.
     */
    @Query("""
            SELECT r.id, r.title, r.description, r.status,
                   EXISTS (SELECT 1 FROM favorites f
                           WHERE f.recipe_id = r.id AND f.user_id = :userId)        AS favorited,
                   EXISTS (SELECT 1 FROM recipe_likes l
                           WHERE l.recipe_id = r.id AND l.user_id = :userId)        AS liked,
                   (SELECT count(*) FROM recipe_likes lc WHERE lc.recipe_id = r.id) AS like_count
            FROM recipes r
            WHERE r.family_id = :familyId
            """)
    List<RecipeSummary> findSummariesByFamilyId(@Param("familyId") UUID familyId,
                                                @Param("userId") UUID userId);

    /** Takes the viewing user, because {@code favorited} / {@code liked} are per-user. */
    @Query("""
            SELECT r.id, r.title, r.description, r.status,
                   EXISTS (SELECT 1 FROM favorites f
                           WHERE f.recipe_id = r.id AND f.user_id = :userId)        AS favorited,
                   EXISTS (SELECT 1 FROM recipe_likes l
                           WHERE l.recipe_id = r.id AND l.user_id = :userId)        AS liked,
                   (SELECT count(*) FROM recipe_likes lc WHERE lc.recipe_id = r.id) AS like_count
            FROM recipes r
            WHERE r.id = :id
            """)
    Optional<RecipeSummary> findSummaryById(@Param("id") UUID id, @Param("userId") UUID userId);

    /** Recipe header plus per-user flags, family-scoped. Empty means missing or not yours. */
    @Query("""
            SELECT r.id, r.title, r.description, r.prep_time_minutes, r.cook_time_minutes,
                   r.status, r.is_public, r.created_at, r.updated_at,
                   EXISTS (SELECT 1 FROM favorites f
                           WHERE f.recipe_id = r.id AND f.user_id = :userId)        AS favorited,
                   EXISTS (SELECT 1 FROM recipe_likes l
                           WHERE l.recipe_id = r.id AND l.user_id = :userId)        AS liked,
                   (SELECT count(*) FROM recipe_likes lc WHERE lc.recipe_id = r.id) AS like_count
            FROM recipes r
            WHERE r.id = :id AND r.family_id = :familyId
            """)
    Optional<RecipeDetailRow> findDetailByFamilyIdAndId(@Param("id") UUID id,
                                                     @Param("familyId") UUID familyId,
                                                     @Param("userId") UUID userId);

    @Query("""
            SELECT id, step_order, instruction, is_optional
            FROM recipe_steps
            WHERE recipe_id = :recipeId
            ORDER BY step_order
            """)
    List<StepRow> findStepsByRecipeId(@Param("recipeId") UUID recipeId);

    /**
     * Every step ingredient of the recipe in one query, tagged with its {@code step_id} so
     * the service can group them — rather than one query per step.
     */
    @Query("""
            SELECT si.step_id, si.ingredient_id, i.canonical_name AS name,
                   si.amount, si.amount_text, si.unit, si.is_optional, si.prep_note
            FROM step_ingredients si
            JOIN recipe_steps s ON s.id = si.step_id
            JOIN ingredients i  ON i.id = si.ingredient_id
            WHERE s.recipe_id = :recipeId
            ORDER BY s.step_order
            """)
    List<StepIngredientRow> findStepIngredientsByRecipeId(@Param("recipeId") UUID recipeId);

    /** Every step image key of the recipe in one query, tagged with its {@code step_id}. */
    @Query("""
            SELECT im.step_id, im.storage_key
            FROM step_images im
            JOIN recipe_steps s ON s.id = im.step_id
            WHERE s.recipe_id = :recipeId
            ORDER BY s.step_order, im.created_at
            """)
    List<StepImageRow> findStepImageKeysByRecipeId(@Param("recipeId") UUID recipeId);

    /**
     * Ids are generated in Java rather than by the column default, because child rows
     * need the parent id before the insert returns.
     *
     * <p>{@code status} is fixed at {@code 'done'}: {@code 'pending'} belongs to the AI
     * pipeline, not to a recipe a user typed in.
     */
    @Modifying
    @Query("""
            INSERT INTO recipes (id, user_id, family_id, title, description,
                                 prep_time_minutes, cook_time_minutes, status, is_public)
            VALUES (:id, :userId, :familyId, :title, :description,
                    :prepTimeMinutes, :cookTimeMinutes, 'done', :isPublic)
            """)
    void insertRecipe(@Param("id") UUID id,
                      @Param("userId") UUID userId,
                      @Param("familyId") UUID familyId,
                      @Param("title") String title,
                      @Param("description") String description,
                      @Param("prepTimeMinutes") Integer prepTimeMinutes,
                      @Param("cookTimeMinutes") Integer cookTimeMinutes,
                      @Param("isPublic") boolean isPublic);

    @Modifying
    @Query("""
            INSERT INTO recipe_steps (id, recipe_id, step_order, instruction, is_optional)
            VALUES (:id, :recipeId, :stepOrder, :instruction, :isOptional)
            """)
    void insertStep(@Param("id") UUID id,
                    @Param("recipeId") UUID recipeId,
                    @Param("stepOrder") int stepOrder,
                    @Param("instruction") String instruction,
                    @Param("isOptional") boolean isOptional);

    @Modifying
    @Query("""
            INSERT INTO step_ingredients (id, step_id, ingredient_id, amount, amount_text,
                                          unit, is_optional, prep_note)
            VALUES (:id, :stepId, :ingredientId, :amount, :amountText,
                    :unit, :isOptional, :prepNote)
            """)
    void insertStepIngredient(@Param("id") UUID id,
                              @Param("stepId") UUID stepId,
                              @Param("ingredientId") UUID ingredientId,
                              @Param("amount") Double amount,
                              @Param("amountText") String amountText,
                              @Param("unit") String unit,
                              @Param("isOptional") boolean isOptional,
                              @Param("prepNote") String prepNote);

    @Modifying
    @Query("""
            INSERT INTO step_images (id, step_id, storage_key)
            VALUES (:id, :stepId, :storageKey)
            """)
    void insertStepImage(@Param("id") UUID id,
                         @Param("stepId") UUID stepId,
                         @Param("storageKey") String storageKey);

    /**
     * Family-scoped delete returning the affected row count, so "does it exist and is it
     * mine?" and "delete it" are one statement — no separate existence check, hence no
     * TOCTOU window. A count of 0 means missing *or* another family's; both are a 404, so
     * the endpoint never leaks whether a foreign recipe exists.
     *
     * <p>Every other child of {@code recipes} cascades; {@code recipe_ingredients} does so
     * only once its FK is altered to ON DELETE CASCADE.
     */
    @Modifying
    @Query("DELETE FROM recipes WHERE id = :id AND family_id = :familyId")
    int deleteByFamilyIdAndId(@Param("familyId") UUID familyId, @Param("id") UUID id);

    /** Flat per-recipe ingredient list, derived from the steps rather than sent by clients. */
    @Modifying
    @Query("""
            INSERT INTO recipe_ingredients (id, recipe_id, ingredient_id, is_optional)
            VALUES (:id, :recipeId, :ingredientId, :isOptional)
            """)
    void insertRecipeIngredient(@Param("id") UUID id,
                                @Param("recipeId") UUID recipeId,
                                @Param("ingredientId") UUID ingredientId,
                                @Param("isOptional") boolean isOptional);
}
