package today.what4dinner.what4dinner_dash_service.service;

import today.what4dinner.what4dinner_dash_service.dto.UserProfile;

import java.util.UUID;

public interface UserService {

    /**
     * Profile of the given user.
     *
     * @throws org.springframework.web.server.ResponseStatusException 401 if the user row is gone
     */
    UserProfile getProfile(UUID userId);
}
