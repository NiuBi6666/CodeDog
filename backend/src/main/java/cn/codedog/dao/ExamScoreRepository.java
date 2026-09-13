package cn.codedog.dao;
import cn.codedog.model.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
public interface ExamScoreRepository extends JpaRepository<ExamScore,Long> {
    Optional<ExamScore> findByExamIdAndStudentName(Long examId,String studentName);
}
