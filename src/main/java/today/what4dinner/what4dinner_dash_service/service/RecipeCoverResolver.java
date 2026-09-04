package today.what4dinner.what4dinner_dash_service.service;

import org.springframework.stereotype.Component;
import today.what4dinner.what4dinner_dash_service.dto.RecipeCoverRow;
import today.what4dinner.what4dinner_dash_service.dto.RecipeSummary;
import today.what4dinner.what4dinner_dash_service.repository.RecipeRepository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Fills in {@code coverUrl} on a list of recipe summaries.
 *
 * <p>Its own class because two endpoints need it — {@code GET /v1/recipe} and
 * {@code GET /v1/favorite} return the same DTO, and the favorites query documents that it
 * deliberately produces the same populated shape. Duplicating the grouping in both services
 * would be two places to get the cover-selection rules wrong.
 */
@Component
public class RecipeCoverResolver {

    private final RecipeRepository recipeRepository;

    private final ImageUploadService imageUploadService;

    public RecipeCoverResolver(RecipeRepository recipeRepository,
                               ImageUploadService imageUploadService) {
        this.recipeRepository = recipeRepository;
        this.imageUploadService = imageUploadService;
    }

    /**
     * One query for the whole list, then one signature per recipe that has a cover.
     *
     * <p>Signing is local computation against the service-account private key, so this costs
     * no round trip per recipe — the same reasoning the recipe-detail endpoint already relies
     * on. A recipe with no usable image simply keeps a null {@code coverUrl}, and so does
     * every recipe when storage is unconfigured: this must degrade, never fail, because the
     * list is still worth returning without pictures.
     */
    public void attachCovers(List<RecipeSummary> summaries) {
        if (summaries == null || summaries.isEmpty()) {
            return;
        }
        List<UUID> ids = new ArrayList<>(summaries.size());
        for (RecipeSummary summary : summaries) {
            ids.add(summary.getId());
        }
        Map<UUID, String> keyByRecipe = new HashMap<>();
        for (RecipeCoverRow row : recipeRepository.findCoverKeysByRecipeIds(ids)) {
            keyByRecipe.put(row.getRecipeId(), row.getStorageKey());
        }
        for (RecipeSummary summary : summaries) {
            summary.setCoverUrl(imageUploadService.createReadUrl(keyByRecipe.get(summary.getId())));
        }
    }
}
