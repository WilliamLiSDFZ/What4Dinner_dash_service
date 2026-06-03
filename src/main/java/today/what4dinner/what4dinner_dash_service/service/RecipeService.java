package today.what4dinner.what4dinner_dash_service.service;

import today.what4dinner.what4dinner_dash_service.dto.RecipeSummary;

import java.util.List;
import java.util.UUID;

public interface RecipeService {

    /**
     * Returns summaries (id, title, description, status) of all recipes owned by the given user.
     *
     * @param userId the owning user's id
     * @return the user's recipe summaries (empty if none)
     */
    List<RecipeSummary> getRecipesForUser(UUID userId);
}
