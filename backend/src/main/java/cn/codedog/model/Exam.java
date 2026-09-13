package cn.codedog.model;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name="exam_sessions")
public class Exam {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
    @Column(name="public_id", nullable=false, unique=true, length=32)
    public String publicId=UUID.randomUUID().toString().replace("-","");
    @Column(name="query_code",unique=true,length=8) public String queryCode;
    @Column(nullable=false,length=120) public String title;
    @Column(name="score_labels",nullable=false,columnDefinition="TEXT") public String scoreLabels;
    @Column(name="student_count",nullable=false) public int studentCount;
    @Column(name="result_mode",nullable=false,length=12) public String resultMode="legacy";
    @Column(nullable=false) public boolean enabled=true;
    @Column(name="created_by",nullable=false,length=50) public String createdBy;
    @Column(name="created_at",nullable=false) public Instant createdAt=Instant.now();
}
