package tripkovic.andrej.pmu.dto;

import lombok.Data;

@Data
public class VoteRequest {
    private String type; // UPVOTE or DOWNVOTE
}
