package today.what4dinner.what4dinner_dash_service.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Body of {@code POST /v1/recipe/generate/link}.
 *
 * <p>Deliberately the whole share blob rather than a bare URL: the share sheet produces text
 * like {@code 26 【…标题… - 小红书】 😆 JYvTxs7qDduiv59 😆 https://…}, and asking a user to pick
 * the URL out of that by hand is the kind of step people get wrong.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class GenerateFromLinkRequest {

    /** Required. A share link, or the whole share text with a link somewhere inside it. */
    private String shareText;
}
