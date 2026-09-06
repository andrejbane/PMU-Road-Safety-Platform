package tripkovic.andrej.pmu.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import tripkovic.andrej.pmu.model.RoadProblem;

import java.time.LocalDateTime;

@Data
public class VoteResponse {
    private String voteId;
    private String voteType;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime votedAt;

    private RoadProblem problem;
}
