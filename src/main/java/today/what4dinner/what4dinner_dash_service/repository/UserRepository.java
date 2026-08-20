package today.what4dinner.what4dinner_dash_service.repository;

import today.what4dinner.what4dinner_dash_service.model.User;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends CrudRepository<User, UUID> {

    /**
     * Resolves the family a user belongs to. Every user is required to be in a family,
     * so an empty result means the user row is gone.
     */
    @Query("SELECT family_id FROM users WHERE id = :userId")
    Optional<UUID> findFamilyIdById(@Param("userId") UUID userId);
}
