package cn.codedog.exams;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
public interface ExamScoreRepository extends JpaRepository<ExamScore,Long> {
    Optional<ExamScore> findByExamIdAndStudentName(Long examId,String studentName);
}
