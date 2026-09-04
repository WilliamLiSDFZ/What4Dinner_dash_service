package today.what4dinner.what4dinner_dash_service.service;

import today.what4dinner.what4dinner_dash_service.dto.GeneratedImage;

/**
 * Turns a prompt into a picture.
 *
 * <p>This is an interface because the provider is expected to change — Claude cannot generate
 * images at all, so this half of the pipeline is bought from whoever is currently best at it.
 * There is one implementation today; a second one would also need a way to choose between
 * them, which is not worth building before it exists.
 */
public interface DishImageGenerator {

    /**
     * @return the generated image
     * @throws org.springframework.web.server.ResponseStatusException 502 when the provider
     *         refuses or returns something unusable
     */
    GeneratedImage generate(String prompt);

    /** Recorded on the image row, so it is always possible to tell what produced a picture. */
    String modelName();
}
