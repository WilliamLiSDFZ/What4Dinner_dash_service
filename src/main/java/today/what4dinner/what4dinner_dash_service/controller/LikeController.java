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
import today.what4dinner.what4dinner_dash_service.dto.LikeStatusRequest;
import today.what4dinner.what4dinner_dash_service.dto.LikeStatusResponse;
import today.what4dinner.what4dinner_dash_service.service.LikeService;

import java.util.UUID;

@RestController
@RequestMapping("/v1/like")
public class LikeController {

    private final LikeService likeService;

    public LikeController(LikeService likeService) {
        this.likeService = likeService;
    }

    /**
     * Returns the recipe's total like count and whether the authenticated user has liked
     * it. The user id is taken from the JWT {@code sub} claim.
     */
    @GetMapping("/{recipeId}")
    public ResponseEntity<LikeStatusResponse> getLikeStatus(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID recipeId) {

        UUID userId = UUID.fromString(jwt.getSubject());
        return ResponseEntity.ok(likeService.getLikeStatus(userId, recipeId));
    }

    /**
     * Likes or unlikes a recipe for the authenticated user. Idempotent — the resulting
     * state always matches the requested one, and the fresh count is returned so the
     * client needs no follow-up request.
     */
    @PatchMapping("/{recipeId}")
    public ResponseEntity<LikeStatusResponse> setLikeStatus(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID recipeId,
            @RequestBody(required = false) LikeStatusRequest request) {

        if (request == null || request.getLiked() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "liked is required");
        }
        UUID userId = UUID.fromString(jwt.getSubject());
        return ResponseEntity.ok(likeService.setLike(userId, recipeId, request.getLiked()));
    }
}
