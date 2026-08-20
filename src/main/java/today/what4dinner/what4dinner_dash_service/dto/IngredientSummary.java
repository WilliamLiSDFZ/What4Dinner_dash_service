package today.what4dinner.what4dinner_dash_service.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Projection of an ingredient returned by {@code GET /v1/ingredient}.
 * Field names match the selected columns (underscores stripped) so Spring Data JDBC
 * maps them by name.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class IngredientSummary {

    private UUID id;

    private String canonicalName;

    private UUID categoryId;

    private Double referencePrice;

    private LocalDateTime lastPurchase;
}
