package cn.codedog.dao;
import cn.codedog.model.*;

import cn.codedog.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select u from User u where u.id=:id")
    Optional<User> findLockedById(@org.springframework.data.repository.query.Param("id") long id);
    Optional<User> findByTeacherPublicIdIgnoreCase(String teacherPublicId);
    boolean existsByUsernameIgnoreCase(String username);
    List<User> findAllByOrderByCreatedAtAscIdAsc();
}
