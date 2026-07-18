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
    @Column(name = "ip_address", nullable = false, length = 80)
    private String ipAddress;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public Long getId() { return id; }
    public String getAction() { return action; }
    public String getIpAddress() { return ipAddress; }
    public Instant getCreatedAt() { return createdAt; }
}
