package today.what4dinner.what4dinner_dash_service.config;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

/**
 * Builds the Anthropic client used for AI recipe generation.
 *
 * <p>Mirrors {@link GcsConfig}: returns {@code null} when no API key is configured so the
 * application still starts, and the generation endpoint answers {@code 503} instead of the
 * whole service failing to boot.
 */
@Configuration
public class AnthropicConfig {

    private static final Logger log = LoggerFactory.getLogger(AnthropicConfig.class);

    @Value("${anthropic.api-key:}")
    private String apiKey;

    @Bean
    public AnthropicClient anthropicClient() {
        if (!StringUtils.hasText(apiKey)) {
            log.warn("anthropic.api-key is not set - AI recipe generation is disabled (endpoint returns 503).");
            return null;
        }
        log.info("Anthropic client initialised");
        return AnthropicOkHttpClient.builder().apiKey(apiKey).build();
    }
}
