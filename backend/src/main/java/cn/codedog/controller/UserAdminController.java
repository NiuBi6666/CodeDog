package cn.codedog.controller;

import cn.codedog.model.User;
import cn.codedog.repository.UserRepository;
import cn.codedog.security.PermissionCatalog;
import cn.codedog.service.AuditService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin")
public class UserAdminController {
    private final UserRepository users;
    private final AuditService audit;
    private final JdbcTemplate jdbc;

    public UserAdminController(UserRepository users, AuditService audit, JdbcTemplate jdbc) {
        this.users = users;
        this.audit = audit;
        this.jdbc = jdbc;
    }

    @GetMapping("/permissions")
    public List<PermissionCatalog.Group> permissions() {
        return PermissionCatalog.groups();
    }

    @GetMapping("/users")
    public List<UserResponse> users() {
        Map<String, String> mappings = jdbc.query(
            "SELECT owner_username,crm_teacher_id FROM ranking_teacher_mappings",
            (rs, row) -> Map.entry(rs.getString(1), rs.getString(2)))
            .stream().collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        return users.findAllByOrderByCreatedAtAscIdAsc().stream()
            .map(user -> UserResponse.from(user, mappings.get(user.getUsername()))).toList();
    }

    @PutMapping("/users/{id}/permissions")
    @Transactional
    public UserResponse updatePermissions(@PathVariable long id,
                                          @Valid @RequestBody PermissionRequest body,
                                          HttpServletRequest request) {
        User user = findUser(id);
        if (user.isAdmin())
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "系统管理员始终拥有全部权限");

        LinkedHashSet<String> requested = new LinkedHashSet<>(body.permissions());
        if (!PermissionCatalog.allCodes().containsAll(requested))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "包含未知权限");
        for (PermissionCatalog.Group group : PermissionCatalog.groups()) {
            String page = group.permissions().stream()
                .filter(permission -> permission.type().equals("page"))
                .map(PermissionCatalog.Permission::code)
                .findFirst().orElse(null);
            boolean hasDependentPermission = group.permissions().stream()
                .anyMatch(permission -> !permission.type().equals("page") && requested.contains(permission.code()));
            if (hasDependentPermission && (page == null || !requested.contains(page)))
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "数据或按钮权限必须同时授予所属页面权限");
        }
        user.setPermissions(requested);
        user.setUpdatedAt(Instant.now());
        User saved = users.save(user);
        audit.record("permissions_updated:" + saved.getUsername() + ":" + requested.size(), request);
        return UserResponse.from(saved, mappingFor(saved.getUsername()));
    }

    @PutMapping("/users/{id}/crm-teacher")
    @Transactional
    public UserResponse updateCrmTeacher(@PathVariable long id,
                                         @RequestBody CrmTeacherRequest body,
                                         HttpServletRequest request) {
        User user = findUser(id);
        String crmTeacherId = normalizeCrmTeacherId(body == null ? null : body.crmTeacherId());
        if (crmTeacherId != null) {
            String existingOwner = jdbc.query(
                "SELECT owner_username FROM ranking_teacher_mappings WHERE crm_teacher_id=?",
                (rs, row) -> rs.getString(1), crmTeacherId).stream().findFirst().orElse(null);
            if (existingOwner != null && !existingOwner.equals(user.getUsername()))
                throw new ResponseStatusException(HttpStatus.CONFLICT, "该 CRM 教师 ID 已绑定其他 CodeDog 用户");
        }

        jdbc.update("DELETE FROM ranking_teacher_mappings WHERE owner_username=?", user.getUsername());
        if (crmTeacherId != null) {
            try {
                jdbc.update("INSERT INTO ranking_teacher_mappings(crm_teacher_id,owner_username) VALUES(?,?)",
                    crmTeacherId, user.getUsername());
            } catch (DataIntegrityViolationException error) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "该 CRM 教师 ID 已绑定其他 CodeDog 用户");
            }
        }
        user.setUpdatedAt(Instant.now());
        User saved = users.save(user);
        audit.record((crmTeacherId == null ? "crm_teacher_mapping_removed:" : "crm_teacher_mapping_updated:")
            + saved.getUsername() + (crmTeacherId == null ? "" : ":" + crmTeacherId), request);
        return UserResponse.from(saved, crmTeacherId);
    }

    private User findUser(long id) {
        return users.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "用户不存在"));
    }

    private String mappingFor(String username) {
        return jdbc.query("SELECT crm_teacher_id FROM ranking_teacher_mappings WHERE owner_username=?",
            (rs, row) -> rs.getString(1), username).stream().findFirst().orElse(null);
    }

    private String normalizeCrmTeacherId(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) return null;
        if (!normalized.matches("^[A-Za-z0-9_-]{1,100}$"))
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                "CRM 教师 ID 只能包含字母、数字、下划线和短横线");
        return normalized;
    }

    public record PermissionRequest(@NotNull Set<String> permissions) {}
    public record CrmTeacherRequest(String crmTeacherId) {}
    public record UserResponse(long id, String username, String teacherId, String crmTeacherId,
                               boolean admin, Set<String> permissions, Instant createdAt, Instant updatedAt) {
        static UserResponse from(User user, String crmTeacherId) {
            return new UserResponse(user.getId(), user.getUsername(), user.getTeacherPublicId(), crmTeacherId,
                user.isAdmin(), user.isAdmin() ? PermissionCatalog.allCodes() : Set.copyOf(user.getPermissions()),
                user.getCreatedAt(), user.getUpdatedAt());
        }
    }
}
