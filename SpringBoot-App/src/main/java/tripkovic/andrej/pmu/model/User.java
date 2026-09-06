package tripkovic.andrej.pmu.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.index.Indexed;

@Data
@Document(collection = "users")
public class User {

    @Id
    private String id;

    @Indexed(unique = true)
    private String username;

    @Indexed(unique = true)
    private String email;

    private String name;

    private String displayUsername;

    private String password;

    private String role = "USER";

    private boolean disabled = false;

    private String photoBase64;
}
