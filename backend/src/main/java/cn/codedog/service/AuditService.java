package cn.codedog.service;

import cn.codedog.model.AuditLog;
import cn.codedog.repository.AuditLogRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class AuditService {
    private static final ZoneId CHINA = ZoneId.of("Asia/Shanghai");
    private static final Set<String> RESULTS = Set.of("success", "failed");
    private static final Map<String, String> MODULE_PATTERNS = Map.of(
        "auth", "login_%",
        "account", "password_%",
        "documents", "document_%",
        "students", "student_%"
    );
    private final JdbcTemplate jdbc;
    private final AuditLogRepository repository;

    public AuditService(JdbcTemplate jdbc, AuditLogRepository repository) {
        this.jdbc = jdbc;
        this.repository = repository;
    }

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

    public Page<AuditLog> list(LocalDate startDate, LocalDate endDate, String module,
                               String result, String keyword, int page) {
        String normalizedModule = normalize(module);
        String normalizedResult = normalize(result);
        if (!normalizedModule.isEmpty() && !MODULE_PATTERNS.containsKey(normalizedModule))
            throw new IllegalArgumentException("未知日志模块");
        if (!normalizedResult.isEmpty() && !RESULTS.contains(normalizedResult))
            throw new IllegalArgumentException("未知执行结果");
        if (startDate != null && endDate != null && startDate.isAfter(endDate))
            throw new IllegalArgumentException("开始日期不能晚于结束日期");

        String search = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);
        Specification<AuditLog> specification = (root, query, cb) -> {
            var predicates = new ArrayList<Predicate>();
            if (startDate != null) predicates.add(cb.greaterThanOrEqualTo(root.<Instant>get("createdAt"),
                startDate.atStartOfDay(CHINA).toInstant()));
            if (endDate != null) predicates.add(cb.lessThan(root.<Instant>get("createdAt"),
                endDate.plusDays(1).atStartOfDay(CHINA).toInstant()));
            if (!normalizedModule.isEmpty())
                predicates.add(cb.like(root.get("action"), MODULE_PATTERNS.get(normalizedModule)));
            if (normalizedResult.equals("failed")) predicates.add(cb.equal(root.get("action"), "login_failed"));
            if (normalizedResult.equals("success")) predicates.add(cb.notEqual(root.get("action"), "login_failed"));
            if (!search.isEmpty()) {
                String pattern = "%" + search.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
                predicates.add(cb.or(
                    cb.like(cb.lower(root.get("action")), pattern, '\\'),
                    cb.like(cb.lower(root.get("ipAddress")), pattern, '\\')));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        return repository.findAll(specification, PageRequest.of(Math.max(page, 0), 30,
            Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))));
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
