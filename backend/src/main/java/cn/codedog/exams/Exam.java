package cn.codedog.exams;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name="exam_sessions")
public class Exam {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) Long id;
    @Column(name="public_id", nullable=false, unique=true, length=32)
    String publicId=UUID.randomUUID().toString().replace("-","");
    @Column(nullable=false,length=120) String title;
    @Column(name="score_labels",nullable=false,columnDefinition="TEXT") String scoreLabels;
    @Column(name="student_count",nullable=false) int studentCount;
    @Column(nullable=false) boolean enabled=true;
    @Column(name="created_by",nullable=false,length=50) String createdBy;
    @Column(name="created_at",nullable=false) Instant createdAt=Instant.now();
}
