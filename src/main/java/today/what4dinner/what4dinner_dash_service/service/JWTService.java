package today.what4dinner.what4dinner_dash_service.service;

import java.util.Optional;

/**
 * Service for generating and validating JSON Web Tokens (JWT) used for authentication.
 */
public interface JWTService {

    /**
     * Generates a JWT with the configured default expiration.
     *
     * @param userId the unique identifier of the user (stored as the {@code sub} claim)
     * @param email  the email address of the user (stored as the {@code email} claim)
     * @return the encoded JWT string
     */
    String generateToken(String userId, String email);

    /**
     * Generates a short-lived JWT with a 15-minute expiration, typically used as an
     * OAuth2 redirect code that is exchanged for a full-duration token.
     *
     * @param userId the unique identifier of the user (stored as the {@code sub} claim)
     * @param email  the email address of the user (stored as the {@code email} claim)
     * @return the encoded JWT string
     */
    String generateShortTermToken(String userId, String email);

    /**
     * Validates the given token and issues a new full-duration token with the same claims.
     *
     * @param token the JWT string to validate and exchange
     * @return an {@link Optional} containing the new token, or empty if validation fails
     */
    Optional<String> exchangeToken(String token);
}
