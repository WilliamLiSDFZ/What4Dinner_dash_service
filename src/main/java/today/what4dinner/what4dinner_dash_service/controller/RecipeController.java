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
import today.what4dinner.what4dinner_dash_service.dto.CreateRecipeRequest;
import today.what4dinner.what4dinner_dash_service.dto.RecipeDetail;
import today.what4dinner.what4dinner_dash_service.dto.RecipeSummary;
import today.what4dinner.what4dinner_dash_service.service.RecipeService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/recipe")
public class RecipeController {

    private final RecipeService recipeService;

    public RecipeController(RecipeService recipeService) {
        this.recipeService = recipeService;
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
