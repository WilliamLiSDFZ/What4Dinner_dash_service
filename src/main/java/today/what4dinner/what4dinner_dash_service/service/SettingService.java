package today.what4dinner.what4dinner_dash_service.service;

import today.what4dinner.what4dinner_dash_service.dto.SettingsResponse;
import today.what4dinner.what4dinner_dash_service.dto.UpdateSettingsRequest;

import java.util.UUID;

public interface SettingService {

    /**
     * The settings visible to the given user, grouped by scope.
     *
     * @throws org.springframework.web.server.ResponseStatusException 401 if the user row is
     *         gone; 404 if the family row is gone
     */
    SettingsResponse getSettings(UUID userId);

    /**
     * Applies a partial settings update and returns the resulting document. Values left
     * null are unchanged.
     *
     * @throws org.springframework.web.server.ResponseStatusException 400 for an invalid
     *         timezone or currency; 401 if the user row is gone; 404 if the family is gone
     */
    SettingsResponse updateSettings(UUID userId, UpdateSettingsRequest request);
}
