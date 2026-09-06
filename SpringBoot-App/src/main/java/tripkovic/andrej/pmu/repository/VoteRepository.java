package tripkovic.andrej.pmu.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import tripkovic.andrej.pmu.model.Vote;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface VoteRepository extends MongoRepository<Vote, String> {
    Optional<Vote> findByProblemIdAndVotedBy(String problemId, String votedBy);
    List<Vote> findByProblemId(String problemId);
    List<Vote> findByVotedByOrderByCreatedAtDesc(String votedBy);
    List<Vote> findByProblemIdAndCreatedAtAfter(String problemId, LocalDateTime since);
    List<Vote> findAllByOrderByCreatedAtDesc();
    void deleteByVotedBy(String votedBy);
    void deleteByProblemId(String problemId);
}
