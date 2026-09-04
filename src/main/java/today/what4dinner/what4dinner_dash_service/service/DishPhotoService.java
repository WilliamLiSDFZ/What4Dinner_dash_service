package today.what4dinner.what4dinner_dash_service.service;

import today.what4dinner.what4dinner_dash_service.dto.AiTask;

import java.util.UUID;

public interface DishPhotoService {

    /**
     * Creates the pending {@code recipe_images} row, registers a task, and generates the dish
     * photo in the background. Returns immediately.
     *
     * @throws org.springframework.web.server.ResponseStatusException 404 when the recipe is not
     *         in the caller's family; 503 when the vision model, the image model, or the task
     *         store is unconfigured
     */
    AiTask submitPhotoGeneration(UUID userId, UUID recipeId);
}
