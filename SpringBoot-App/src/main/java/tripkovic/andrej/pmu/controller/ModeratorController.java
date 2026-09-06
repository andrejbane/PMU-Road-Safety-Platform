package tripkovic.andrej.pmu.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tripkovic.andrej.pmu.dto.UserActivityDto;
import tripkovic.andrej.pmu.dto.UserProfileDto;
import tripkovic.andrej.pmu.model.RoadProblem;
import tripkovic.andrej.pmu.model.User;
import tripkovic.andrej.pmu.model.Vote;
import tripkovic.andrej.pmu.repository.RoadProblemRepository;
import tripkovic.andrej.pmu.repository.UserRepository;
import tripkovic.andrej.pmu.repository.VoteRepository;
import tripkovic.andrej.pmu.service.ProblemEnricher;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/moderator")
@RequiredArgsConstructor
public class ModeratorController {

    private final UserRepository userRepository;
    private final RoadProblemRepository roadProblemRepository;
    private final VoteRepository voteRepository;
    private final ProblemEnricher problemEnricher;

    @GetMapping("/users")
    public List<UserProfileDto> getAllUsers() {
        return userRepository.findAll().stream()
                .map(this::toProfileDto)
                .collect(Collectors.toList());
    }

    @GetMapping("/users/{id}")
    public ResponseEntity<UserActivityDto> getUserActivity(@PathVariable String id) {
        User user = userRepository.findById(id).orElse(null);
        if (user == null) return ResponseEntity.notFound().build();

        List<String> reporters = reporterAliases(user);
        List<RoadProblem> problems = roadProblemRepository.findByReportedByIn(reporters);
        List<Vote> votes = voteRepository.findByVotedByOrderByCreatedAtDesc(user.getUsername());

        UserActivityDto dto = new UserActivityDto();
        dto.setUser(toProfileDto(user));
        dto.setProblems(problems);
        dto.setVotes(votes);
        return ResponseEntity.ok(dto);
    }

    @PutMapping("/users/{id}/disable")
    public ResponseEntity<Void> disableUser(@PathVariable String id) {
        User user = userRepository.findById(id).orElse(null);
        if (user == null) return ResponseEntity.notFound().build();
        user.setDisabled(true);
        userRepository.save(user);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/users/{id}/enable")
    public ResponseEntity<Void> enableUser(@PathVariable String id) {
        User user = userRepository.findById(id).orElse(null);
        if (user == null) return ResponseEntity.notFound().build();
        user.setDisabled(false);
        userRepository.save(user);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/users/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable String id) {
        User user = userRepository.findById(id).orElse(null);
        if (user == null) return ResponseEntity.notFound().build();

        List<String> reporters = reporterAliases(user);
        List<RoadProblem> problems = roadProblemRepository.findByReportedByIn(reporters);
        List<String> problemIds = problems.stream().map(RoadProblem::getId).collect(Collectors.toList());
        for (String pid : problemIds) {
            voteRepository.deleteByProblemId(pid);
        }
        roadProblemRepository.deleteAll(problems);

        voteRepository.deleteByVotedBy(user.getUsername());

        userRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/problems")
    public List<RoadProblem> getAllProblems() {
        List<RoadProblem> problems = roadProblemRepository.findAllByOrderByCreatedAtDesc();
        problemEnricher.enrich(problems);
        return problems;
    }

    @DeleteMapping("/problems/{id}")
    public ResponseEntity<Void> deleteProblem(@PathVariable String id) {
        if (!roadProblemRepository.existsById(id)) return ResponseEntity.notFound().build();
        voteRepository.deleteByProblemId(id);
        roadProblemRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/problems/{id}/votes")
    public ResponseEntity<List<Vote>> getProblemVotes(@PathVariable String id) {
        if (!roadProblemRepository.existsById(id)) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(voteRepository.findByProblemId(id));
    }

    @DeleteMapping("/votes/{id}")
    public ResponseEntity<Void> deleteVote(@PathVariable String id) {
        Vote vote = voteRepository.findById(id).orElse(null);
        if (vote == null) return ResponseEntity.notFound().build();

        voteRepository.deleteById(id);

        roadProblemRepository.findById(vote.getProblemId()).ifPresent(problem -> {
            List<Vote> remaining = voteRepository.findByProblemId(problem.getId());
            problem.setUpvotes((int) remaining.stream().filter(v -> "UPVOTE".equals(v.getVoteType())).count());
            problem.setDownvotes((int) remaining.stream().filter(v -> "DOWNVOTE".equals(v.getVoteType())).count());
            roadProblemRepository.save(problem);
        });

        return ResponseEntity.noContent().build();
    }

    private UserProfileDto toProfileDto(User user) {
        UserProfileDto dto = new UserProfileDto();
        dto.setId(user.getId());
        dto.setUsername(user.getUsername());
        dto.setEmail(user.getEmail());
        dto.setName(user.getName());
        dto.setDisplayUsername(user.getDisplayUsername());
        dto.setRole(user.getRole());
        dto.setDisabled(user.isDisabled());
        return dto;
    }

    private List<String> reporterAliases(User user) {
        List<String> aliases = new ArrayList<>();
        aliases.add(user.getUsername());
        if (user.getDisplayUsername() != null && !user.getDisplayUsername().isBlank()) {
            aliases.add(user.getDisplayUsername());
        }
        return aliases;
    }
}
