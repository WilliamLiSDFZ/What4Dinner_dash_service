package today.what4dinner.what4dinner_dash_service.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.util.UUID;

/**
 * Spring Data JDBC aggregate for the {@code users} table. Minimal — it exists so
 * {@link today.what4dinner.what4dinner_dash_service.repository.UserRepository} has a
 * domain type; reads use explicit SQL projections.
 */
@Data
@Table("users")
public class User {

    @Id
    private UUID id;

    @Column("family_id")
    private UUID familyId;

    private String email;

    private String username;
}
