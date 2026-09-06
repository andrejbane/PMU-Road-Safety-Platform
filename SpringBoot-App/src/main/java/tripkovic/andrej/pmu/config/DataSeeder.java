package tripkovic.andrej.pmu.config;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import tripkovic.andrej.pmu.model.RoadProblem;
import tripkovic.andrej.pmu.model.User;
import tripkovic.andrej.pmu.repository.RoadProblemRepository;
import tripkovic.andrej.pmu.repository.UserRepository;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final RoadProblemRepository roadProblemRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${bootstrap.moderator.email:}")
    private String moderatorEmail;

    @Value("${bootstrap.moderator.password:}")
    private String moderatorPassword;

    @Value("${bootstrap.report-moderator.email:}")
    private String reportModeratorEmail;

    @Value("${bootstrap.report-moderator.password:}")
    private String reportModeratorPassword;

    @Override
    public void run(String... args) {
        seedModerators();
        seedProblems();
    }

    private void seedModerators() {
        moderator(moderatorEmail, "Full Moderator", "Moderator", "MODERATOR", moderatorPassword);
        moderator(reportModeratorEmail, "Report Moderator", "ReportModerator",
                "REPORT_MODERATOR", reportModeratorPassword);
    }

    private void moderator(String email, String name, String displayUsername, String role, String password) {
        if (email.isBlank() && password.isBlank()) {
            log.info("Skipping {} bootstrap account because its credentials are not configured", role);
            return;
        }
        if (email.isBlank() || password.isBlank()) {
            throw new IllegalStateException(
                    "Both email and password must be configured for the " + role + " bootstrap account");
        }
        if (userRepository.existsByUsername(email)) return;
        User user = new User();
        user.setUsername(email);
        user.setEmail(email);
        user.setName(name);
        user.setDisplayUsername(displayUsername);
        user.setRole(role);
        user.setPassword(passwordEncoder.encode(password));
        userRepository.save(user);
    }

    private void seedProblems() {
        if (roadProblemRepository.count() > 0) return;

        roadProblemRepository.saveAll(List.of(
            problem(37.42206, -122.08409, "Work on the road",
                    "Street resurfacing in progress on Amphitheatre Pkwy.",
                    "WORK_ON_ROAD", "HIGH", "BOTH", null),
            problem(37.42310, -122.09220, "Problem on the road",
                    "Large pothole on Shoreline Blvd (northbound lane).",
                    "PROBLEM_ON_ROAD", "MEDIUM", "MY_SIDE", 350.0),
            problem(37.42574, -122.07840, "Work on the road",
                    "Pipe replacement on Charleston Rd — expect delays.",
                    "WORK_ON_ROAD", "CANT_PASS", "BOTH", null),
            problem(37.41935, -122.08570, "Problem on the road",
                    "Broken traffic light at Plymouth St intersection.",
                    "PROBLEM_ON_ROAD", "LOW", "BOTH", null),
            problem(37.42460, -122.08860, "Other",
                    "Fallen tree branch partially blocking the eastbound lane.",
                    "OTHER", "MEDIUM", "MY_SIDE", 90.0)
        ));
    }

    private RoadProblem problem(double lat, double lng, String title,
                                String description, String type, String severity,
                                String roadSide, Double directionBearing) {
        RoadProblem p = new RoadProblem();
        p.setLatitude(lat);
        p.setLongitude(lng);
        p.setTitle(title);
        p.setDescription(description);
        p.setType(type);
        p.setSeverity(severity);
        p.setRoadSide(roadSide);
        p.setDirectionBearing(directionBearing);
        p.setUserReport(false);
        p.setOfficial(true);
        p.setCreatedAt(LocalDateTime.now());
        return p;
    }
}
