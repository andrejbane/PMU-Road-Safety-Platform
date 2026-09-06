package tripkovic.andrej.pmu.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class AuthResponse {
    private String token;
    private String username;
    private String displayUsername;
    private String name;
    private String email;
    private String role;
    private String photoBase64;
}
