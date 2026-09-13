package cn.codedog.dao;
import cn.codedog.model.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.*;
public interface ExamRepository extends JpaRepository<Exam,Long> {
    Optional<Exam> findByPublicId(String publicId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Exam e where e.id = :id")
    Optional<Exam> findLockedById(@Param("id") Long id);
    @Query("select e.id from Exam e where e.queryCode is null order by e.id")
    List<Long> findIdsWithoutQueryCode();
}
