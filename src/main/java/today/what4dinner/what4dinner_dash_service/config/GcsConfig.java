package today.what4dinner.what4dinner_dash_service.config;

import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.util.StringUtils;

import java.io.InputStream;

/**
 * Builds the GCS {@link Storage} client from a service-account JSON key, mirroring the
 * PEM-loading convention in {@link JwtConfig}.
 *
 * <p>Deliberately degrades rather than failing startup: if {@code gcs.bucket} is unset or
 * the credentials file is absent, this returns {@code null} so the rest of the app still
 * boots. The upload endpoint then answers {@code 503} instead of the whole service being
 * down. The project id is not configured separately — it is read from the key file.
 */
@Configuration
public class GcsConfig {

    private static final Logger log = LoggerFactory.getLogger(GcsConfig.class);

    @Value("${gcs.bucket:}")
    private String bucket;

    @Value("${gcs.credentials:}")
    private Resource credentialsResource;

    @Bean
    public Storage gcsStorage() {
        if (!StringUtils.hasText(bucket)) {
            log.warn("gcs.bucket is not set - image upload URLs are disabled (endpoint returns 503).");
            return null;
        }
        if (credentialsResource == null || !credentialsResource.exists()) {
            log.warn("GCS credentials not found at {} - image upload URLs are disabled (endpoint returns 503).",
                    credentialsResource);
            return null;
        }
        try (InputStream in = credentialsResource.getInputStream()) {
            Storage storage = StorageOptions.newBuilder()
                    .setCredentials(ServiceAccountCredentials.fromStream(in))
                    .build()
                    .getService();
            log.info("GCS storage client initialised for bucket {}", bucket);
            return storage;
        } catch (Exception e) {
            log.warn("Failed to initialise GCS storage client - image upload URLs are disabled: {}",
                    e.getMessage());
            return null;
        }
    }
}
