package today.what4dinner.what4dinner_dash_service.service;

import today.what4dinner.what4dinner_dash_service.dto.AddRecipeImagesRequest;
import today.what4dinner.what4dinner_dash_service.dto.CreateRecipeRequest;
import today.what4dinner.what4dinner_dash_service.dto.RecipeImageDetail;
import today.what4dinner.what4dinner_dash_service.dto.RecipeDetail;
import today.what4dinner.what4dinner_dash_service.dto.RecipeSummary;

import java.util.List;
import java.util.UUID;

public interface RecipeService {

    /**
     * Returns summaries of every recipe in the given user's family, each annotated with
     * that user's favorite / like state and the recipe's total like count.
     *
     * @param userId the viewing user; the family is resolved from them
     * @return the family's recipe summaries (empty if none)
     */
    List<RecipeSummary> getRecipesForUser(UUID userId);

    /**
     * Returns one recipe in full — header, per-user flags, and ordered steps with their
     * ingredients and signed image URLs.
     *
     * @throws org.springframework.web.server.ResponseStatusException 404 if no such recipe
     *         exists in the caller's family; 401 if the user row is gone
     */
    RecipeDetail getRecipeDetail(UUID userId, UUID recipeId);

    /**
     * Creates a recipe together with its steps and each step's ingredients, in a single
     * transaction. The flat {@code recipe_ingredients} list is derived from the steps.
     *
     * @throws org.springframework.web.server.ResponseStatusException 400 if the title is blank,
     *         a time or amount is negative, or an ingredient is missing / not in the caller's
     *         family; 401 if the user row is gone
     */
    RecipeSummary createRecipe(UUID userId, CreateRecipeRequest request);

    /**
     * Deletes a recipe belonging to the caller's family, together with everything that
     * cascades from it (steps, step ingredients and images, tags, favorites, likes,
     * images, shopping-list entries).
     *
     * <p>Family-scoped, not uploader-scoped: any member may delete any of the family's
     * recipes.
     *
     * @throws org.springframework.web.server.ResponseStatusException 404 if no such recipe
     *         exists in the caller's family; 401 if the user row is gone
     */
    void deleteRecipe(UUID userId, UUID recipeId);

    /**
     * Attaches user-uploaded photos to the recipe itself ({@code recipe_images}, always
     * {@code source = 'user'}). Never touches {@code recipe_raw_images}, which is reserved
     * for the AI pipeline's source screenshots.
     *
     * <p>If {@code primaryIndex} is given, the recipe's existing cover is demoted in the
     * same transaction — the schema permits only one cover per recipe.
     *
     * @throws org.springframework.web.server.ResponseStatusException 400 for an empty key
     *         list, a key not owned by the caller's family, or an out-of-range
     *         {@code primaryIndex}; 404 if the recipe is not in the caller's family
     */
    List<RecipeImageDetail> addRecipeImages(UUID userId, UUID recipeId, AddRecipeImagesRequest request);
}
