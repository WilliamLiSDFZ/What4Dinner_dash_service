package today.what4dinner.what4dinner_dash_service.service;

import today.what4dinner.what4dinner_dash_service.dto.CreateIngredientRequest;
import today.what4dinner.what4dinner_dash_service.dto.IngredientSummary;

import java.util.List;
import java.util.UUID;

public interface IngredientService {

    /** Ingredients belonging to the caller's family, newest first. */
    List<IngredientSummary> getIngredientsForUser(UUID userId);

    /**
     * Creates an ingredient in the caller's family.
     *
     * @throws org.springframework.web.server.ResponseStatusException 400 if the name is blank,
     *         the category is unknown, or the reference price is negative; 409 if the name is
     *         already used in the family
     */
    IngredientSummary createIngredient(UUID userId, CreateIngredientRequest request);

    /**
     * Deletes an ingredient from the caller's family.
     *
     * @throws org.springframework.web.server.ResponseStatusException 404 if it does not exist
     *         in the caller's family; 409 if a recipe or step still references it
     */
    void deleteIngredient(UUID userId, UUID ingredientId);
}
