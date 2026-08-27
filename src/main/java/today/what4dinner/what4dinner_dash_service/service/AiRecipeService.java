package today.what4dinner.what4dinner_dash_service.service;

import today.what4dinner.what4dinner_dash_service.dto.AiTask;
import today.what4dinner.what4dinner_dash_service.dto.GenerateRecipeRequest;

import java.util.Optional;
import java.util.UUID;

public interface AiRecipeService {

    /**
     * Accepts recipe photos, creates the pending recipe and its raw-image rows, registers a
     * task, and dispatches the generation to run in the background. Returns immediately.
     *
     * @throws org.springframework.web.server.ResponseStatusException 400 for an empty key list,
     *         too many images, or a key not owned by the caller's family; 503 if the model or
     *         storage is unconfigured
     */
    AiTask submit(UUID userId, GenerateRecipeRequest request);

    Optional<AiTask> findTask(UUID taskId);
}
