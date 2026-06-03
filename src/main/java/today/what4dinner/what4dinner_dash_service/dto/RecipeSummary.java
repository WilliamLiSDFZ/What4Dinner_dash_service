package today.what4dinner.what4dinner_dash_service.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Lightweight projection of a recipe returned by {@code GET /v1/recipe}.
 * Field names match the selected column names so Spring Data JDBC maps them by name.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class RecipeSummary {

    private UUID id;

    private String title;

    private String description;

    private String status;
}
