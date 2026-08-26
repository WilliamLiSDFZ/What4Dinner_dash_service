package today.what4dinner.what4dinner_dash_service.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Internal flat projection: every step image key of a recipe in one query, carrying the
 * owning {@code stepId} for grouping. Never serialized — the response exposes signed URLs,
 * not keys.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class StepImageRow {

    private UUID stepId;

    private String storageKey;
}
