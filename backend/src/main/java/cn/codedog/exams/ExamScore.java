package cn.codedog.exams;
import jakarta.persistence.*;

@Entity @Table(name="exam_scores",uniqueConstraints=@UniqueConstraint(columnNames={"exam_id","student_name"}))
public class ExamScore {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) Long id;
    @Column(name="exam_id",nullable=false) Long examId;
    @Column(name="student_name",nullable=false,length=100) String studentName;
    @Column(name="score_values",nullable=false,columnDefinition="TEXT") String scoreValues;
}
