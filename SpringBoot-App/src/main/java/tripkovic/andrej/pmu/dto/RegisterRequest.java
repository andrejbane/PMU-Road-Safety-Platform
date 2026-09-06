package tripkovic.andrej.pmu.dto;

import lombok.Data;

@Data
public class RegisterRequest {
    private String username;
    private String name;
    private String email;
    private String password;
    private String displayUsername;
    private String photoBase64;
}
