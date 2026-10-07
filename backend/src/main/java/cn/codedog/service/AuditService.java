package cn.codedog.service;
import cn.codedog.model.*;
import cn.codedog.dao.*;
import cn.codedog.service.RankingScore;

import cn.codedog.model.AuditLog;
import cn.codedog.dao.AuditLogRepository;
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
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class AuditService {
    private static final ZoneId CHINA = ZoneId.of("Asia/Shanghai");
    private static final Set<String> RESULTS = Set.of("success", "failed");
    private static final Map<String, List<String>> MODULE_PATTERNS = Map.of(
        "auth", List.of("login_%", "student_login_%", "registration_%"),
        "account", List.of("password_%", "permissions_%", "crm_teacher_mapping_%"),
        "documents", List.of("document_%"),
        "students", List.of("student_%"),
        "classes", List.of("class_progress_%")
    );
    private final JdbcTemplate jdbc;
    private final AuditLogRepository repository;
    private final StructuredAuditService structured;

    public AuditService(JdbcTemplate jdbc, AuditLogRepository repository, StructuredAuditService structured) {
        this.jdbc = jdbc;
        this.repository = repository;
        this.structured = structured;
    }

    public void record(String action, HttpServletRequest request) {
        structured.record(action, request);
    }

    public int recentLoginFailures(HttpServletRequest request) {
        return structured.recentFailures("login_failed", request, 15, "login_succeeded");
    }

    public int recentStudentLoginFailures(HttpServletRequest request) {
        return structured.recentFailures("student_login_failed", request, 15, "student_login_succeeded");
    }

    public int recentRegistrations(HttpServletRequest request) {
        return structured.recentRegistrations(request);
    }
    public void event(String module,String type,String code) { structured.event(module,type,code); }
    public void owner(String username) { structured.owner(username); }
    public void actor(String type,String id,String name) { structured.actor(type,id,name); }
    public void target(String type,Object id) { structured.target(type,id); }
    public void detail(Map<String,?> values) { structured.detail(values); }
    public void change(String type,Object id,Map<String,?> before,Map<String,?> after) { structured.change(type,id,before,after); }
    public Map<String,Object> fileMetadata(org.springframework.web.multipart.MultipartFile file) { return structured.fileMetadata(file); }

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
            if (!normalizedModule.isEmpty()) {
                Predicate[] modulePredicates = MODULE_PATTERNS.get(normalizedModule).stream()
                    .map(pattern -> cb.like(root.get("action"), pattern))
                    .toArray(Predicate[]::new);
                predicates.add(cb.or(modulePredicates));
            }
            Predicate failed = cb.or(
                cb.equal(root.get("action"), "login_failed"),
                cb.equal(root.get("action"), "registration_failed"));
            if (normalizedResult.equals("failed")) predicates.add(failed);
            if (normalizedResult.equals("success")) predicates.add(cb.not(failed));
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
