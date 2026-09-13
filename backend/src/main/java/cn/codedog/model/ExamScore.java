package cn.codedog.model;
import jakarta.persistence.*;

@Entity @Table(name="exam_scores",uniqueConstraints=@UniqueConstraint(columnNames={"exam_id","student_name"}))
public class ExamScore {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
    @Column(name="exam_id",nullable=false) public Long examId;
    @Column(name="student_name",nullable=false,length=100) public String studentName;
    @Column(nullable=false) public boolean absent=false;
    @Column(name="score_values",nullable=false,columnDefinition="TEXT") public String scoreValues;
}
