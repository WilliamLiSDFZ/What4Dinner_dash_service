package today.what4dinner.what4dinner_dash_service.service;

import today.what4dinner.what4dinner_dash_service.dto.FamilyInfo;

import java.util.UUID;

public interface FamilyService {

    /**
     * The family the given user belongs to, including its members and a signed URL for the
     * background image (null when there is none).
     *
     * @throws org.springframework.web.server.ResponseStatusException 401 if the user row is gone;
     *         404 if the family row is gone
     */
    FamilyInfo getFamilyForUser(UUID userId);
}
