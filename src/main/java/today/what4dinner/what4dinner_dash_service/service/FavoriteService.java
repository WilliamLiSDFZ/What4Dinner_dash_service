package today.what4dinner.what4dinner_dash_service.service;

import today.what4dinner.what4dinner_dash_service.dto.RecipeSummary;

import java.util.List;
import java.util.UUID;

public interface FavoriteService {

    /**
     * Returns summaries (id, title, description, status) of the recipes the given user has
     * favorited, newest favorite first.
     *
     * @param userId the favoriting user's id
     * @return the user's favorited recipe summaries (empty if none)
     */
    List<RecipeSummary> getFavoritesForUser(UUID userId);

    /**
     * Sets whether the given user has favorited the given recipe. Idempotent — setting a
     * state the recipe is already in changes nothing.
     *
     * @param userId    the favoriting user's id
     * @param recipeId  the recipe to favorite or unfavorite
     * @param favorited the desired state
     * @throws org.springframework.web.server.ResponseStatusException 404 if no such recipe exists
     */
    void setFavorite(UUID userId, UUID recipeId, boolean favorited);
}
