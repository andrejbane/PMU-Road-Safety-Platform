package tripkovic.andrej.pmu.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tripkovic.andrej.pmu.model.RoadProblem;
import tripkovic.andrej.pmu.model.User;
import tripkovic.andrej.pmu.repository.UserRepository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class ProblemEnricher {

    private final UserRepository userRepository;

    public static boolean isModeratorRole(String role) {
        return "MODERATOR".equalsIgnoreCase(role) || "REPORT_MODERATOR".equalsIgnoreCase(role);
    }

    /**
     * Resolves reporter display names and recomputes the official flag from the
     * reporter's current role, so it also covers reports created before the
     * flag existed or before a promotion.
     */
    public void enrich(List<RoadProblem> problems) {
        Set<String> reporters = problems.stream()
                .map(RoadProblem::getReportedBy)
                .filter(r -> r != null && !r.isBlank())
                .collect(Collectors.toSet());
        if (reporters.isEmpty()) return;

        // reportedBy may hold either the username (email) or the display username
        Map<String, User> reporterToUser = new HashMap<>();
        userRepository.findByUsernameIn(reporters).forEach(u -> reporterToUser.put(u.getUsername(), u));
        userRepository.findByDisplayUsernameIn(reporters).forEach(u -> reporterToUser.put(u.getDisplayUsername(), u));

        problems.forEach(p -> {
            User reporter = p.getReportedBy() != null ? reporterToUser.get(p.getReportedBy()) : null;
            if (reporter == null) return;
            if (isModeratorRole(reporter.getRole())) p.setOfficial(true);
            if (reporter.getDisplayUsername() != null && !reporter.getDisplayUsername().isBlank()) {
                p.setReportedBy(reporter.getDisplayUsername());
            }
        });
    }
}
