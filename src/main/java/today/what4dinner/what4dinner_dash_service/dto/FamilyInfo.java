package today.what4dinner.what4dinner_dash_service.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * The caller's family, returned by {@code GET /v1/family}.
 *
 * <p>{@code backgroundImageUrl} is a short-lived signed GET URL, not an object key: the
 * bucket enforces public-access prevention, so a key could not be rendered by a client.
 * It is {@code null} when the family has no background image (or storage is unconfigured).
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class FamilyInfo {

    private UUID id;

    private String familyName;

    private String backgroundImageUrl;

    /** IANA zone id. Also available, grouped, from {@code GET /v1/setting}. */
    private String timezone;

    /** ISO 4217 currency code. Also available, grouped, from {@code GET /v1/setting}. */
    private String currencyUnit;

    private LocalDateTime createdAt;

    private List<FamilyMember> members;
}
