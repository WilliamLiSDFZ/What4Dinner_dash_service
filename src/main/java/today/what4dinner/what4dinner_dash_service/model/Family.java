package today.what4dinner.what4dinner_dash_service.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Spring Data JDBC aggregate for the {@code family} table.
 *
 * <p>{@code backgroundImageKey} is an object-storage key and is deliberately internal —
 * it is used only to mint a signed read URL and never reaches a response DTO, because the
 * bucket enforces public-access prevention and a bare key is useless to a client.
 */
@Data
@Table("family")
public class Family {

    @Id
    private UUID id;

    @Column("family_name")
    private String familyName;

    @Column("background_image_key")
    private String backgroundImageKey;

    private String timezone;

    @Column("currency_unit")
    private String currencyUnit;

    @Column("created_at")
    private LocalDateTime createdAt;
}
