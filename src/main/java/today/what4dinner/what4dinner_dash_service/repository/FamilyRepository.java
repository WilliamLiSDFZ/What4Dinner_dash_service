package today.what4dinner.what4dinner_dash_service.repository;

import today.what4dinner.what4dinner_dash_service.model.Family;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

/**
 * The inherited {@code findById} is all this needs — the family is always looked up by
 * the id resolved from the caller's JWT, never by anything from the request.
 */
public interface FamilyRepository extends CrudRepository<Family, UUID> {

    /**
     * Partial update of the family settings. COALESCE gives PATCH semantics in a single
     * atomic statement: a null argument leaves that column at its current value, so there
     * is no read-modify-write race.
     */
    @Modifying
    @Query("""
            UPDATE family
            SET timezone = COALESCE(:timezone, timezone),
                currency_unit = COALESCE(:currencyUnit, currency_unit)
            WHERE id = :familyId
            """)
    void updateSettings(@Param("familyId") UUID familyId,
                        @Param("timezone") String timezone,
                        @Param("currencyUnit") String currencyUnit);
}
