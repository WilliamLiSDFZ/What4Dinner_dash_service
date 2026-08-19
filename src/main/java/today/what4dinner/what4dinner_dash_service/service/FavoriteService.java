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
}
