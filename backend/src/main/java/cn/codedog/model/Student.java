package cn.codedog.model;

import jakarta.persistence.*;

@Entity
@Table(name = "students", indexes = @Index(name = "idx_students_name", columnList = "name"))
public class Student {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "user_id", nullable = false, unique = true, length = 100)
    private String userId;
    @Column(nullable = false, length = 100)
    private String name;
    @Column(length = 30) private String gender;
    @Column(length = 30) private String age;
    @Column(length = 100) private String grade;
    @Column(name = "class_name", length = 100) private String className;

    public Long getId() { return id; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getGender() { return gender; }
    public void setGender(String gender) { this.gender = gender; }
    public String getAge() { return age; }
    public void setAge(String age) { this.age = age; }
    public String getGrade() { return grade; }
    public void setGrade(String grade) { this.grade = grade; }
    public String getClassName() { return className; }
    public void setClassName(String className) { this.className = className; }
}
