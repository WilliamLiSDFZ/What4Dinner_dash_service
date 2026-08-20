package today.what4dinner.what4dinner_dash_service.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.util.UUID;

/**
 * Spring Data JDBC aggregate for the {@code ingredients} table. Rows are owned by a
 * family, not a user. Reads use explicit SQL projections.
 */
@Data
@Table("ingredients")
public class Ingredient {

    @Id
    private UUID id;

    @Column("family_id")
    private UUID familyId;

    @Column("canonical_name")
    private String canonicalName;

    @Column("category_id")
    private UUID categoryId;
}
