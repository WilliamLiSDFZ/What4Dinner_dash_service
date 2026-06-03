package today.what4dinner.what4dinner_dash_service.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.util.UUID;

/**
 * Spring Data JDBC aggregate for the {@code recipes} table. Currently minimal — it
 * serves as the domain type for {@link today.what4dinner.what4dinner_dash_service.repository.RecipeRepository};
 * read queries use explicit SQL projections.
 */
@Data
@Table("recipes")
public class Recipe {

    @Id
    private UUID id;

    @Column("user_id")
    private UUID userId;

    private String title;

    private String description;

    private String status;
}
