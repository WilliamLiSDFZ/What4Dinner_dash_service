package today.what4dinner.what4dinner_dash_service.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import today.what4dinner.what4dinner_dash_service.dto.UploadUrlRequest;
import today.what4dinner.what4dinner_dash_service.dto.UploadUrlResponse;
import today.what4dinner.what4dinner_dash_service.service.ImageUploadService;

import java.util.UUID;

@RestController
@RequestMapping("/v1/image")
public class ImageController {

    private final ImageUploadService imageUploadService;

    public ImageController(ImageUploadService imageUploadService) {
        this.imageUploadService = imageUploadService;
    }

    /**
     * Returns a short-lived signed PUT URL and the object key the client should report
     * back later. The key is generated entirely server-side. The family is resolved from
     * the JWT {@code sub} claim.
     */
    @PostMapping("/upload-url")
    public ResponseEntity<UploadUrlResponse> createUploadUrl(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody(required = false) UploadUrlRequest request) {

        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "purpose and contentType are required");
        }
        UUID userId = UUID.fromString(jwt.getSubject());
        return ResponseEntity.ok(imageUploadService.createUploadUrl(
                userId, request.getPurpose(), request.getContentType()));
    }
}
