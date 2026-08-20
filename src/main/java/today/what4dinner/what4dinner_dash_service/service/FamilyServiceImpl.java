package today.what4dinner.what4dinner_dash_service.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import today.what4dinner.what4dinner_dash_service.dto.FamilyInfo;
import today.what4dinner.what4dinner_dash_service.model.Family;
import today.what4dinner.what4dinner_dash_service.repository.FamilyRepository;
import today.what4dinner.what4dinner_dash_service.repository.UserRepository;

import java.util.UUID;

@Service
public class FamilyServiceImpl implements FamilyService {

    private final FamilyRepository familyRepository;

    private final UserRepository userRepository;

    private final ImageUploadService imageUploadService;

    public FamilyServiceImpl(FamilyRepository familyRepository,
                             UserRepository userRepository,
                             ImageUploadService imageUploadService) {
        this.familyRepository = familyRepository;
        this.userRepository = userRepository;
        this.imageUploadService = imageUploadService;
    }

    @Override
    public FamilyInfo getFamilyForUser(UUID userId) {
        UUID familyId = userRepository.findFamilyIdById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unknown user"));
        Family family = familyRepository.findById(familyId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Family not found"));

        // createReadUrl already yields null for a null/blank key, so "no background image"
        // needs no special case here.
        String backgroundImageUrl = imageUploadService.createReadUrl(family.getBackgroundImageKey());

        return new FamilyInfo(
                family.getId(),
                family.getFamilyName(),
                backgroundImageUrl,
                family.getCreatedAt(),
                userRepository.findMembersByFamilyId(familyId));
    }
}
