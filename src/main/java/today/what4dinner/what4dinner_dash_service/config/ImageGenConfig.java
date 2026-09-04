package today.what4dinner.what4dinner_dash_service.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import today.what4dinner.what4dinner_dash_service.service.DishImageGenerator;
import today.what4dinner.what4dinner_dash_service.service.OpenAiImageGenerator;

/**
 * Builds the image generator used to produce dish photos.
 *
 * <p>Mirrors {@link AnthropicConfig} and {@link GcsConfig}: returns {@code null} when the
 * provider is unconfigured so the application still starts, and the endpoint answers
 * {@code 503} rather than the whole service failing to boot.
 *
 * <p>{@code image-gen.provider} exists so the provider can be swapped later. With one
 * implementation it only ever selects that one; a second implementation is when a real
 * selector becomes worth writing.
 */
@Configuration
public class ImageGenConfig {

    private static final Logger log = LoggerFactory.getLogger(ImageGenConfig.class);

    @Value("${image-gen.provider:openai}")
    private String provider;

    @Value("${image-gen.timeout-seconds:120}")
    private long timeoutSeconds;

    @Value("${image-gen.openai.api-key:}")
    private String openAiApiKey;

    @Value("${image-gen.openai.model:gpt-image-1}")
    private String openAiModel;

    @Value("${image-gen.openai.size:1024x1024}")
    private String openAiSize;

    @Value("${image-gen.openai.quality:medium}")
    private String openAiQuality;

    @Value("${image-gen.openai.output-format:png}")
    private String openAiOutputFormat;

    @Bean
    public DishImageGenerator dishImageGenerator() {
        if (!"openai".equalsIgnoreCase(provider)) {
            log.warn("image-gen.provider '{}' is not recognised - dish photo generation is "
                    + "disabled (endpoint returns 503).", provider);
            return null;
        }
        if (!StringUtils.hasText(openAiApiKey)) {
            log.warn("image-gen.openai.api-key is not set - dish photo generation is disabled "
                    + "(endpoint returns 503).");
            return null;
        }
        log.info("Dish image generator initialised: openai/{}", openAiModel);
        return new OpenAiImageGenerator(openAiApiKey, openAiModel, openAiSize, openAiQuality,
                openAiOutputFormat, timeoutSeconds);
    }
}
