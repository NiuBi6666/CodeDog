package cn.codedog.model;
import jakarta.persistence.*;
import org.springframework.data.domain.Persistable;
@Entity @Table(name="exam_query_aliases")
public class ExamQueryAlias implements Persistable<String> {
    @Id @Column(length=8) public String code;
    @Column(name="exam_id",nullable=false) public Long examId;
    protected ExamQueryAlias(){}
    public ExamQueryAlias(String code,Long examId){this.code=code;this.examId=examId;}
    public String getId(){return code;}
    // Aliases are insert-only: never merge an existing code into another exam.
    @Transient public boolean isNew(){return true;}
}
