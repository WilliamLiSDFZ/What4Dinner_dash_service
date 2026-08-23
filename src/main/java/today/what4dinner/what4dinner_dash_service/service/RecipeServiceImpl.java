package today.what4dinner.what4dinner_dash_service.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import today.what4dinner.what4dinner_dash_service.dto.CreateRecipeRequest;
import today.what4dinner.what4dinner_dash_service.dto.CreateRecipeStepRequest;
import today.what4dinner.what4dinner_dash_service.dto.CreateStepIngredientRequest;
import today.what4dinner.what4dinner_dash_service.dto.RecipeSummary;
import today.what4dinner.what4dinner_dash_service.repository.IngredientRepository;
import today.what4dinner.what4dinner_dash_service.repository.RecipeRepository;
import today.what4dinner.what4dinner_dash_service.repository.UserRepository;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class RecipeServiceImpl implements RecipeService {

    private final RecipeRepository recipeRepository;

    private final UserRepository userRepository;

    private final IngredientRepository ingredientRepository;

    public RecipeServiceImpl(RecipeRepository recipeRepository,
                             UserRepository userRepository,
                             IngredientRepository ingredientRepository) {
        this.recipeRepository = recipeRepository;
        this.userRepository = userRepository;
        this.ingredientRepository = ingredientRepository;
    }

    @Override
    public List<RecipeSummary> getRecipesForUser(UUID userId) {
        return recipeRepository.findSummariesByUserId(userId);
    }

    /**
     * {@code @Transactional} carries the weight here: a recipe spans four tables, and
     * without a single transaction a failure part-way through would leave an orphan recipe
     * with only some of its steps. It is also required because the Spring Data JDBC proxy
     * carries {@code @Transactional(readOnly = true)} metadata and PostgreSQL rejects
     * writes inside a read-only transaction.
     */
    @Override
    @Transactional
    public RecipeSummary createRecipe(UUID userId, CreateRecipeRequest request) {
        UUID familyId = familyOf(userId);

        String title = request.getTitle() == null ? "" : request.getTitle().trim();
        if (title.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "title is required");
        }
        requireNonNegative(request.getPrepTimeMinutes(), "prepTimeMinutes");
        requireNonNegative(request.getCookTimeMinutes(), "cookTimeMinutes");

        List<CreateRecipeStepRequest> steps =
                request.getSteps() == null ? List.of() : request.getSteps();

        // Validate everything before the first insert, so a bad request can never leave a
        // partially written recipe behind. Tracks whether an ingredient was optional at
        // *every* occurrence, which is what the derived recipe_ingredients row records.
        Map<UUID, Boolean> optionalEverywhere = new LinkedHashMap<>();
        for (CreateRecipeStepRequest step : steps) {
            List<CreateStepIngredientRequest> stepIngredients =
                    step.getIngredients() == null ? List.of() : step.getIngredients();
            for (CreateStepIngredientRequest ingredient : stepIngredients) {
                UUID ingredientId = ingredient.getIngredientId();
                if (ingredientId == null) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ingredientId is required");
                }
                if (ingredient.getAmount() != null && ingredient.getAmount() < 0) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "amount must not be negative");
                }
                // Scoping the lookup by family is what stops a recipe referencing another
                // family's ingredient.
                if (!optionalEverywhere.containsKey(ingredientId)
                        && ingredientRepository.findByFamilyIdAndId(familyId, ingredientId).isEmpty()) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Unknown ingredientId: " + ingredientId);
                }
                boolean optional = Boolean.TRUE.equals(ingredient.getIsOptional());
                optionalEverywhere.merge(ingredientId, optional, (a, b) -> a && b);
            }
        }

        UUID recipeId = UUID.randomUUID();
        recipeRepository.insertRecipe(recipeId, userId, familyId, title, request.getDescription(),
                request.getPrepTimeMinutes(), request.getCookTimeMinutes(),
                Boolean.TRUE.equals(request.getIsPublic()));

        for (int i = 0; i < steps.size(); i++) {
            CreateRecipeStepRequest step = steps.get(i);
            UUID stepId = UUID.randomUUID();
            // step_order is positional: the client sends order by array position, never a number.
            recipeRepository.insertStep(stepId, recipeId, i + 1, step.getInstruction(),
                    Boolean.TRUE.equals(step.getIsOptional()));

            List<CreateStepIngredientRequest> stepIngredients =
                    step.getIngredients() == null ? List.of() : step.getIngredients();
            for (CreateStepIngredientRequest ingredient : stepIngredients) {
                recipeRepository.insertStepIngredient(UUID.randomUUID(), stepId,
                        ingredient.getIngredientId(), ingredient.getAmount(),
                        ingredient.getAmountText(), ingredient.getUnit(),
                        Boolean.TRUE.equals(ingredient.getIsOptional()), ingredient.getPrepNote());
            }
        }

        // recipe_ingredients is derived, not client-supplied: one row per distinct
        // ingredient, optional only when every occurrence was optional.
        for (Map.Entry<UUID, Boolean> entry : optionalEverywhere.entrySet()) {
            recipeRepository.insertRecipeIngredient(UUID.randomUUID(), recipeId,
                    entry.getKey(), entry.getValue());
        }

        return recipeRepository.findSummaryById(recipeId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                        "Recipe vanished after insert"));
    }

    private void requireNonNegative(Integer minutes, String field) {
        if (minutes != null && minutes < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " must not be negative");
        }
    }

    private UUID familyOf(UUID userId) {
        return userRepository.findFamilyIdById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unknown user"));
    }
}
