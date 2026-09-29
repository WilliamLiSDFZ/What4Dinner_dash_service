package today.what4dinner.what4dinner_dash_service.service;

import today.what4dinner.what4dinner_dash_service.dto.ShoppingList;

import java.util.UUID;

public interface ShoppingListService {

    /**
     * Returns the user's family shopping list: the selected recipes, plus their ingredients
     * listed once each regardless of how many recipes use them.
     *
     * @param userId the calling user's id
     */
    ShoppingList getShoppingList(UUID userId);

    /**
     * Adds a recipe (and all its ingredients) to the family's list. Idempotent.
     *
     * @throws org.springframework.web.server.ResponseStatusException 404 if the recipe is not
     *         the family's, 400 if it has no ingredients
     */
    void addRecipe(UUID userId, UUID recipeId);

    /**
     * Removes a recipe and its ingredient rows from the family's list.
     *
     * @throws org.springframework.web.server.ResponseStatusException 404 if it is not on the list
     */
    void removeRecipe(UUID userId, UUID recipeId);

    /**
     * Sets whether an ingredient on the family's list has been bought.
     *
     * @throws org.springframework.web.server.ResponseStatusException 404 if it is not on the list
     */
    void setIngredientChecked(UUID userId, UUID ingredientId, boolean checked);
}
