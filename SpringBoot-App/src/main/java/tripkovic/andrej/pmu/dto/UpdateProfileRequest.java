package tripkovic.andrej.pmu.dto;

import lombok.Data;

@Data
public class UpdateProfileRequest {
    private String displayUsername;
    private String currentPassword;
    private String newPassword;
    private String photoBase64; // null = no change, "" = remove, "data..." = set new
}
