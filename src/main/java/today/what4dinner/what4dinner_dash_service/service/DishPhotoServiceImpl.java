package today.what4dinner.what4dinner_dash_service.service;

import com.anthropic.client.AnthropicClient;
import com.anthropic.models.messages.Base64ImageSource;
import com.anthropic.models.messages.ContentBlockParam;
import com.anthropic.models.messages.ImageBlockParam;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.TextBlockParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import today.what4dinner.what4dinner_dash_service.dto.AiTask;
import today.what4dinner.what4dinner_dash_service.dto.DishPhotoPlan;
import today.what4dinner.what4dinner_dash_service.dto.GeneratedImage;
import today.what4dinner.what4dinner_dash_service.dto.RecipeDetailRow;
import today.what4dinner.what4dinner_dash_service.repository.RecipeRepository;
import today.what4dinner.what4dinner_dash_service.repository.UserRepository;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executor;

/**
 * Generates a photograph of the finished dish.
 *
 * <p>Two models, because no single one does both halves: Claude looks at the recipe's original
 * photos and works out what the dish looks like, and a {@link DishImageGenerator} paints it.
 * Claude cannot generate images and the image model cannot see the recipe, so the hand-off is
 * a written description rather than the photo itself — which also keeps the composition under
 * our control instead of inheriting whatever framing the original snapshot had.
 */
@Service
public class DishPhotoServiceImpl implements DishPhotoService {

    private static final Logger log = LoggerFactory.getLogger(DishPhotoServiceImpl.class);

    /** Lenient for the same reason as the recipe pipeline: one stray field must not fail a run. */
    private static final JsonMapper jsonMapper = JsonMapper.builder()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .build();

    private static final String PLAN_PROMPT = """
            You look at photos from a recipe and prepare a brief for a food photographer.

            Rules:
            - Photos are labelled. Each photo is preceded by a line "Photo pN:". Refer to a photo
              only by that exact label - p1, p2, and so on. Never write a file name, a path, or a
              number on its own.
            - photoKey: the ONE photo that best shows the finished, plated dish. If none of them
              does - they are all raw ingredients, mid-cooking steps, screenshots or text - set it
              to null. Null is a perfectly good answer; do not stretch for a photo that only
              roughly qualifies, and do not pick one just because it is the only photo there is.
            - appearance: describe how the finished dish looks, in English, for someone who has to
              paint it without ever seeing it. Colour, texture, how the main ingredients are cut
              and arranged, sauce, garnish, and the kind of vessel it is served in. Two or three
              sentences. Base it on the chosen photo when there is one, and on the recipe text
              alone when there is not.
            - cuisine: which cuisine the dish belongs to, as one lowercase English word - chinese,
              western, japanese, korean, thai, indian, and so on. Work it out from the dish itself
              if the recipe does not say.

            Respond with ONLY a JSON object, no prose and no markdown fence, in exactly this shape:
            {"photoKey": string|null, "appearance": string, "cuisine": string}
            """;

    /**
     * The composition is fixed by the product, not by the model: plate centred, camera about
     * 45 degrees above it, and a restaurant dining room matching the cuisine behind it. Only
     * the dish and the cuisine vary.
     */
    private static final String IMAGE_PROMPT = """
            A photorealistic food photograph of %s: %s
            The plate sits in the exact centre of the frame. The camera looks down at it from \
            roughly 45 degrees above, aimed straight at the plate. The background is the dining \
            room of a %s restaurant, softly out of focus. Warm, natural, appetising lighting, \
            shallow depth of field. No text, no watermark, no people, no hands.
            """;

    private final RecipeRepository recipeRepository;

    private final UserRepository userRepository;

    private final ImageUploadService imageUploadService;

    private final AiTaskStore taskStore;

    private final ObjectProvider<AnthropicClient> anthropicProvider;

    private final ObjectProvider<DishImageGenerator> generatorProvider;

    private final Executor aiImageExecutor;

    private final TransactionTemplate transactionTemplate;

    @Value("${anthropic.model:claude-sonnet-5}")
    private String model;

    @Value("${anthropic.max-images:10}")
    private int maxImages;

    public DishPhotoServiceImpl(RecipeRepository recipeRepository,
                                UserRepository userRepository,
                                ImageUploadService imageUploadService,
                                AiTaskStore taskStore,
                                ObjectProvider<AnthropicClient> anthropicProvider,
                                ObjectProvider<DishImageGenerator> generatorProvider,
                                @Qualifier("aiImageExecutor") Executor aiImageExecutor,
                                TransactionTemplate transactionTemplate) {
        this.recipeRepository = recipeRepository;
        this.userRepository = userRepository;
        this.imageUploadService = imageUploadService;
        this.taskStore = taskStore;
        this.anthropicProvider = anthropicProvider;
        this.generatorProvider = generatorProvider;
        this.aiImageExecutor = aiImageExecutor;
        this.transactionTemplate = transactionTemplate;
    }

    @Override
    public AiTask submitPhotoGeneration(UUID userId, UUID recipeId) {
        UUID familyId = familyOf(userId);
        if (recipeRepository.countByFamilyIdAndId(familyId, recipeId) == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Recipe not found");
        }
        // Both models are needed, so check both before writing anything: discovering the second
        // one is missing after the row exists would strand it at 'pending'.
        if (anthropicProvider.getIfAvailable() == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "AI recipe generation is not configured");
        }
        if (generatorProvider.getIfAvailable() == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Dish photo generation is not configured");
        }

        UUID imageId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        // Task first, durable row second - same ordering as recipe generation, so a task-store
        // outage costs an expiring Redis entry rather than a permanently pending image row.
        AiTask task = taskStore.createForImage(taskId, recipeId, imageId);
        transactionTemplate.executeWithoutResult(status ->
                recipeRepository.insertPendingAiImage(imageId, recipeId,
                        recipeRepository.maxImageDisplayOrder(recipeId) + 1));

        aiImageExecutor.execute(() -> generateAsync(taskId, recipeId, imageId, familyId, userId));
        return task;
    }

    /** Every failure path must land the row in a terminal state, or it is pending forever. */
    private void generateAsync(UUID taskId, UUID recipeId, UUID imageId, UUID familyId, UUID userId) {
        try {
            taskStore.markProcessing(taskId);
            transactionTemplate.executeWithoutResult(s ->
                    recipeRepository.markAiImageProcessing(imageId));

            RecipeDetailRow recipe = recipeRepository
                    .findDetailByFamilyIdAndId(recipeId, familyId, userId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                            "Recipe not found"));
            List<String> rawKeys = recipeRepository.findRawImageKeysByRecipeId(recipeId);
            if (rawKeys.size() > maxImages) {
                rawKeys = rawKeys.subList(0, maxImages);
            }

            DishPhotoPlan plan = planPhoto(recipe, rawKeys);
            String prompt = buildImagePrompt(recipe.getTitle(), plan);

            DishImageGenerator generator = generatorProvider.getIfAvailable();
            if (generator == null) {
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                        "Dish photo generation is not configured");
            }
            GeneratedImage image = generator.generate(prompt);
            String storageKey = imageUploadService.writeBytes(
                    userId, "recipe", image.getContentType(), image.getBytes());

            transactionTemplate.executeWithoutResult(s -> {
                recipeRepository.completeAiImage(imageId, storageKey, prompt, generator.modelName());
                // Only when the recipe has no cover yet; a cover the user chose is left alone.
                int promoted = recipeRepository.promoteToPrimaryIfNone(imageId, recipeId);
                if (promoted > 0) {
                    log.info("Generated photo became the cover for recipe {}", recipeId);
                }
            });
            taskStore.markDone(taskId);
            log.info("Dish photo generated for recipe {}", recipeId);
        } catch (Exception e) {
            log.warn("Dish photo generation failed for recipe {}: {}", recipeId, e.toString());
            safelyMarkFailed(taskId, imageId, e);
        }
    }

    /** Each failure write is isolated: failing to record the failure must not hide the cause. */
    private void safelyMarkFailed(UUID taskId, UUID imageId, Exception cause) {
        String message = cause.getMessage() == null ? cause.toString() : cause.getMessage();
        try {
            transactionTemplate.executeWithoutResult(s ->
                    recipeRepository.failAiImage(imageId, truncate(message, 2000)));
        } catch (Exception e) {
            log.error("Could not mark image {} failed: {}", imageId, e.toString());
        }
        try {
            taskStore.markFailed(taskId, message);
        } catch (Exception e) {
            log.error("Could not mark task {} failed: {}", taskId, e.toString());
        }
    }

    // ------------------------------------------------------------------ the vision half

    private DishPhotoPlan planPhoto(RecipeDetailRow recipe, List<String> rawKeys) {
        AnthropicClient client = anthropicProvider.getIfAvailable();
        if (client == null) {
            throw new IllegalStateException("Anthropic client is not configured");
        }
        // Same positional-label indirection as the recipe pipeline: the model refers to photos
        // by p1/p2 and never sees a storage key, so it cannot name an object it was not given.
        Map<String, String> byLabel = AiRecipeServiceImpl.photoMenu(rawKeys);

        List<ContentBlockParam> blocks = new ArrayList<>();
        int photoNumber = 0;
        for (String key : rawKeys) {
            blocks.add(ContentBlockParam.ofText(TextBlockParam.builder()
                    .text("Photo p" + (++photoNumber) + ":")
                    .build()));
            byte[] bytes = imageUploadService.readBytes(key);
            blocks.add(ContentBlockParam.ofImage(ImageBlockParam.builder()
                    .source(Base64ImageSource.builder()
                            .data(Base64.getEncoder().encodeToString(bytes))
                            .mediaType(AiRecipeServiceImpl.mediaTypeFor(key))
                            .build())
                    .build()));
        }
        blocks.add(ContentBlockParam.ofText(TextBlockParam.builder()
                .text(rawKeys.isEmpty()
                        ? "There are no photos of this recipe. Set photoKey to null and describe "
                        + "the dish from the recipe text alone."
                        : "You were shown " + rawKeys.size() + " photo(s), labelled p1 through p"
                        + rawKeys.size() + ". Use only these labels for photoKey.")
                .build()));
        blocks.add(ContentBlockParam.ofText(TextBlockParam.builder()
                .text(recipeText(recipe))
                .build()));

        MessageCreateParams params = MessageCreateParams.builder()
                .model(model)
                .maxTokens(2000L)
                .system(PLAN_PROMPT)
                .addUserMessageOfBlockParams(blocks)
                .build();

        String raw = client.messages().create(params).content().stream()
                .flatMap(block -> block.text().stream())
                .map(text -> text.text())
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Model returned no content"));

        DishPhotoPlan plan = parsePlan(raw);
        // The label is validated rather than trusted; an invented one just means no reference.
        String resolved = AiRecipeServiceImpl.resolvePhoto(
                plan.getPhotoKey(), byLabel, new HashSet<>(byLabel.values()));
        log.info("Photo plan for '{}': reference={}, cuisine={}",
                recipe.getTitle(), resolved == null ? "none" : plan.getPhotoKey(), plan.getCuisine());
        return plan;
    }

    private static String recipeText(RecipeDetailRow recipe) {
        StringBuilder text = new StringBuilder("The recipe is called: ").append(recipe.getTitle());
        if (recipe.getDescription() != null && !recipe.getDescription().isBlank()) {
            text.append('\n').append("Description: ").append(recipe.getDescription().trim());
        }
        return text.toString();
    }

    private DishPhotoPlan parsePlan(String raw) {
        String json = raw.trim();
        if (json.startsWith("```")) {
            int start = json.indexOf('\n');
            int end = json.lastIndexOf("```");
            if (start >= 0 && end > start) {
                json = json.substring(start + 1, end).trim();
            }
        }
        try {
            return jsonMapper.readValue(json, DishPhotoPlan.class);
        } catch (Exception e) {
            throw new IllegalStateException("Model did not return a parseable photo plan: "
                    + e.getMessage(), e);
        }
    }

    // ------------------------------------------------------------------ the painting half

    /** Package-private so the composition can be asserted without spending a model call. */
    static String buildImagePrompt(String title, DishPhotoPlan plan) {
        String dish = title == null || title.isBlank() ? "a home-cooked dish" : title.trim();
        String appearance = plan.getAppearance() == null || plan.getAppearance().isBlank()
                ? "Present it the way it would be served in a restaurant."
                : plan.getAppearance().trim();
        return IMAGE_PROMPT.formatted(dish, appearance, cuisineOf(plan));
    }

    /**
     * Kept to a plain adjective: the whole string is interpolated into the image prompt, so a
     * model that answered with a sentence would otherwise rewrite the scene description.
     */
    private static String cuisineOf(DishPhotoPlan plan) {
        String cuisine = plan.getCuisine();
        if (cuisine == null || cuisine.isBlank()) {
            return "Chinese";
        }
        String word = cuisine.trim().split("[\\s,;.]+")[0];
        if (word.length() > 20 || !word.matches("[A-Za-z-]+")) {
            return "Chinese";
        }
        return Character.toUpperCase(word.charAt(0)) + word.substring(1).toLowerCase();
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    private UUID familyOf(UUID userId) {
        return userRepository.findFamilyIdById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unknown user"));
    }
}
