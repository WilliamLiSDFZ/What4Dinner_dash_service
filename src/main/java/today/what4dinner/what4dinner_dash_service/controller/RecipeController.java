package today.what4dinner.what4dinner_dash_service.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import today.what4dinner.what4dinner_dash_service.dto.AddRecipeImagesRequest;
import today.what4dinner.what4dinner_dash_service.dto.AiTask;
import today.what4dinner.what4dinner_dash_service.dto.GenerateFromLinkRequest;
import today.what4dinner.what4dinner_dash_service.dto.GenerateRecipeRequest;
import today.what4dinner.what4dinner_dash_service.dto.CreateRecipeRequest;
import today.what4dinner.what4dinner_dash_service.dto.RecipeImageDetail;
import today.what4dinner.what4dinner_dash_service.dto.RecipeDetail;
import today.what4dinner.what4dinner_dash_service.dto.RecipeSummary;
import today.what4dinner.what4dinner_dash_service.service.AiRecipeService;
import today.what4dinner.what4dinner_dash_service.service.RecipeService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/recipe")
public class RecipeController {

    private final RecipeService recipeService;

    private final AiRecipeService aiRecipeService;

    public RecipeController(RecipeService recipeService, AiRecipeService aiRecipeService) {
        this.recipeService = recipeService;
        this.aiRecipeService = aiRecipeService;
    }

    /**
     * Returns every recipe in the caller's family, each with that user's favorite / like
     * state. The family is resolved from the JWT {@code sub} claim.
     */
    @GetMapping
    public ResponseEntity<List<RecipeSummary>> getMyRecipes(@AuthenticationPrincipal Jwt jwt) {
        UUID userId = UUID.fromString(jwt.getSubject());
        return ResponseEntity.ok(recipeService.getRecipesForUser(userId));
    }

    /**
     * Returns one recipe in full — header, favorite/like state, and ordered steps with
     * their ingredients and signed image URLs. Family-scoped, like the list and delete.
     */
    @GetMapping("/{recipeId}")
    public ResponseEntity<RecipeDetail> getRecipe(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID recipeId) {

        UUID userId = UUID.fromString(jwt.getSubject());
        return ResponseEntity.ok(recipeService.getRecipeDetail(userId, recipeId));
    }

    /**
     * Creates a recipe with its steps and their ingredients. The owning user and family
     * come from the JWT {@code sub} claim, never from the request.
     */
    @PostMapping
    public ResponseEntity<RecipeSummary> createRecipe(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody(required = false) CreateRecipeRequest request) {

        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "title is required");
        }
        UUID userId = UUID.fromString(jwt.getSubject());
        RecipeSummary created = recipeService.createRecipe(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * Submits recipe photos for AI analysis. Returns {@code 202} immediately with a task id;
     * the recipe row already exists at {@code status = "pending"}. Poll
     * {@code GET /v1/recipe/generate/{taskId}} for the outcome.
     */
    @PostMapping("/generate")
    public ResponseEntity<AiTask> generateRecipe(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody(required = false) GenerateRecipeRequest request) {

        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "imageKeys is required");
        }
        UUID userId = UUID.fromString(jwt.getSubject());
        return ResponseEntity.accepted().body(aiRecipeService.submit(userId, request));
    }

    /**
     * Generates a recipe from a shared post instead of uploaded photos. Same {@code 202} and
     * the same poll endpoint as {@code /generate}; only the input differs.
     *
     * <p>Takes the whole share blob, not just a URL — the share sheet wraps the link in title
     * text and emoji, and making the user extract it by hand is a step they would get wrong.
     */
    @PostMapping("/generate/link")
    public ResponseEntity<AiTask> generateRecipeFromLink(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody(required = false) GenerateFromLinkRequest request) {

        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "shareText is required");
        }
        UUID userId = UUID.fromString(jwt.getSubject());
        return ResponseEntity.accepted().body(aiRecipeService.submitFromLink(userId, request));
    }

    /** Polls one generation task. {@code 404} once the task is unknown or its TTL has expired. */
    @GetMapping("/generate/{taskId}")
    public ResponseEntity<AiTask> getGenerationTask(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID taskId) {

        return ResponseEntity.ok(aiRecipeService.findTask(taskId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Task not found")));
    }

    /**
     * Attaches user-uploaded photos to the recipe. Writes {@code recipe_images} with
     * {@code source = 'user'}; never touches {@code recipe_raw_images}, which is reserved
     * for the AI pipeline's source screenshots.
     */
    @PostMapping("/{recipeId}/image")
    public ResponseEntity<List<RecipeImageDetail>> addRecipeImages(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID recipeId,
            @RequestBody(required = false) AddRecipeImagesRequest request) {

        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "imageKeys is required");
        }
        UUID userId = UUID.fromString(jwt.getSubject());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(recipeService.addRecipeImages(userId, recipeId, request));
    }

    /**
     * Deletes a recipe from the caller's family, along with everything that cascades from
     * it. Any family member may delete any of the family's recipes.
     */
    @DeleteMapping("/{recipeId}")
    public ResponseEntity<Void> deleteRecipe(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID recipeId) {

        UUID userId = UUID.fromString(jwt.getSubject());
        recipeService.deleteRecipe(userId, recipeId);
        return ResponseEntity.noContent().build();
    }
}
