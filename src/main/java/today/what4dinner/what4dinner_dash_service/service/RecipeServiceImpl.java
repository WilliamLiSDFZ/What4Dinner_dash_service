package today.what4dinner.what4dinner_dash_service.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import today.what4dinner.what4dinner_dash_service.dto.AddRecipeImagesRequest;
import today.what4dinner.what4dinner_dash_service.dto.CreateRecipeRequest;
import today.what4dinner.what4dinner_dash_service.dto.CreateRecipeStepRequest;
import today.what4dinner.what4dinner_dash_service.dto.CreateStepIngredientRequest;
import today.what4dinner.what4dinner_dash_service.dto.RecipeDetail;
import today.what4dinner.what4dinner_dash_service.dto.RecipeDetailRow;
import today.what4dinner.what4dinner_dash_service.dto.RecipeImageDetail;
import today.what4dinner.what4dinner_dash_service.dto.RecipeImageRow;
import today.what4dinner.what4dinner_dash_service.dto.RecipeStepDetail;
import today.what4dinner.what4dinner_dash_service.dto.RecipeSummary;
import today.what4dinner.what4dinner_dash_service.dto.StepImageRow;
import today.what4dinner.what4dinner_dash_service.dto.StepIngredientDetail;
import today.what4dinner.what4dinner_dash_service.dto.StepIngredientRow;
import today.what4dinner.what4dinner_dash_service.dto.StepRow;
import today.what4dinner.what4dinner_dash_service.repository.IngredientRepository;
import today.what4dinner.what4dinner_dash_service.repository.RecipeRepository;
import today.what4dinner.what4dinner_dash_service.repository.UserRepository;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class RecipeServiceImpl implements RecipeService {

    private final RecipeRepository recipeRepository;

    private final UserRepository userRepository;

    private final IngredientRepository ingredientRepository;

    private final ImageUploadService imageUploadService;

    public RecipeServiceImpl(RecipeRepository recipeRepository,
                             UserRepository userRepository,
                             IngredientRepository ingredientRepository,
                             ImageUploadService imageUploadService) {
        this.recipeRepository = recipeRepository;
        this.userRepository = userRepository;
        this.ingredientRepository = ingredientRepository;
        this.imageUploadService = imageUploadService;
    }

    @Override
    public List<RecipeSummary> getRecipesForUser(UUID userId) {
        return recipeRepository.findSummariesByFamilyId(familyOf(userId), userId);
    }

    /**
     * Loads the whole recipe in a fixed four queries — header, steps, all step ingredients,
     * all step images — then groups the children by step id in memory. Deliberately not
     * one query per step, so cost does not grow with the number of steps.
     */
    @Override
    public RecipeDetail getRecipeDetail(UUID userId, UUID recipeId) {
        UUID familyId = familyOf(userId);
        RecipeDetailRow header = recipeRepository.findDetailByFamilyIdAndId(recipeId, familyId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Recipe not found"));

        Map<UUID, List<StepIngredientDetail>> ingredientsByStep = new LinkedHashMap<>();
        for (StepIngredientRow row : recipeRepository.findStepIngredientsByRecipeId(recipeId)) {
            ingredientsByStep.computeIfAbsent(row.getStepId(), k -> new ArrayList<>())
                    .add(new StepIngredientDetail(row.getIngredientId(), row.getName(), row.getAmount(),
                            row.getAmountText(), row.getUnit(), row.getIsOptional(), row.getPrepNote()));
        }

        Map<UUID, List<String>> imagesByStep = new LinkedHashMap<>();
        for (StepImageRow row : recipeRepository.findStepImageKeysByRecipeId(recipeId)) {
            // Signing is local computation, so one call per image costs no round trip.
            // A null means storage is unconfigured; drop it rather than emit a broken entry.
            String url = imageUploadService.createReadUrl(row.getStorageKey());
            if (url != null) {
                imagesByStep.computeIfAbsent(row.getStepId(), k -> new ArrayList<>()).add(url);
            }
        }

        List<RecipeStepDetail> steps = new ArrayList<>();
        for (StepRow row : recipeRepository.findStepsByRecipeId(recipeId)) {
            steps.add(new RecipeStepDetail(row.getId(), row.getStepOrder(), row.getInstruction(),
                    row.getIsOptional(),
                    ingredientsByStep.getOrDefault(row.getId(), List.of()),
                    imagesByStep.getOrDefault(row.getId(), List.of())));
        }
        return new RecipeDetail(header.getId(), header.getTitle(), header.getDescription(),
                header.getPrepTimeMinutes(), header.getCookTimeMinutes(), header.getStatus(),
                header.getIsPublic(), header.getCreatedAt(), header.getUpdatedAt(),
                header.getFavorited(), header.getLiked(), header.getLikeCount(),
                recipeImages(recipeId), steps);
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
        String keyPrefix = "family/" + familyId + "/";
        for (CreateRecipeStepRequest step : steps) {
            for (String imageKey : step.getImageKeys() == null ? List.<String>of() : step.getImageKeys()) {
                // step_images.storage_key is VARCHAR(1024)
                requireOwnedImageKey(imageKey, keyPrefix, 1024);
            }
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

            // Keys were already validated above; a step may carry any number of images.
            for (String imageKey : step.getImageKeys() == null ? List.<String>of() : step.getImageKeys()) {
                recipeRepository.insertStepImage(UUID.randomUUID(), stepId, imageKey.trim());
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

    /**
     * {@code @Transactional} is required, not decorative: the Spring Data JDBC repository
     * proxy carries {@code @Transactional(readOnly = true)} metadata, and PostgreSQL
     * rejects writes inside a read-only transaction.
     */
    @Override
    @Transactional
    public void deleteRecipe(UUID userId, UUID recipeId) {
        UUID familyId = familyOf(userId);
        // Scoping the delete by family is what stops a cross-family delete; a zero row
        // count covers both "missing" and "not yours" without distinguishing them.
        if (recipeRepository.deleteByFamilyIdAndId(familyId, recipeId) == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Recipe not found");
        }
    }

    /**
     * {@code @Transactional} does double duty: the Spring Data JDBC proxy carries
     * {@code @Transactional(readOnly = true)} metadata that would otherwise block the
     * writes, and it makes demote-then-insert atomic so a failure cannot leave the recipe
     * without the cover it had.
     */
    @Override
    @Transactional
    public List<RecipeImageDetail> addRecipeImages(UUID userId, UUID recipeId,
                                                   AddRecipeImagesRequest request) {
        UUID familyId = familyOf(userId);
        if (recipeRepository.countByFamilyIdAndId(familyId, recipeId) == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Recipe not found");
        }

        List<String> keys = request.getImageKeys() == null ? List.of() : request.getImageKeys();
        if (keys.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "imageKeys is required");
        }
        // Validate everything before the first write, so a bad request writes nothing.
        String keyPrefix = "family/" + familyId + "/";
        for (String key : keys) {
            // recipe_images.storage_key is VARCHAR(512) - narrower than step_images.
            requireOwnedImageKey(key, keyPrefix, 512);
        }
        Integer primaryIndex = request.getPrimaryIndex();
        if (primaryIndex != null && (primaryIndex < 0 || primaryIndex >= keys.size())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "primaryIndex must be between 0 and " + (keys.size() - 1));
        }

        if (primaryIndex != null) {
            recipeRepository.clearPrimaryImage(recipeId);
        }
        // Continue after the current highest so repeated calls append rather than collide.
        int nextOrder = recipeRepository.maxImageDisplayOrder(recipeId) + 1;
        for (int i = 0; i < keys.size(); i++) {
            recipeRepository.insertRecipeImage(UUID.randomUUID(), recipeId, keys.get(i).trim(),
                    primaryIndex != null && primaryIndex == i, nextOrder + i, userId);
        }
        return recipeImages(recipeId);
    }

    /** Maps stored keys to short-lived signed URLs, dropping any that cannot be signed. */
    private List<RecipeImageDetail> recipeImages(UUID recipeId) {
        List<RecipeImageDetail> images = new ArrayList<>();
        for (RecipeImageRow row : recipeRepository.findImagesByRecipeId(recipeId)) {
            String url = imageUploadService.createReadUrl(row.getStorageKey());
            if (url != null) {
                images.add(new RecipeImageDetail(row.getId(), url, row.getIsPrimary(), row.getDisplayOrder()));
            }
        }
        return images;
    }

    /**
     * The only client-supplied storage key in the API — everywhere else the server builds
     * the whole path. Requiring the caller's own family prefix is what stops a recipe from
     * attaching another family's object; rejecting ".." stops escaping that prefix. The
     * length check turns an over-long key into a 400 rather than a database error.
     *
     * <p>Verifies ownership and shape, not existence: a key for an object that was never
     * uploaded is accepted, matching how {@code family.background_image_key} behaves.
     */
    private void requireOwnedImageKey(String imageKey, String keyPrefix, int maxLength) {
        if (imageKey == null || imageKey.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "imageKeys must not contain blanks");
        }
        String key = imageKey.trim();
        if (key.length() > maxLength) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "imageKey is too long");
        }
        if (key.contains("..") || !key.startsWith(keyPrefix)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "imageKey must be an object key belonging to your family");
        }
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
