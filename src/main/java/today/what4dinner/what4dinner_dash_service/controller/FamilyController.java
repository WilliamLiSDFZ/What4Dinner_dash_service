package today.what4dinner.what4dinner_dash_service.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import today.what4dinner.what4dinner_dash_service.dto.FamilyInfo;
import today.what4dinner.what4dinner_dash_service.service.FamilyService;

import java.util.UUID;

@RestController
@RequestMapping("/v1/family")
public class FamilyController {

    private final FamilyService familyService;

    public FamilyController(FamilyService familyService) {
        this.familyService = familyService;
    }

    /**
     * Returns the family the authenticated user belongs to, including its members and a
     * short-lived signed URL for the background image ({@code null} when there is none).
     * The family is resolved from the JWT {@code sub} claim, never from the request.
     */
    @GetMapping
    public ResponseEntity<FamilyInfo> getMyFamily(@AuthenticationPrincipal Jwt jwt) {
        UUID userId = UUID.fromString(jwt.getSubject());
        return ResponseEntity.ok(familyService.getFamilyForUser(userId));
    }
}
