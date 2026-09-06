package tripkovic.andrej.pmu.dto;

import lombok.Data;
import tripkovic.andrej.pmu.model.RoadProblem;
import tripkovic.andrej.pmu.model.Vote;

import java.util.List;

@Data
public class UserActivityDto {
    private UserProfileDto user;
    private List<RoadProblem> problems;
    private List<Vote> votes;
}
