package today.what4dinner.what4dinner_dash_service.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import today.what4dinner.what4dinner_dash_service.dto.ShoppingIngredientCheckRequest;
import today.what4dinner.what4dinner_dash_service.dto.ShoppingIngredientCheckResponse;
import today.what4dinner.what4dinner_dash_service.dto.ShoppingList;
import today.what4dinner.what4dinner_dash_service.service.ShoppingListService;

import java.util.UUID;

@RestController
@RequestMapping("/v1/shopping-list")
public class ShoppingListController {

    private final ShoppingListService shoppingListService;

    public ShoppingListController(ShoppingListService shoppingListService) {
        this.shoppingListService = shoppingListService;
    }

    /** Returns the caller's family shopping list: selected recipes plus de-duplicated ingredients. */
    @GetMapping
    public ResponseEntity<ShoppingList> getShoppingList(@AuthenticationPrincipal Jwt jwt) {
        UUID userId = UUID.fromString(jwt.getSubject());
        return ResponseEntity.ok(shoppingListService.getShoppingList(userId));
    }

    /** Adds a recipe to the family's list. Idempotent. */
    @PutMapping("/recipes/{recipeId}")
    public ResponseEntity<Void> addRecipe(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID recipeId) {
        UUID userId = UUID.fromString(jwt.getSubject());
        shoppingListService.addRecipe(userId, recipeId);
        return ResponseEntity.noContent().build();
    }

    /** Removes a recipe, and its ingredients' rows, from the family's list. */
    @DeleteMapping("/recipes/{recipeId}")
    public ResponseEntity<Void> removeRecipe(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID recipeId) {
        UUID userId = UUID.fromString(jwt.getSubject());
        shoppingListService.removeRecipe(userId, recipeId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Sets whether an ingredient has been bought. Idempotent — the resulting state always
     * matches the requested one, so it is echoed back.
     */
    @PatchMapping("/ingredients/{ingredientId}")
    public ResponseEntity<ShoppingIngredientCheckResponse> setIngredientChecked(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID ingredientId,
            @RequestBody(required = false) ShoppingIngredientCheckRequest request) {

        if (request == null || request.getChecked() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "checked is required");
        }
        UUID userId = UUID.fromString(jwt.getSubject());
        boolean checked = request.getChecked();
        shoppingListService.setIngredientChecked(userId, ingredientId, checked);
        return ResponseEntity.ok(new ShoppingIngredientCheckResponse(ingredientId, checked));
    }
}
