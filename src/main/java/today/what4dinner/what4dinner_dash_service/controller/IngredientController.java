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
import today.what4dinner.what4dinner_dash_service.dto.CreateIngredientRequest;
import today.what4dinner.what4dinner_dash_service.dto.IngredientSummary;
import today.what4dinner.what4dinner_dash_service.service.IngredientService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/ingredient")
public class IngredientController {

    private final IngredientService ingredientService;

    public IngredientController(IngredientService ingredientService) {
        this.ingredientService = ingredientService;
    }

    /**
     * Lists the ingredients owned by the caller's family, newest first. The family is
     * resolved from the JWT {@code sub} claim, never from the request.
     */
    @GetMapping
    public ResponseEntity<List<IngredientSummary>> getMyIngredients(@AuthenticationPrincipal Jwt jwt) {
        UUID userId = UUID.fromString(jwt.getSubject());
        return ResponseEntity.ok(ingredientService.getIngredientsForUser(userId));
    }

    /** Creates an ingredient in the caller's family. */
    @PostMapping
    public ResponseEntity<IngredientSummary> createIngredient(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody(required = false) CreateIngredientRequest request) {

        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "name is required");
        }
        UUID userId = UUID.fromString(jwt.getSubject());
        IngredientSummary created = ingredientService.createIngredient(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /** Deletes an ingredient from the caller's family. */
    @DeleteMapping("/{ingredientId}")
    public ResponseEntity<Void> deleteIngredient(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID ingredientId) {

        UUID userId = UUID.fromString(jwt.getSubject());
        ingredientService.deleteIngredient(userId, ingredientId);
        return ResponseEntity.noContent().build();
    }
}
