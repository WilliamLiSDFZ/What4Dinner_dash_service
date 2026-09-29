package today.what4dinner.what4dinner_dash_service.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import today.what4dinner.what4dinner_dash_service.dto.RecipeCoverRow;
import today.what4dinner.what4dinner_dash_service.dto.ShoppingIngredient;
import today.what4dinner.what4dinner_dash_service.dto.ShoppingList;
import today.what4dinner.what4dinner_dash_service.dto.ShoppingListRow;
import today.what4dinner.what4dinner_dash_service.dto.ShoppingRecipe;
import today.what4dinner.what4dinner_dash_service.dto.ShoppingRecipeIngredient;
import today.what4dinner.what4dinner_dash_service.repository.RecipeRepository;
import today.what4dinner.what4dinner_dash_service.repository.ShoppingListRepository;
import today.what4dinner.what4dinner_dash_service.repository.UserRepository;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class ShoppingListServiceImpl implements ShoppingListService {

    private final ShoppingListRepository shoppingListRepository;

    private final RecipeRepository recipeRepository;

    private final UserRepository userRepository;

    private final ImageUploadService imageUploadService;

    public ShoppingListServiceImpl(ShoppingListRepository shoppingListRepository,
                                   RecipeRepository recipeRepository,
                                   UserRepository userRepository,
                                   ImageUploadService imageUploadService) {
        this.shoppingListRepository = shoppingListRepository;
        this.recipeRepository = recipeRepository;
        this.userRepository = userRepository;
        this.imageUploadService = imageUploadService;
    }

    @Override
    public ShoppingList getShoppingList(UUID userId) {
        UUID familyId = familyOf(userId);

        // Each row is a (recipe, ingredient) pair. Recipes keep their full ingredient list;
        // the top-level list collapses each ingredient to one entry, bought only once every
        // recipe's row for it is checked.
        Map<UUID, ShoppingRecipe> recipes = new LinkedHashMap<>();
        Map<UUID, ShoppingIngredient> ingredients = new LinkedHashMap<>();
        for (ShoppingListRow row : shoppingListRepository.findRowsByFamilyId(familyId)) {
            recipes.computeIfAbsent(row.getRecipeId(), id -> new ShoppingRecipe(
                            id, row.getTitle(), row.getDescription(), row.getStatus(), null, new ArrayList<>()))
                    .getIngredients().add(new ShoppingRecipeIngredient(row.getIngredientId(), row.getName()));

            ShoppingIngredient ingredient = ingredients.get(row.getIngredientId());
            if (ingredient == null) {
                ingredients.put(row.getIngredientId(),
                        new ShoppingIngredient(row.getIngredientId(), row.getName(), row.isChecked()));
            } else {
                ingredient.setChecked(ingredient.isChecked() && row.isChecked());
            }
        }

        if (!recipes.isEmpty()) {
            for (RecipeCoverRow cover : recipeRepository.findCoverKeysByRecipeIds(recipes.keySet())) {
                recipes.get(cover.getRecipeId()).setCoverUrl(imageUploadService.createReadUrl(cover.getStorageKey()));
            }
        }
        return new ShoppingList(new ArrayList<>(recipes.values()), new ArrayList<>(ingredients.values()));
    }

    /**
     * {@code @Transactional} is required, not decorative: the Spring Data JDBC repository
     * proxy carries {@code @Transactional(readOnly = true)} metadata, and PostgreSQL
     * rejects writes inside a read-only transaction.
     */
    @Override
    @Transactional
    public void addRecipe(UUID userId, UUID recipeId) {
        UUID familyId = familyOf(userId);
        if (recipeRepository.countByFamilyIdAndId(familyId, recipeId) == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Recipe not found");
        }
        // A recipe is only on the list through its ingredient rows, so one with none can't be added.
        if (shoppingListRepository.countRecipeIngredients(recipeId) == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Recipe has no ingredients");
        }
        shoppingListRepository.addRecipe(familyId, recipeId);
    }

    @Override
    @Transactional
    public void removeRecipe(UUID userId, UUID recipeId) {
        if (shoppingListRepository.removeRecipe(familyOf(userId), recipeId) == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Recipe is not on the shopping list");
        }
    }

    @Override
    @Transactional
    public void setIngredientChecked(UUID userId, UUID ingredientId, boolean checked) {
        if (shoppingListRepository.setChecked(familyOf(userId), ingredientId, checked) == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Ingredient is not on the shopping list");
        }
    }

    private UUID familyOf(UUID userId) {
        return userRepository.findFamilyIdById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unknown user"));
    }
}
