package today.what4dinner.what4dinner_dash_service.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Settings document returned by {@code GET} / {@code PATCH /v1/setting}.
 *
 * <p>Settings are grouped by scope rather than flattened, so future groups (user,
 * notification, …) can be added as sibling keys without breaking this contract. Clients
 * should read {@code settings.family.timezone}, not a top-level {@code timezone}.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class SettingsResponse {

    private FamilySettings family;
}
