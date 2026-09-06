package tripkovic.andrej.pmu.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import tripkovic.andrej.pmu.model.User;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends MongoRepository<User, String> {
    Optional<User> findByUsername(String username);
    Optional<User> findByEmail(String email);
    boolean existsByUsername(String username);
    boolean existsByEmail(String email);
    List<User> findByUsernameIn(Collection<String> usernames);
    List<User> findByDisplayUsernameIn(Collection<String> displayUsernames);
}
