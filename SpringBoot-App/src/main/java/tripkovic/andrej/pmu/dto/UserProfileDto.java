package tripkovic.andrej.pmu.dto;

import lombok.Data;

@Data
public class UserProfileDto {
    private String id;
    private String username;
    private String email;
    private String name;
    private String displayUsername;
    private String role;
    private boolean disabled;
}
