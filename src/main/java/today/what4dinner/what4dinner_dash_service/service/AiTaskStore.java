package today.what4dinner.what4dinner_dash_service.service;

import today.what4dinner.what4dinner_dash_service.dto.AiTask;

import java.util.Optional;
import java.util.UUID;

/**
 * Task state for AI generation. Backed by Redis today; kept behind this interface so the
 * storage choice is one class, not a decision spread through the service.
 */
public interface AiTaskStore {

    AiTask create(UUID taskId, UUID recipeId);

    /** As {@link #create}, but for a task that is filling in a {@code recipe_images} row. */
    AiTask createForImage(UUID taskId, UUID recipeId, UUID imageId);

    void markProcessing(UUID taskId);

    void markDone(UUID taskId);

    void markFailed(UUID taskId, String errorMessage);

    Optional<AiTask> find(UUID taskId);
}
