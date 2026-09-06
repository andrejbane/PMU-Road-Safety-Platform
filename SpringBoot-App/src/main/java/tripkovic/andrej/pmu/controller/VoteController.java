package tripkovic.andrej.pmu.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import tripkovic.andrej.pmu.dto.VoteRequest;
import tripkovic.andrej.pmu.dto.VoteResponse;
import tripkovic.andrej.pmu.model.RoadProblem;
import tripkovic.andrej.pmu.model.Vote;
import tripkovic.andrej.pmu.repository.RoadProblemRepository;
import tripkovic.andrej.pmu.repository.VoteRepository;
import tripkovic.andrej.pmu.service.ProblemEnricher;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequiredArgsConstructor
public class VoteController {

    private final VoteRepository voteRepository;
    private final RoadProblemRepository roadProblemRepository;
    private final ProblemEnricher problemEnricher;

    @PostMapping("/api/problems/{id}/vote")
    public ResponseEntity<RoadProblem> vote(
            @PathVariable String id,
            @RequestBody VoteRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {

        RoadProblem problem = roadProblemRepository.findById(id).orElse(null);
        if (problem == null) return ResponseEntity.notFound().build();

        String username = userDetails.getUsername();
        Optional<Vote> existing = voteRepository.findByProblemIdAndVotedBy(id, username);

        if (existing.isPresent()) {
            Vote existingVote = existing.get();
            if (existingVote.getVoteType().equals(request.getType())) {
                voteRepository.delete(existingVote);
            } else {
                existingVote.setVoteType(request.getType());
                existingVote.setCreatedAt(LocalDateTime.now());
                voteRepository.save(existingVote);
            }
        } else {
            Vote vote = new Vote();
            vote.setProblemId(id);
            vote.setVotedBy(username);
            vote.setVoteType(request.getType());
            voteRepository.save(vote);
        }

        updateAndSave(problem);
        return ResponseEntity.ok(problem);
    }

    @DeleteMapping("/api/problems/{id}/vote")
    public ResponseEntity<RoadProblem> removeVote(
            @PathVariable String id,
            @AuthenticationPrincipal UserDetails userDetails) {

        RoadProblem problem = roadProblemRepository.findById(id).orElse(null);
        if (problem == null) return ResponseEntity.notFound().build();

        voteRepository.findByProblemIdAndVotedBy(id, userDetails.getUsername())
                .ifPresent(voteRepository::delete);

        updateAndSave(problem);
        return ResponseEntity.ok(problem);
    }

    @GetMapping("/api/votes/my")
    public List<VoteResponse> getMyVotes(@AuthenticationPrincipal UserDetails userDetails) {
        List<VoteResponse> responses = voteRepository.findByVotedByOrderByCreatedAtDesc(userDetails.getUsername())
                .stream()
                .map(v -> {
                    VoteResponse r = new VoteResponse();
                    r.setVoteId(v.getId());
                    r.setVoteType(v.getVoteType());
                    r.setVotedAt(v.getCreatedAt());
                    roadProblemRepository.findById(v.getProblemId()).ifPresent(r::setProblem);
                    return r;
                })
                .filter(r -> r.getProblem() != null)
                .collect(Collectors.toList());
        problemEnricher.enrich(responses.stream().map(VoteResponse::getProblem).collect(Collectors.toList()));
        return responses;
    }

    private void updateAndSave(RoadProblem problem) {
        List<Vote> allVotes = voteRepository.findByProblemId(problem.getId());
        problem.setUpvotes((int) allVotes.stream()
                .filter(v -> "UPVOTE".equals(v.getVoteType())).count());
        problem.setDownvotes((int) allVotes.stream()
                .filter(v -> "DOWNVOTE".equals(v.getVoteType())).count());

        LocalDateTime since = LocalDateTime.now().minusHours(24);
        List<Vote> recent = voteRepository.findByProblemIdAndCreatedAtAfter(problem.getId(), since);
        long recentDown = recent.stream().filter(v -> "DOWNVOTE".equals(v.getVoteType())).count();
        long recentUp = recent.stream().filter(v -> "UPVOTE".equals(v.getVoteType())).count();
        problem.setHidden((recentDown - recentUp) >= 5);

        roadProblemRepository.save(problem);
    }
}
