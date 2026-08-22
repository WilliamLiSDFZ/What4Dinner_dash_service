package today.what4dinner.what4dinner_dash_service.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import today.what4dinner.what4dinner_dash_service.dto.SettingsResponse;
import today.what4dinner.what4dinner_dash_service.dto.UpdateSettingsRequest;
import today.what4dinner.what4dinner_dash_service.service.SettingService;

import java.util.UUID;

@RestController
@RequestMapping("/v1/setting")
public class SettingController {

    private final SettingService settingService;

    public SettingController(SettingService settingService) {
        this.settingService = settingService;
    }

    /**
     * Returns the settings visible to the authenticated user, grouped by scope. The family
     * is resolved from the JWT {@code sub} claim, never from the request.
     */
    @GetMapping
    public ResponseEntity<SettingsResponse> getSettings(@AuthenticationPrincipal Jwt jwt) {
        UUID userId = UUID.fromString(jwt.getSubject());
        return ResponseEntity.ok(settingService.getSettings(userId));
    }

    /**
     * Applies a partial settings update. Omitted groups and omitted fields are left
     * unchanged, so an empty body is a no-op that simply returns the current settings.
     */
    @PatchMapping
    public ResponseEntity<SettingsResponse> updateSettings(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody(required = false) UpdateSettingsRequest request) {

        UUID userId = UUID.fromString(jwt.getSubject());
        return ResponseEntity.ok(settingService.updateSettings(userId, request));
    }
}
