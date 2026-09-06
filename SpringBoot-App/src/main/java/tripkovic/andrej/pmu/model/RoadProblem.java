package tripkovic.andrej.pmu.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Data
@Document(collection = "road_problems")
public class RoadProblem {

    @Id
    private String id;

    private double latitude;
    private double longitude;

    private String title;
    private String description;

    private String type;      // WORK_ON_ROAD, PROBLEM_ON_ROAD, OTHER
    private String severity;  // LOW, MEDIUM, HIGH, CANT_PASS

    private String roadSide = "BOTH"; // BOTH, MY_SIDE, OPPOSITE — relative to directionBearing
    private Double directionBearing;  // reporter's travel bearing in degrees (0–360), null if unknown

    private boolean userReport;
    private boolean official = false; // reported by a moderator or system-seeded
    private String reportedBy;
    private String photoBase64;

    private boolean hidden = false;
    private int upvotes = 0;
    private int downvotes = 0;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime createdAt = LocalDateTime.now();
}
