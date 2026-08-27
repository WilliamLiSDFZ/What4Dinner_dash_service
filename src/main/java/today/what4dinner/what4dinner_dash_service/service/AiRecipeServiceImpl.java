package today.what4dinner.what4dinner_dash_service.service;

import com.anthropic.client.AnthropicClient;
import com.anthropic.models.messages.Base64ImageSource;
import com.anthropic.models.messages.ContentBlockParam;
import com.anthropic.models.messages.ImageBlockParam;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.TextBlockParam;
import com.anthropic.models.messages.ThinkingConfigAdaptive;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import today.what4dinner.what4dinner_dash_service.dto.AiTask;
import today.what4dinner.what4dinner_dash_service.dto.GenerateRecipeRequest;
import today.what4dinner.what4dinner_dash_service.dto.IngredientSummary;
import today.what4dinner.what4dinner_dash_service.dto.RecipeDraft;
import today.what4dinner.what4dinner_dash_service.repository.IngredientRepository;
import today.what4dinner.what4dinner_dash_service.repository.RecipeRepository;
import today.what4dinner.what4dinner_dash_service.repository.UserRepository;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class AiRecipeServiceImpl implements AiRecipeService {

    private static final Logger log = LoggerFactory.getLogger(AiRecipeServiceImpl.class);

    /** Lenient on purpose: an extra field from the model must not fail a whole generation. */
    private static final JsonMapper jsonMapper = JsonMapper.builder()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .build();

    private static final String PLACEHOLDER_TITLE = "Generating…";

    private static final String SYSTEM_PROMPT = """
            You read photos of recipes and turn them into structured data.

            Rules:
            - Transcribe only what the photos actually show. Do not invent steps, times, or
              quantities that are not visible. Use null when something is not stated.
            - Write every step as one self-contained action, in order.
            - For each ingredient a step uses, prefer an existing ingredient: set ingredientKey
              to its key from the list below. Only when nothing in the list matches, leave
              ingredientKey null and set newIngredientName instead.
            - Never set both ingredientKey and newIngredientName.
            - Keep title, description, steps and ingredient names in the same language as the photos.

            Respond with ONLY a JSON object, no prose and no markdown fence, in exactly this shape:
            {"title": string, "description": string|null,
             "prepTimeMinutes": int|null, "cookTimeMinutes": int|null,
             "steps": [{"instruction": string, "isOptional": bool,
                        "ingredients": [{"ingredientKey": string|null, "newIngredientName": string|null,
                                         "amount": number|null, "amountText": string|null,
                                         "unit": string|null, "isOptional": bool,
                                         "prepNote": string|null}]}]}
            Use null - not an empty string, not 0, not a placeholder - for anything the photos do
            not state.
            """;

    private final RecipeRepository recipeRepository;

    private final IngredientRepository ingredientRepository;

    private final UserRepository userRepository;

    private final ImageUploadService imageUploadService;

    private final AiTaskStore taskStore;

    private final ObjectProvider<AnthropicClient> anthropicProvider;

    @Value("${anthropic.model:claude-sonnet-5}")
    private String model;

    @Value("${anthropic.max-images:10}")
    private int maxImages;

    public AiRecipeServiceImpl(RecipeRepository recipeRepository,
                               IngredientRepository ingredientRepository,
                               UserRepository userRepository,
                               ImageUploadService imageUploadService,
                               AiTaskStore taskStore,
                               ObjectProvider<AnthropicClient> anthropicProvider) {
        this.recipeRepository = recipeRepository;
        this.ingredientRepository = ingredientRepository;
        this.userRepository = userRepository;
        this.imageUploadService = imageUploadService;
        this.taskStore = taskStore;
        this.anthropicProvider = anthropicProvider;
    }

    @Override
    public AiTask submit(UUID userId, GenerateRecipeRequest request) {
        UUID familyId = familyOf(userId);
        List<String> keys = request.getImageKeys() == null ? List.of() : request.getImageKeys();

        // Validate before anything is written, so a rejected request leaves nothing behind.
        if (keys.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "imageKeys is required");
        }
        if (keys.size() > maxImages) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "At most " + maxImages + " images per request");
        }
        String keyPrefix = "family/" + familyId + "/";
        for (String key : keys) {
            requireOwnedKey(key, keyPrefix);
        }
        if (anthropicProvider.getIfAvailable() == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "AI recipe generation is not configured");
        }

        UUID recipeId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        // Register the task BEFORE writing anything durable. Reversed, a task-store outage
        // strands a "Generating…" recipe in the database forever; this way the only casualty
        // is a Redis entry that expires on its own.
        AiTask task = taskStore.create(taskId, recipeId);
        createShell(recipeId, userId, familyId, keys);

        // Hand off to the pool; the caller gets 202 immediately.
        generateAsync(taskId, recipeId, familyId, userId, List.copyOf(keys));
        return task;
    }

    @Override
    public Optional<AiTask> findTask(UUID taskId) {
        return taskStore.find(taskId);
    }

    /** The pending shell plus its raw images, so the recipe exists from the moment of submission. */
    @Transactional
    protected void createShell(UUID recipeId, UUID userId, UUID familyId, List<String> keys) {
        recipeRepository.insertPendingRecipe(recipeId, userId, familyId, PLACEHOLDER_TITLE);
        for (String key : keys) {
            recipeRepository.insertRawImage(UUID.randomUUID(), recipeId, key.trim());
        }
    }

    /**
     * Runs on the {@code ai-gen-} pool. Every failure path must land the recipe in a terminal
     * state — a task that dies silently would leave the recipe stuck at {@code pending}.
     */
    @Async("aiTaskExecutor")
    public void generateAsync(UUID taskId, UUID recipeId, UUID familyId, UUID userId, List<String> keys) {
        try {
            taskStore.markProcessing(taskId);
            RecipeDraft draft = askModel(familyId, keys);
            persistDraft(recipeId, familyId, draft);
            taskStore.markDone(taskId);
            log.info("AI generation finished for recipe {}", recipeId);
        } catch (Exception e) {
            log.warn("AI generation failed for recipe {}: {}", recipeId, e.toString());
            safelyMarkFailed(taskId, recipeId, e);
        }
    }

    private void safelyMarkFailed(UUID taskId, UUID recipeId, Exception cause) {
        try {
            recipeRepository.markRecipeFailed(recipeId);
        } catch (Exception e) {
            // Most likely chk_status still lacks 'failed' - see the ALTER in the plan.
            log.error("Could not mark recipe {} failed: {}", recipeId, e.toString());
        }
        try {
            taskStore.markFailed(taskId, cause.getMessage() == null ? cause.toString() : cause.getMessage());
        } catch (Exception e) {
            log.error("Could not mark task {} failed: {}", taskId, e.toString());
        }
    }

    /** Positional keys (i1, i2, …) so the model never sees a UUID and spends few tokens. */
    private Map<String, UUID> ingredientMenu(UUID familyId, StringBuilder rendered) {
        Map<String, UUID> byKey = new LinkedHashMap<>();
        List<IngredientSummary> existing = ingredientRepository.findByFamilyId(familyId);
        for (int i = 0; i < existing.size(); i++) {
            String key = "i" + (i + 1);
            byKey.put(key, existing.get(i).getId());
            rendered.append(key).append(": ").append(existing.get(i).getCanonicalName()).append('\n');
        }
        return byKey;
    }

    private RecipeDraft askModel(UUID familyId, List<String> keys) {
        AnthropicClient client = anthropicProvider.getIfAvailable();
        if (client == null) {
            throw new IllegalStateException("Anthropic client is not configured");
        }
        StringBuilder menu = new StringBuilder();
        Map<String, UUID> byKey = ingredientMenu(familyId, menu);

        List<ContentBlockParam> blocks = new ArrayList<>();
        for (String key : keys) {
            byte[] bytes = imageUploadService.readBytes(key);
            blocks.add(ContentBlockParam.ofImage(ImageBlockParam.builder()
                    .source(Base64ImageSource.builder()
                            .data(Base64.getEncoder().encodeToString(bytes))
                            .mediaType(mediaTypeFor(key))
                            .build())
                    .build()));
        }
        blocks.add(ContentBlockParam.ofText(TextBlockParam.builder()
                .text(menu.isEmpty()
                        ? "The family has no saved ingredients yet, so name every ingredient you see."
                        : "Ingredients already saved by this family:\n" + menu)
                .build()));

        MessageCreateParams params = MessageCreateParams.builder()
                .model(model)
                .maxTokens(16000L)
                // Adaptive is the only on-mode for Sonnet 5; budget_tokens is removed and 400s.
                .thinking(ThinkingConfigAdaptive.builder().build())
                .system(SYSTEM_PROMPT)
                .addUserMessageOfBlockParams(blocks)
                .build();

        String raw = client.messages().create(params).content().stream()
                .flatMap(block -> block.text().stream())
                .map(text -> text.text())
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Model returned no content"));
        RecipeDraft draft = parseDraft(raw);
        // Keys are validated here rather than trusted: the model may invent one.
        validateDraftKeys(draft, byKey);
        return draft;
    }

    /**
     * The schema-derived structured-output mode was tried first and rejected: because it marks
     * every property required, the model fabricates filler for fields it wants to leave empty
     * (empty strings, {@code 0} amounts, literal "placeholder" keys) and sometimes drops whole
     * ingredients. Asking for plain JSON yields correct nulls, at the cost of parsing it here.
     */
    private RecipeDraft parseDraft(String raw) {
        String json = raw.trim();
        // Models often wrap JSON in a markdown fence even when told not to.
        if (json.startsWith("```")) {
            int start = json.indexOf('\n');
            int end = json.lastIndexOf("```");
            if (start >= 0 && end > start) {
                json = json.substring(start + 1, end).trim();
            }
        }
        try {
            return jsonMapper.readValue(json, RecipeDraft.class);
        } catch (Exception e) {
            throw new IllegalStateException("Model did not return parseable recipe JSON: " + e.getMessage(), e);
        }
    }

    private void validateDraftKeys(RecipeDraft draft, Map<String, UUID> byKey) {
        for (RecipeDraft.DraftStep step : safeSteps(draft)) {
            for (RecipeDraft.DraftIngredient ing : safeIngredients(step)) {
                String key = blankToNull(ing.getIngredientKey());
                if (key != null && !byKey.containsKey(key) && blankToNull(ing.getNewIngredientName()) == null) {
                    throw new IllegalStateException("Model referenced unknown ingredient key " + key);
                }
            }
        }
    }

    /**
     * Writes the whole draft in one transaction, mirroring {@code RecipeServiceImpl.createRecipe}:
     * steps in order, per-step ingredients, and the derived flat {@code recipe_ingredients} list.
     */
    @Transactional
    protected void persistDraft(UUID recipeId, UUID familyId, RecipeDraft draft) {
        StringBuilder menu = new StringBuilder();
        Map<String, UUID> byKey = ingredientMenu(familyId, menu);

        Map<UUID, Boolean> optionalEverywhere = new LinkedHashMap<>();
        List<RecipeDraft.DraftStep> steps = safeSteps(draft);
        for (int i = 0; i < steps.size(); i++) {
            RecipeDraft.DraftStep step = steps.get(i);
            UUID stepId = UUID.randomUUID();
            recipeRepository.insertStep(stepId, recipeId, i + 1, step.getInstruction(),
                    Boolean.TRUE.equals(step.getIsOptional()));

            for (RecipeDraft.DraftIngredient ing : safeIngredients(step)) {
                UUID ingredientId = resolveIngredient(familyId, byKey, ing);
                if (ingredientId == null) {
                    continue;
                }
                // Structured output fills absent numbers with 0 rather than null; a zero
                // quantity is meaningless, so treat it as "not stated".
                Double amount = ing.getAmount() == null || ing.getAmount() <= 0 ? null : ing.getAmount();
                boolean optional = Boolean.TRUE.equals(ing.getIsOptional());
                recipeRepository.insertStepIngredient(UUID.randomUUID(), stepId, ingredientId,
                        amount, blankToNull(ing.getAmountText()), truncate(blankToNull(ing.getUnit()), 16),
                        optional, blankToNull(ing.getPrepNote()));
                optionalEverywhere.merge(ingredientId, optional, (a, b) -> a && b);
            }
        }
        for (Map.Entry<UUID, Boolean> entry : optionalEverywhere.entrySet()) {
            recipeRepository.insertRecipeIngredient(UUID.randomUUID(), recipeId, entry.getKey(), entry.getValue());
        }
        recipeRepository.completeGeneratedRecipe(recipeId,
                truncate(blankToPlaceholder(draft.getTitle()), 512), blankToNull(draft.getDescription()),
                nonNegative(draft.getPrepTimeMinutes()), nonNegative(draft.getCookTimeMinutes()));
    }

    /** Existing key wins; otherwise create the named ingredient in this family, reusing any match. */
    private UUID resolveIngredient(UUID familyId, Map<String, UUID> byKey, RecipeDraft.DraftIngredient ing) {
        String key = blankToNull(ing.getIngredientKey());
        if (key != null && byKey.containsKey(key)) {
            return byKey.get(key);
        }
        String name = blankToNull(ing.getNewIngredientName());
        if (name == null) {
            return null;
        }
        name = name.trim();
        Optional<UUID> existing = ingredientRepository.findIdByFamilyIdAndName(familyId, name);
        if (existing.isPresent()) {
            return existing.get();
        }
        UUID created = UUID.randomUUID();
        ingredientRepository.insert(created, familyId, truncate(name, 256), null, 0d, null);
        byKey.put("new:" + name.toLowerCase(), created);
        return created;
    }

    private List<RecipeDraft.DraftStep> safeSteps(RecipeDraft draft) {
        return draft.getSteps() == null ? List.of() : draft.getSteps();
    }

    private List<RecipeDraft.DraftIngredient> safeIngredients(RecipeDraft.DraftStep step) {
        return step.getIngredients() == null ? List.of() : step.getIngredients();
    }

    private Integer nonNegative(Integer value) {
        return value != null && value < 0 ? null : value;
    }

    /**
     * Structured output returns empty strings, not nulls, for fields the model left unset —
     * so every optional string from the model has to go through this before it is stored or
     * branched on.
     */
    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private String blankToPlaceholder(String title) {
        return title == null || title.isBlank() ? "Untitled recipe" : title.trim();
    }

    /** Model output is untrusted for length as well as content; columns are narrow. */
    private String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    private Base64ImageSource.MediaType mediaTypeFor(String key) {
        String lower = key.toLowerCase();
        if (lower.endsWith(".png")) {
            return Base64ImageSource.MediaType.IMAGE_PNG;
        }
        if (lower.endsWith(".webp")) {
            return Base64ImageSource.MediaType.IMAGE_WEBP;
        }
        if (lower.endsWith(".gif")) {
            return Base64ImageSource.MediaType.IMAGE_GIF;
        }
        return Base64ImageSource.MediaType.IMAGE_JPEG;
    }

    private void requireOwnedKey(String imageKey, String keyPrefix) {
        if (imageKey == null || imageKey.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "imageKeys must not contain blanks");
        }
        String key = imageKey.trim();
        // recipe_raw_images.storage_key is VARCHAR(1024).
        if (key.length() > 1024) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "imageKey is too long");
        }
        if (key.contains("..") || !key.startsWith(keyPrefix)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "imageKey must be an object key belonging to your family");
        }
    }

    private UUID familyOf(UUID userId) {
        return userRepository.findFamilyIdById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unknown user"));
    }
}
