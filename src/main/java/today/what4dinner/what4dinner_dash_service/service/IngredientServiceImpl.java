package today.what4dinner.what4dinner_dash_service.service;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import today.what4dinner.what4dinner_dash_service.dto.CreateIngredientRequest;
import today.what4dinner.what4dinner_dash_service.dto.IngredientSummary;
import today.what4dinner.what4dinner_dash_service.repository.IngredientRepository;
import today.what4dinner.what4dinner_dash_service.repository.UserRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class IngredientServiceImpl implements IngredientService {

    private final IngredientRepository ingredientRepository;

    private final UserRepository userRepository;

    public IngredientServiceImpl(IngredientRepository ingredientRepository, UserRepository userRepository) {
        this.ingredientRepository = ingredientRepository;
        this.userRepository = userRepository;
    }

    @Override
    public List<IngredientSummary> getIngredientsForUser(UUID userId) {
        return ingredientRepository.findByFamilyId(familyOf(userId));
    }

    /**
     * {@code @Transactional} is required, not decorative: the Spring Data JDBC repository
     * proxy carries {@code @Transactional(readOnly = true)} metadata, and PostgreSQL
     * rejects writes inside a read-only transaction.
     */
    @Override
    @Transactional
    public IngredientSummary createIngredient(UUID userId, CreateIngredientRequest request) {
        UUID familyId = familyOf(userId);

        String canonicalName = request.getName() == null ? "" : request.getName().trim();
        if (canonicalName.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "name is required");
        }
        UUID categoryId = request.getCategoryId();
        if (categoryId != null && ingredientRepository.countCategoryById(categoryId) == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown categoryId");
        }
        Double price = request.getReferencePrice();
        if (price != null && price < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "referencePrice must not be negative");
        }
        // Resolve to a concrete value: inserting NULL would override the column default of 0.
        double referencePrice = price == null ? 0d : price;
        // The API takes a date; the column is a timestamp, so anchor it at start of day.
        LocalDateTime lastPurchase = request.getLastPurchase() == null
                ? null
                : request.getLastPurchase().atStartOfDay();
        // The schema has no unique constraint on the name, so this is enforced here only.
        if (ingredientRepository.countByFamilyIdAndName(familyId, canonicalName) > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ingredient name already exists");
        }

        UUID id = UUID.randomUUID();
        try {
            ingredientRepository.insert(id, familyId, canonicalName, categoryId, referencePrice, lastPurchase);
        } catch (DataIntegrityViolationException e) {
            // Backstop in case a unique constraint is added to the schema later.
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ingredient name already exists");
        }
        return ingredientRepository.findByFamilyIdAndId(familyId, id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                        "Ingredient vanished after insert"));
    }

    @Override
    @Transactional
    public void deleteIngredient(UUID userId, UUID ingredientId) {
        UUID familyId = familyOf(userId);

        // Scoping the lookup by family is what stops a cross-family delete.
        ingredientRepository.findByFamilyIdAndId(familyId, ingredientId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ingredient not found"));

        long references = ingredientRepository.countReferences(ingredientId);
        if (references > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Ingredient is used by " + references + " recipe(s) or step(s)");
        }
        ingredientRepository.deleteByFamilyIdAndId(familyId, ingredientId);
    }

    private UUID familyOf(UUID userId) {
        return userRepository.findFamilyIdById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unknown user"));
    }
}
