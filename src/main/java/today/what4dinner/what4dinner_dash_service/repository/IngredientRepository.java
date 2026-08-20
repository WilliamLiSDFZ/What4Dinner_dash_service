package today.what4dinner.what4dinner_dash_service.repository;

import today.what4dinner.what4dinner_dash_service.dto.IngredientSummary;
import today.what4dinner.what4dinner_dash_service.model.Ingredient;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Access to the family-scoped {@code ingredients} table. Extends the bare
 * {@link Repository} marker rather than {@code CrudRepository} so that no unscoped
 * find/delete is reachable — every query filters by {@code family_id}.
 */
public interface IngredientRepository extends Repository<Ingredient, UUID> {

    @Query("""
            SELECT id, canonical_name, category_id, reference_price, last_purchase
            FROM ingredients
            WHERE family_id = :familyId
            ORDER BY created_at DESC
            """)
    List<IngredientSummary> findByFamilyId(@Param("familyId") UUID familyId);

    @Query("""
            SELECT id, canonical_name, category_id, reference_price, last_purchase
            FROM ingredients
            WHERE family_id = :familyId AND id = :id
            """)
    Optional<IngredientSummary> findByFamilyIdAndId(@Param("familyId") UUID familyId, @Param("id") UUID id);

    /**
     * Duplicate-name check, scoped to the family. The schema no longer carries a unique
     * constraint on the name, so this is enforced in the application only.
     */
    @Query("""
            SELECT count(*) FROM ingredients
            WHERE family_id = :familyId AND lower(canonical_name) = lower(:canonicalName)
            """)
    long countByFamilyIdAndName(@Param("familyId") UUID familyId, @Param("canonicalName") String canonicalName);

    /**
     * How many recipes/steps reference this ingredient. Both FKs default to NO ACTION, so
     * a non-zero count means Postgres would reject the delete.
     */
    @Query("""
            SELECT (SELECT count(*) FROM recipe_ingredients WHERE ingredient_id = :id)
                 + (SELECT count(*) FROM step_ingredients  WHERE ingredient_id = :id)
            """)
    long countReferences(@Param("id") UUID id);

    @Query("SELECT count(*) FROM categories WHERE id = :categoryId")
    long countCategoryById(@Param("categoryId") UUID categoryId);

    @Modifying
    @Query("""
            INSERT INTO ingredients (id, family_id, canonical_name, category_id)
            VALUES (:id, :familyId, :canonicalName, :categoryId)
            """)
    void insert(@Param("id") UUID id,
                @Param("familyId") UUID familyId,
                @Param("canonicalName") String canonicalName,
                @Param("categoryId") UUID categoryId);

    @Modifying
    @Query("DELETE FROM ingredients WHERE id = :id AND family_id = :familyId")
    void deleteByFamilyIdAndId(@Param("familyId") UUID familyId, @Param("id") UUID id);
}
