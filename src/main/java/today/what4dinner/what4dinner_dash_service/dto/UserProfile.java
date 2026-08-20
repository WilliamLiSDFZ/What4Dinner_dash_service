package today.what4dinner.what4dinner_dash_service.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Profile of the authenticated user, returned by {@code GET /v1/user/me}.
 * Deliberately has no password field — {@code password_hash} is never selected.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserProfile {

    private UUID id;

    private UUID familyId;

    private String email;

    private String username;

    private Boolean activated;

    private Integer seenTourVersion;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
