package today.what4dinner.what4dinner_dash_service.service;

import today.what4dinner.what4dinner_dash_service.dto.LikeStatusResponse;

import java.util.UUID;

public interface LikeService {

    /**
     * Returns the recipe's total like count plus whether the given user has liked it.
     *
     * @throws org.springframework.web.server.ResponseStatusException 404 if no such recipe exists
     */
    LikeStatusResponse getLikeStatus(UUID userId, UUID recipeId);

    /**
     * Sets whether the given user likes the given recipe, and returns the resulting state
     * with a fresh count. Idempotent — setting a state the recipe is already in changes
     * nothing and never double-counts.
     *
     * @throws org.springframework.web.server.ResponseStatusException 404 if no such recipe exists
     */
    LikeStatusResponse setLike(UUID userId, UUID recipeId, boolean liked);
}
