package tripkovic.andrej.pmu.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import tripkovic.andrej.pmu.dto.RoadProblemRequest;
import tripkovic.andrej.pmu.model.RoadProblem;
import tripkovic.andrej.pmu.model.User;
import tripkovic.andrej.pmu.repository.RoadProblemRepository;
import tripkovic.andrej.pmu.repository.UserRepository;
import tripkovic.andrej.pmu.service.ProblemEnricher;

import java.util.List;

@RestController
@RequestMapping("/api/problems")
@RequiredArgsConstructor
public class RoadProblemController {

    private final RoadProblemRepository roadProblemRepository;
    private final UserRepository userRepository;
    private final ProblemEnricher problemEnricher;

    @GetMapping
    public List<RoadProblem> getAll() {
        List<RoadProblem> problems = roadProblemRepository.findAllByHiddenFalseOrderByCreatedAtDesc();
        problemEnricher.enrich(problems);
        return problems;
    }

    @GetMapping("/{id}")
    public ResponseEntity<RoadProblem> getById(@PathVariable String id) {
        return roadProblemRepository.findById(id)
                .map(p -> { problemEnricher.enrich(List.of(p)); return ResponseEntity.ok(p); })
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/my")
    public List<RoadProblem> getMyReports(@AuthenticationPrincipal UserDetails userDetails) {
        List<RoadProblem> problems = roadProblemRepository.findByReportedByOrderByCreatedAtDesc(userDetails.getUsername());
        problemEnricher.enrich(problems);
        return problems;
    }

    @PostMapping
    public ResponseEntity<RoadProblem> create(
            @RequestBody RoadProblemRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {

        User user = userRepository.findByUsername(userDetails.getUsername()).orElseThrow();
        String reporter = (user.getDisplayUsername() != null && !user.getDisplayUsername().isBlank())
                ? user.getDisplayUsername()
                : user.getUsername();

        RoadProblem problem = new RoadProblem();
        problem.setLatitude(request.getLatitude());
        problem.setLongitude(request.getLongitude());
        problem.setTitle(request.getTitle());
        problem.setDescription(request.getDescription());
        problem.setType(request.getType());
        problem.setSeverity(request.getSeverity());
        problem.setRoadSide(request.getRoadSide() != null ? request.getRoadSide() : "BOTH");
        problem.setDirectionBearing(request.getDirectionBearing());
        problem.setUserReport(true);
        problem.setOfficial(ProblemEnricher.isModeratorRole(user.getRole()));
        problem.setReportedBy(reporter);
        problem.setPhotoBase64(request.getPhotoBase64());

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(roadProblemRepository.save(problem));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable String id,
            @AuthenticationPrincipal UserDetails userDetails) {

        RoadProblem problem = roadProblemRepository.findById(id).orElse(null);
        if (problem == null) {
            return ResponseEntity.notFound().build();
        }

        User user = userRepository.findByUsername(userDetails.getUsername()).orElseThrow();
        boolean isOwner = problem.getReportedBy().equals(userDetails.getUsername())
                || (user.getDisplayUsername() != null && problem.getReportedBy().equals(user.getDisplayUsername()));
        if (!isOwner) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        roadProblemRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
