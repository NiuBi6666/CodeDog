package cn.codedog.controller;

import cn.codedog.model.User;
import cn.codedog.repository.UserRepository;
import cn.codedog.security.PermissionCatalog;
import cn.codedog.service.AuditService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/admin")
public class UserAdminController {
    private final UserRepository users;
    private final AuditService audit;

    public UserAdminController(UserRepository users, AuditService audit) {
        this.users = users;
        this.audit = audit;
    }

    @GetMapping("/permissions")
    public List<PermissionCatalog.Group> permissions() {
        return PermissionCatalog.groups();
    }

    @GetMapping("/users")
    public List<UserResponse> users() {
        return users.findAllByOrderByCreatedAtAscIdAsc().stream().map(UserResponse::from).toList();
    }

    @PutMapping("/users/{id}/permissions")
    @Transactional
    public UserResponse updatePermissions(@PathVariable long id,
                                          @Valid @RequestBody PermissionRequest body,
                                          HttpServletRequest request) {
        User user = users.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "用户不存在"));
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
        return UserResponse.from(saved);
    }

    public record PermissionRequest(@NotNull Set<String> permissions) {}
    public record UserResponse(long id, String username, boolean admin, Set<String> permissions,
                               Instant createdAt, Instant updatedAt) {
        static UserResponse from(User user) {
            return new UserResponse(user.getId(), user.getUsername(), user.isAdmin(),
                user.isAdmin() ? PermissionCatalog.allCodes() : Set.copyOf(user.getPermissions()),
                user.getCreatedAt(), user.getUpdatedAt());
        }
    }
}
