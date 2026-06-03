package today.what4dinner.what4dinner_dash_service.service;

import org.springframework.stereotype.Service;
import today.what4dinner.what4dinner_dash_service.dto.RecipeSummary;
import today.what4dinner.what4dinner_dash_service.repository.RecipeRepository;

import java.util.List;
import java.util.UUID;

@Service
public class RecipeServiceImpl implements RecipeService {

    private final RecipeRepository recipeRepository;

    public RecipeServiceImpl(RecipeRepository recipeRepository) {
        this.recipeRepository = recipeRepository;
    }

    @Override
    public List<RecipeSummary> getRecipesForUser(UUID userId) {
        return recipeRepository.findSummariesByUserId(userId);
    }
}
