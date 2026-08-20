package today.what4dinner.what4dinner_dash_service.repository;

import today.what4dinner.what4dinner_dash_service.model.Family;
import org.springframework.data.repository.CrudRepository;

import java.util.UUID;

/**
 * The inherited {@code findById} is all this needs — the family is always looked up by
 * the id resolved from the caller's JWT, never by anything from the request.
 */
public interface FamilyRepository extends CrudRepository<Family, UUID> {
}
