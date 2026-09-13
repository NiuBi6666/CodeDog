package cn.codedog.exams;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
public interface ExamRepository extends JpaRepository<Exam,Long> {
    Optional<Exam> findByPublicId(String publicId);
}
