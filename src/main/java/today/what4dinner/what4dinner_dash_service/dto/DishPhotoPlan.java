package today.what4dinner.what4dinner_dash_service.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * What the vision model works out before a dish photo can be generated: which of the recipe's
 * original photos best shows the finished dish, what that dish looks like, and which cuisine
 * it belongs to.
 *
 * <p>One call answers all three because they are the same act of looking. Splitting them would
 * mean sending the same photos twice.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class DishPhotoPlan {

    /**
     * Label of the photo that best shows the finished dish, e.g. {@code "p2"}, or null when
     * none of them does. Null is expected and fine — many recipes only have step photos.
     */
    private String photoKey;

    /** How the finished dish looks, in English, detailed enough to paint from. */
    private String appearance;

    /** Lowercase English cuisine name, used to pick the restaurant in the background. */
    private String cuisine;
}
