package today.what4dinner.what4dinner_dash_service.repository;

import today.what4dinner.what4dinner_dash_service.dto.FamilyMember;
import today.what4dinner.what4dinner_dash_service.dto.UserProfile;
import today.what4dinner.what4dinner_dash_service.model.User;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends CrudRepository<User, UUID> {

    /**
     * Resolves the family a user belongs to. Every user is required to be in a family,
     * so an empty result means the user row is gone.
     */
    @Query("SELECT family_id FROM users WHERE id = :userId")
    Optional<UUID> findFamilyIdById(@Param("userId") UUID userId);

    /**
     * Profile of a single user. The column list is explicit so {@code password_hash} can
     * never leak into a response by accident.
     */
    @Query("""
            SELECT id, family_id, email, username, activated, seen_tour_version, created_at, updated_at
            FROM users
            WHERE id = :userId
            """)
    Optional<UserProfile> findProfileById(@Param("userId") UUID userId);

    /** Everyone in a family, oldest member first. */
    @Query("SELECT id, username, email FROM users WHERE family_id = :familyId ORDER BY created_at")
    List<FamilyMember> findMembersByFamilyId(@Param("familyId") UUID familyId);
}
