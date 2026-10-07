package cn.codedog.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "audit_log", indexes = {
    @Index(name = "idx_audit_action_ip_created", columnList = "action,ip_address,created_at"),
    @Index(name = "idx_audit_created", columnList = "created_at")
})
public class AuditLog {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 255)
    private String action;
    @Column(name="owner_username", length=50) private String ownerUsername;
    @Column(name="actor_type", length=24) private String actorType;
    @Column(name="actor_id", length=100) private String actorId;
    @Column(name="actor_name", length=100) private String actorName;
    @Column(name="request_id", nullable=false, length=64) private String requestId;
    @Column(name="http_method", length=12) private String httpMethod;
    @Column(name="request_path", length=500) private String requestPath;
    @Column(name="query_json", columnDefinition="LONGTEXT") private String queryJson;
    @Column(name="status_code") private Integer statusCode;
    @Column(name="duration_ms") private Long durationMs;
    @Column(length=50) private String module;
    @Column(name="event_type", length=30) private String eventType;
    @Column(name="event_code", length=120) private String eventCode;
    @Column(length=20) private String result;
    @Column(name="target_type", length=50) private String targetType;
    @Column(name="target_id", length=120) private String targetId;
    @Column(name="changes_json", columnDefinition="LONGTEXT") private String changesJson;
    @Column(name="detail_json", columnDefinition="LONGTEXT") private String detailJson;
    @Column(name="error_message", columnDefinition="TEXT") private String errorMessage;
    @Column(name="user_agent", length=500) private String userAgent;
    @Column(name = "ip_address", nullable = false, length = 80)
    private String ipAddress;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public Long getId() { return id; }
    public String getAction() { return action; }
    public String getOwnerUsername() { return ownerUsername; }
    public String getActorType() { return actorType; }
    public String getActorId() { return actorId; }
    public String getActorName() { return actorName; }
    public String getRequestId() { return requestId; }
    public String getHttpMethod() { return httpMethod; }
    public String getRequestPath() { return requestPath; }
    public String getQueryJson() { return queryJson; }
    public Integer getStatusCode() { return statusCode; }
    public Long getDurationMs() { return durationMs; }
    public String getModule() { return module; }
    public String getEventType() { return eventType; }
    public String getEventCode() { return eventCode; }
    public String getResult() { return result; }
    public String getTargetType() { return targetType; }
    public String getTargetId() { return targetId; }
    public String getChangesJson() { return changesJson; }
    public String getDetailJson() { return detailJson; }
    public String getErrorMessage() { return errorMessage; }
    public String getUserAgent() { return userAgent; }
    public String getIpAddress() { return ipAddress; }
    public Instant getCreatedAt() { return createdAt; }
}
