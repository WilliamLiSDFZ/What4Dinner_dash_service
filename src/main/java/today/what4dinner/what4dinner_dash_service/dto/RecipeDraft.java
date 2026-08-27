package today.what4dinner.what4dinner_dash_service.dto;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Structured-output target for AI recipe generation. The Anthropic Java SDK derives the JSON
 * schema from this class, so there is no hand-written schema and no manual parsing.
 *
 * <p>Deliberately Lombok classes rather than records — schema derivation goes through Jackson,
 * which needs a no-args constructor and setters. Records produced a malformed schema and
 * garbage output when this was first written.
 *
 * <p>The {@code @JsonPropertyDescription} text is carried into the schema, so it is prompt,
 * not documentation.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class RecipeDraft {

    @JsonPropertyDescription("Dish name, in the same language as the photo. Concise, no punctuation.")
    private String title;

    @JsonPropertyDescription("One or two sentences describing the dish. Null if unclear.")
    private String description;

    @JsonPropertyDescription("Preparation time in minutes, or null if the photo does not say.")
    private Integer prepTimeMinutes;

    @JsonPropertyDescription("Cooking time in minutes, or null if the photo does not say.")
    private Integer cookTimeMinutes;

    @JsonPropertyDescription("Cooking steps in order. Each step is one self-contained action.")
    private List<DraftStep> steps;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class DraftStep {

        @JsonPropertyDescription("What to do in this step, in the photo's language.")
        private String instruction;

        @JsonPropertyDescription("True only if the step can be skipped without ruining the dish.")
        private Boolean isOptional;

        @JsonPropertyDescription("Ingredients used by this step.")
        private List<DraftIngredient> ingredients;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class DraftIngredient {

        @JsonPropertyDescription(
                "The key of an ingredient from the provided list, e.g. 'i3'. Use this whenever "
              + "the ingredient already exists in the list. Null if none of them match.")
        private String ingredientKey;

        @JsonPropertyDescription(
                "Name of a NEW ingredient not in the provided list. Set this only when "
              + "ingredientKey is null. Use the photo's language.")
        private String newIngredientName;

        @JsonPropertyDescription("Numeric quantity if stated, else null. Never negative.")
        private Double amount;

        @JsonPropertyDescription("Free-text quantity when not numeric, e.g. 'two handfuls'. Else null.")
        private String amountText;

        @JsonPropertyDescription("Unit of measure such as g, ml, tbsp. Null if not stated.")
        private String unit;

        @JsonPropertyDescription("True only if this ingredient is explicitly optional.")
        private Boolean isOptional;

        @JsonPropertyDescription("Preparation note such as 'finely chopped'. Null if none.")
        private String prepNote;
    }
}
