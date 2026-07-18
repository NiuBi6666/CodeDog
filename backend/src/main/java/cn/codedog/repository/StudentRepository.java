package cn.codedog.repository;

import cn.codedog.model.Student;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Collection;
import java.util.List;

public interface StudentRepository extends JpaRepository<Student, Long> {
    List<Student> findByUserIdIn(Collection<String> userIds);
    List<Student> findByNameInOrderByNameAscGradeAscClassNameAscUserIdAsc(Collection<String> names);
}
