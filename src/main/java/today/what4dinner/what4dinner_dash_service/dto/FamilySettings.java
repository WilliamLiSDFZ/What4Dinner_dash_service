package today.what4dinner.what4dinner_dash_service.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * The family-scoped settings group. Shared by request and response because the shape is
 * identical; only the null semantics differ — on a {@code PATCH} request a null field
 * means "leave unchanged".
 *
 * <p>These live on the {@code family} row, so a change applies to every member.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class FamilySettings {

    /** IANA zone id, e.g. {@code America/Los_Angeles}. */
    private String timezone;

    /** ISO 4217 currency code, e.g. {@code USD}. */
    private String currencyUnit;
}
