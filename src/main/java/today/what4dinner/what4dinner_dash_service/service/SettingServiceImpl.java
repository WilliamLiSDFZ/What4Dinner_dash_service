package today.what4dinner.what4dinner_dash_service.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import today.what4dinner.what4dinner_dash_service.dto.FamilySettings;
import today.what4dinner.what4dinner_dash_service.dto.SettingsResponse;
import today.what4dinner.what4dinner_dash_service.dto.UpdateSettingsRequest;
import today.what4dinner.what4dinner_dash_service.model.Family;
import today.what4dinner.what4dinner_dash_service.repository.FamilyRepository;
import today.what4dinner.what4dinner_dash_service.repository.UserRepository;

import java.time.ZoneId;
import java.util.Currency;
import java.util.Set;
import java.util.UUID;

@Service
public class SettingServiceImpl implements SettingService {

    /** Cached once - building this set per request would be wasteful. */
    private static final Set<String> ZONE_IDS = ZoneId.getAvailableZoneIds();

    private final FamilyRepository familyRepository;

    private final UserRepository userRepository;

    public SettingServiceImpl(FamilyRepository familyRepository, UserRepository userRepository) {
        this.familyRepository = familyRepository;
        this.userRepository = userRepository;
    }

    @Override
    public SettingsResponse getSettings(UUID userId) {
        return toResponse(loadFamily(userId));
    }

    /**
     * {@code @Transactional} is required, not decorative: the Spring Data JDBC repository
     * proxy carries {@code @Transactional(readOnly = true)} metadata, and PostgreSQL
     * rejects writes inside a read-only transaction.
     */
    @Override
    @Transactional
    public SettingsResponse updateSettings(UUID userId, UpdateSettingsRequest request) {
        // Validate before touching the database: a malformed value can never succeed, so
        // there is no point spending a query on it.
        FamilySettings requested = request == null ? null : request.getFamily();
        String timezone = requested == null ? null : normalizeTimezone(requested.getTimezone());
        String currencyUnit = requested == null ? null : normalizeCurrency(requested.getCurrencyUnit());

        Family family = loadFamily(userId);

        if (timezone != null || currencyUnit != null) {
            familyRepository.updateSettings(family.getId(), timezone, currencyUnit);
            family = familyRepository.findById(family.getId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Family not found"));
        }
        return toResponse(family);
    }

    /** Null stays null (meaning "leave unchanged"); anything else must be a real IANA zone. */
    private String normalizeTimezone(String timezone) {
        if (timezone == null) {
            return null;
        }
        String trimmed = timezone.trim();
        if (!ZONE_IDS.contains(trimmed)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "timezone must be a valid IANA zone id, e.g. America/Los_Angeles");
        }
        return trimmed;
    }

    /** Upper-cased before validating, so "cny" is accepted as CNY. */
    private String normalizeCurrency(String currencyUnit) {
        if (currencyUnit == null) {
            return null;
        }
        String code = currencyUnit.trim().toUpperCase();
        try {
            Currency.getInstance(code);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "currencyUnit must be a valid ISO 4217 code, e.g. USD");
        }
        return code;
    }

    private Family loadFamily(UUID userId) {
        UUID familyId = userRepository.findFamilyIdById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unknown user"));
        return familyRepository.findById(familyId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Family not found"));
    }

    private SettingsResponse toResponse(Family family) {
        return new SettingsResponse(new FamilySettings(family.getTimezone(), family.getCurrencyUnit()));
    }
}
