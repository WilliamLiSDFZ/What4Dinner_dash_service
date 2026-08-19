package today.what4dinner.what4dinner_dash_service.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import today.what4dinner.what4dinner_dash_service.dto.RecipeSummary;
import today.what4dinner.what4dinner_dash_service.repository.FavoriteRepository;
import today.what4dinner.what4dinner_dash_service.repository.RecipeRepository;

import java.util.List;
import java.util.UUID;

@Service
public class FavoriteServiceImpl implements FavoriteService {

    private final FavoriteRepository favoriteRepository;

    private final RecipeRepository recipeRepository;

    public FavoriteServiceImpl(FavoriteRepository favoriteRepository, RecipeRepository recipeRepository) {
        this.favoriteRepository = favoriteRepository;
        this.recipeRepository = recipeRepository;
    }

    @Override
    public List<RecipeSummary> getFavoritesForUser(UUID userId) {
        return favoriteRepository.findFavoriteSummariesByUserId(userId);
    }

    /**
     * {@code @Transactional} is required, not decorative: the Spring Data JDBC repository
     * proxy carries {@code @Transactional(readOnly = true)} metadata, and PostgreSQL
     * rejects writes inside a read-only transaction.
     */
    @Override
    @Transactional
    public void setFavorite(UUID userId, UUID recipeId, boolean favorited) {
        if (!recipeRepository.existsById(recipeId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Recipe not found");
        }
        if (favorited) {
            favoriteRepository.addFavorite(userId, recipeId);
        } else {
            favoriteRepository.removeFavorite(userId, recipeId);
        }
    }
}
