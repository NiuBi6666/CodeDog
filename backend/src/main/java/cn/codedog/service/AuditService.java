package cn.codedog.service;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
public class AuditService {
    private final JdbcTemplate jdbc;

    public AuditService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public void record(String action, HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        String ip = forwarded == null || forwarded.isBlank()
            ? request.getRemoteAddr() : forwarded.split(",", 2)[0].trim();
        if (ip.length() > 80) ip = ip.substring(0, 80);
        jdbc.update("INSERT INTO audit_log(action, ip_address, created_at) VALUES (?, ?, ?)",
            action, ip, Timestamp.from(Instant.now()));
    }

    public int recentLoginFailures(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        String ip = forwarded == null || forwarded.isBlank()
            ? request.getRemoteAddr() : forwarded.split(",", 2)[0].trim();
        Integer count = jdbc.queryForObject("""
            SELECT COUNT(*) FROM audit_log
            WHERE action = 'login_failed' AND ip_address = ?
              AND created_at >= ?
              AND id > COALESCE((SELECT MAX(id) FROM audit_log
                WHERE action = 'login_succeeded' AND ip_address = ?), 0)
            """, Integer.class, ip, Timestamp.from(Instant.now().minus(15, ChronoUnit.MINUTES)), ip);
        return count == null ? 0 : count;
    }
}
