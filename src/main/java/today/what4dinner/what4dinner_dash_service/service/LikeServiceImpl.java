package today.what4dinner.what4dinner_dash_service.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import today.what4dinner.what4dinner_dash_service.dto.LikeStatusResponse;
import today.what4dinner.what4dinner_dash_service.repository.LikeRepository;
import today.what4dinner.what4dinner_dash_service.repository.RecipeRepository;

import java.util.UUID;

@Service
public class LikeServiceImpl implements LikeService {

    private final LikeRepository likeRepository;

    private final RecipeRepository recipeRepository;

    public LikeServiceImpl(LikeRepository likeRepository, RecipeRepository recipeRepository) {
        this.likeRepository = likeRepository;
        this.recipeRepository = recipeRepository;
    }

    @Override
    public LikeStatusResponse getLikeStatus(UUID userId, UUID recipeId) {
        requireRecipe(recipeId);
        return status(userId, recipeId);
    }

    /**
     * {@code @Transactional} is required, not decorative: the Spring Data JDBC repository
     * proxy carries {@code @Transactional(readOnly = true)} metadata, and PostgreSQL
     * rejects writes inside a read-only transaction. It also keeps the write and the
     * re-count in one unit, so the returned count matches the write that just happened.
     */
    @Override
    @Transactional
    public LikeStatusResponse setLike(UUID userId, UUID recipeId, boolean liked) {
        requireRecipe(recipeId);
        if (liked) {
            likeRepository.addLike(userId, recipeId);
        } else {
            likeRepository.removeLike(userId, recipeId);
        }
        return status(userId, recipeId);
    }

    private void requireRecipe(UUID recipeId) {
        if (!recipeRepository.existsById(recipeId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Recipe not found");
        }
    }

    /** {@code liked} is this user's own row; {@code likeCount} spans every user. */
    private LikeStatusResponse status(UUID userId, UUID recipeId) {
        boolean liked = likeRepository.countByUserIdAndRecipeId(userId, recipeId) > 0;
        return new LikeStatusResponse(recipeId, liked, likeRepository.countByRecipeId(recipeId));
    }
}
