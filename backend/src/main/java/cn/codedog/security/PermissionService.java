package cn.codedog.security;

import cn.codedog.model.User;
import cn.codedog.repository.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.util.Set;

@Service
public class PermissionService {
    private final UserRepository users;

    public PermissionService(UserRepository users) {
        this.users = users;
    }

    public boolean has(Authentication authentication, String permission) {
        if (authentication == null || !authentication.isAuthenticated() || permission == null) return false;
        return users.findByUsername(authentication.getName())
            .map(user -> user.isAdmin() || user.getPermissions().contains(permission))
            .orElse(false);
    }

    public boolean isAdmin(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) return false;
        return isAdmin(authentication.getName());
    }

    public boolean isAdmin(String username) {
        return username != null && users.findByUsername(username).map(User::isAdmin).orElse(false);
    }

    public Set<String> permissions(User user) {
        return user.isAdmin() ? PermissionCatalog.allCodes() : Set.copyOf(user.getPermissions());
    }

    public Set<String> permissions(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) return Set.of();
        return users.findByUsername(authentication.getName()).map(this::permissions).orElse(Set.of());
    }
}
