package tripkovic.andrej.pmu.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import tripkovic.andrej.pmu.model.RoadProblem;

import java.util.List;

public interface RoadProblemRepository extends MongoRepository<RoadProblem, String> {
    List<RoadProblem> findAllByHiddenFalseOrderByCreatedAtDesc();
    List<RoadProblem> findByReportedByOrderByCreatedAtDesc(String reportedBy);
    List<RoadProblem> findAllByOrderByCreatedAtDesc();
    List<RoadProblem> findByReportedByIn(java.util.Collection<String> reportedBy);
}
