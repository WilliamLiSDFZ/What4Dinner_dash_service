package today.what4dinner.what4dinner_dash_service.repository;

import today.what4dinner.what4dinner_dash_service.dto.ShoppingListRow;
import today.what4dinner.what4dinner_dash_service.model.Recipe;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

/**
 * Access to {@code family_shopping_list}, one row per (family, recipe, ingredient). Extends the
 * plain {@link Repository} marker so every reachable query is family-scoped.
 */
public interface ShoppingListRepository extends Repository<Recipe, UUID> {

    /** Oldest first, so recipes and ingredients keep the order they were added in. */
    @Query("""
            SELECT s.recipe_id, r.title, r.description, r.status,
                   s.ingredient_id, i.canonical_name AS name, s.checked
            FROM family_shopping_list s
            JOIN recipes r ON r.id = s.recipe_id
            JOIN ingredients i ON i.id = s.ingredient_id
            WHERE s.family_id = :familyId
            ORDER BY s.created_at, i.canonical_name
            """)
    List<ShoppingListRow> findRowsByFamilyId(@Param("familyId") UUID familyId);

    @Query("SELECT count(*) FROM recipe_ingredients WHERE recipe_id = :recipeId")
    long countRecipeIngredients(@Param("recipeId") UUID recipeId);

    /**
     * Expands the recipe into one row per ingredient. Idempotent — pairs already on the list
     * hit the unique key and are skipped. {@code checked} is left to its column default.
     */
    @Modifying
    @Query("""
            INSERT INTO family_shopping_list (family_id, recipe_id, ingredient_id)
            SELECT DISTINCT :familyId, :recipeId, ingredient_id
            FROM recipe_ingredients
            WHERE recipe_id = :recipeId
            ON CONFLICT (family_id, recipe_id, ingredient_id) DO NOTHING
            """)
    void addRecipe(@Param("familyId") UUID familyId, @Param("recipeId") UUID recipeId);

    /** Returns the rows removed; 0 means the recipe was not on this family's list. */
    @Modifying
    @Query("DELETE FROM family_shopping_list WHERE family_id = :familyId AND recipe_id = :recipeId")
    int removeRecipe(@Param("familyId") UUID familyId, @Param("recipeId") UUID recipeId);

    /**
     * Updates every recipe's row for the ingredient, since the list shows it once.
     * Returns the rows touched; 0 means the ingredient is not on this family's list.
     */
    @Modifying
    @Query("""
            UPDATE family_shopping_list SET checked = :checked
            WHERE family_id = :familyId AND ingredient_id = :ingredientId
            """)
    int setChecked(@Param("familyId") UUID familyId,
                   @Param("ingredientId") UUID ingredientId,
                   @Param("checked") boolean checked);
}
