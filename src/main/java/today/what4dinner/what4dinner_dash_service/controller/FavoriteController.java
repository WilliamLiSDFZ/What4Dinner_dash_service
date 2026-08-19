package today.what4dinner.what4dinner_dash_service.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import today.what4dinner.what4dinner_dash_service.dto.FavoriteStatusRequest;
import today.what4dinner.what4dinner_dash_service.dto.FavoriteStatusResponse;
import today.what4dinner.what4dinner_dash_service.dto.RecipeSummary;
import today.what4dinner.what4dinner_dash_service.service.FavoriteService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/favorite")
public class FavoriteController {

    private final FavoriteService favoriteService;

    public FavoriteController(FavoriteService favoriteService) {
        this.favoriteService = favoriteService;
    }

    /**
     * Returns the authenticated user's favorited recipes (id, title, description, status),
     * newest favorite first. The user id is taken from the JWT {@code sub} claim.
     */
    @GetMapping
    public ResponseEntity<List<RecipeSummary>> getMyFavorites(@AuthenticationPrincipal Jwt jwt) {
        UUID userId = UUID.fromString(jwt.getSubject());
        return ResponseEntity.ok(favoriteService.getFavoritesForUser(userId));
    }

    /**
     * Sets whether the authenticated user has favorited the given recipe. Idempotent — the
     * resulting state always matches the requested one, so it is echoed back.
     * The user id is taken from the JWT {@code sub} claim.
     */
    @PatchMapping("/{recipeId}")
    public ResponseEntity<FavoriteStatusResponse> setFavoriteStatus(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID recipeId,
            @RequestBody(required = false) FavoriteStatusRequest request) {

        if (request == null || request.getFavorited() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "favorited is required");
        }
        UUID userId = UUID.fromString(jwt.getSubject());
        boolean favorited = request.getFavorited();
        favoriteService.setFavorite(userId, recipeId, favorited);
        return ResponseEntity.ok(new FavoriteStatusResponse(recipeId, favorited));
    }
}
