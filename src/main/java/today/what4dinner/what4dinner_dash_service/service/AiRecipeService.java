package today.what4dinner.what4dinner_dash_service.service;

import today.what4dinner.what4dinner_dash_service.dto.AiTask;
import today.what4dinner.what4dinner_dash_service.dto.GenerateFromLinkRequest;
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

    /**
     * Accepts a share link, creates the pending recipe and registers a task, then fetches the
     * post and generates from it in the background. Returns immediately.
     *
     * @throws org.springframework.web.server.ResponseStatusException 400 when the text carries
     *         no usable link or the link is not a supported site; 503 if the model or the task
     *         store is unconfigured
     */
    AiTask submitFromLink(UUID userId, GenerateFromLinkRequest request);

    Optional<AiTask> findTask(UUID taskId);
}
