package tripkovic.andrej.pmu.dto;

import lombok.Data;

@Data
public class RoadProblemRequest {
    private double latitude;
    private double longitude;
    private String title;
    private String description;
    private String type;
    private String severity;
    private String roadSide;
    private Double directionBearing;
    private String photoBase64;
}
