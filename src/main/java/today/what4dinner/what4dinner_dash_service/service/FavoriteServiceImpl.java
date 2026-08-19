package today.what4dinner.what4dinner_dash_service.service;

import org.springframework.stereotype.Service;
import today.what4dinner.what4dinner_dash_service.dto.RecipeSummary;
import today.what4dinner.what4dinner_dash_service.repository.FavoriteRepository;

import java.util.List;
import java.util.UUID;

@Service
public class FavoriteServiceImpl implements FavoriteService {

    private final FavoriteRepository favoriteRepository;

    public FavoriteServiceImpl(FavoriteRepository favoriteRepository) {
        this.favoriteRepository = favoriteRepository;
    }

    @Override
    public List<RecipeSummary> getFavoritesForUser(UUID userId) {
        return favoriteRepository.findFavoriteSummariesByUserId(userId);
    }
}
