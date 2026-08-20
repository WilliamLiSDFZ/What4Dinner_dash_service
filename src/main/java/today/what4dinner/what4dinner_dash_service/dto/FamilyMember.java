package today.what4dinner.what4dinner_dash_service.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/** A member of the caller's family, as returned inside {@link FamilyInfo}. */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class FamilyMember {

    private UUID id;

    private String username;

    private String email;
}
