package today.what4dinner.what4dinner_dash_service.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Body of {@code PATCH /v1/setting}. Nested the same way as {@link SettingsResponse}.
 *
 * <p>Partial at both levels: omit {@code family} entirely, or omit a field inside it, and
 * those values are left unchanged.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class UpdateSettingsRequest {

    private FamilySettings family;
}
