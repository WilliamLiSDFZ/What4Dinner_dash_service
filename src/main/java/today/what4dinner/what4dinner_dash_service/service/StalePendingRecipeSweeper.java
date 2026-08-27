package today.what4dinner.what4dinner_dash_service.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import today.what4dinner.what4dinner_dash_service.repository.RecipeRepository;

import java.time.LocalDateTime;

/**
 * An in-flight AI generation dies with the process, leaving its recipe stuck at
 * {@code pending} forever. This closes out anything old enough that it cannot still be
 * running.
 *
 * <p>Safe because hand-written recipes are always inserted {@code 'done'} — every
 * {@code pending} row belongs to the AI pipeline.
 */
@Component
public class StalePendingRecipeSweeper {

    private static final Logger log = LoggerFactory.getLogger(StalePendingRecipeSweeper.class);

    /** Comfortably longer than any single generation should take. */
    private static final long STALE_AFTER_MINUTES = 60;

    private final RecipeRepository recipeRepository;

    public StalePendingRecipeSweeper(RecipeRepository recipeRepository) {
        this.recipeRepository = recipeRepository;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void sweep() {
        try {
            int failed = recipeRepository.failStalePendingRecipes(
                    LocalDateTime.now().minusMinutes(STALE_AFTER_MINUTES));
            if (failed > 0) {
                log.info("Marked {} stale pending recipe(s) as failed", failed);
            }
        } catch (Exception e) {
            // Never block startup - most likely chk_status does not permit 'failed' yet.
            log.warn("Stale-pending sweep skipped: {}", e.toString());
        }
    }
}
