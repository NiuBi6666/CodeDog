package cn.codedog.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "documents")
public class Document {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "public_id", nullable = false, unique = true, length = 8, updatable = false)
    private String publicId;
    @Column(nullable = false, length = 200)
    private String title;
    @Lob @Column(nullable = false, columnDefinition = "MEDIUMTEXT")
    private String content;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16)
    private DocumentStatus status = DocumentStatus.NORMAL;
    @Version @Column(nullable = false)
    private Long version = 1L;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @PrePersist void assignPublicId() {
        if (publicId == null || publicId.isBlank()) publicId = DocumentIds.newId();
    }
    @PreUpdate void touch() { updatedAt = Instant.now(); }
    public Long getId() { return id; }
    public String getPublicId() { return publicId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public DocumentStatus getStatus() { return status; }
    public void setStatus(DocumentStatus status) { this.status = status; }
    public Long getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
