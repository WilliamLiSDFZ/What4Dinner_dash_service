package today.what4dinner.what4dinner_dash_service.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
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
}
