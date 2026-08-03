package cn.codedog.repository;

import cn.codedog.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);
    Optional<User> findByTeacherPublicIdIgnoreCase(String teacherPublicId);
    boolean existsByUsernameIgnoreCase(String username);
    List<User> findAllByOrderByCreatedAtAscIdAsc();
}
