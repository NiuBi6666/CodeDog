package cn.codedog.controller;

import cn.codedog.model.User;
import cn.codedog.repository.UserRepository;
import cn.codedog.security.PermissionCatalog;
import cn.codedog.security.PermissionService;
import cn.codedog.service.AuditService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthenticationManager authenticationManager;
    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final AuditService audit;
    private final PermissionService permissions;
    private final HttpSessionSecurityContextRepository contextRepository = new HttpSessionSecurityContextRepository();

    public AuthController(AuthenticationManager authenticationManager, UserRepository users,
                          PasswordEncoder encoder, AuditService audit, PermissionService permissions) {
        this.authenticationManager = authenticationManager;
        this.users = users;
        this.encoder = encoder;
        this.audit = audit;
        this.permissions = permissions;
    }

    @GetMapping("/csrf")
    public Map<String, String> csrf(CsrfToken token) { return Map.of("token", token.getToken()); }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public RegistrationResponse register(@Valid @RequestBody RegistrationRequest body,
                                         HttpServletRequest request) {
        if (audit.recentRegistrations(request) >= 5)
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "注册操作过于频繁，请稍后再试");
        if (!body.password().equals(body.confirmation()))
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "两次输入的密码不一致");

        String username = body.username().trim();
        if (users.existsByUsernameIgnoreCase(username))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "用户名已存在");

        User user = new User();
        user.setUsername(username);
        user.setPasswordHash(encoder.encode(body.password()));
        user.setAdmin(false);
        user.setPermissions(new LinkedHashSet<>(PermissionCatalog.DEFAULT_PERMISSIONS));
        user.setCreatedAt(Instant.now());
        user.setUpdatedAt(Instant.now());
        try {
            users.saveAndFlush(user);
        } catch (DataIntegrityViolationException error) {
            audit.record("registration_failed:" + username, request);
            throw new ResponseStatusException(HttpStatus.CONFLICT, "用户名或教师 ID 已存在");
        }
        audit.record("registration_succeeded:" + username, request);
        return new RegistrationResponse(true, username, user.getTeacherPublicId());
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest body,
                              HttpServletRequest request, HttpServletResponse response) {
        if (audit.recentLoginFailures(request) >= 8)
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "登录尝试过于频繁，请稍后再试");
        try {
            Authentication authentication = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(body.username().trim(), body.password()));
            request.getSession(true);
            request.changeSessionId();
            var context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);
            contextRepository.saveContext(context, request, response);
            audit.record("login_succeeded", request);
            return profile(authentication.getName());
        } catch (AuthenticationException error) {
            audit.record("login_failed", request);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "用户名或密码不正确");
        }
    }

    @GetMapping("/me")
    public AuthResponse me(Principal principal) { return profile(principal.getName()); }

    @PostMapping("/password")
    public Map<String, Boolean> password(@Valid @RequestBody PasswordRequest body, Principal principal,
                                         HttpServletRequest request) {
        User user = users.findByUsername(principal.getName()).orElseThrow();
        if (!encoder.matches(body.currentPassword(), user.getPasswordHash()))
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "当前密码不正确");
        if (!body.newPassword().equals(body.confirmation()))
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "两次输入的新密码不一致");
        user.setPasswordHash(encoder.encode(body.newPassword()));
        user.setUpdatedAt(Instant.now());
        users.save(user);
        audit.record("password_changed", request);
        return Map.of("ok", true);
    }

    private AuthResponse profile(String username) {
        User user = users.findByUsername(username).orElseThrow();
        return new AuthResponse(user.getUsername(), user.getTeacherPublicId(), user.isAdmin(), permissions.permissions(user));
    }

    public record LoginRequest(@NotBlank String username, @NotBlank String password) {}
    public record RegistrationRequest(
        @NotBlank
        @Pattern(regexp = "^[A-Za-z0-9_.-]{3,32}$", message = "用户名需为 3-32 位字母、数字、点、下划线或短横线")
        String username,
        @NotBlank @Size(min = 8, max = 72, message = "密码长度需为 8-72 个字符") String password,
        @NotBlank String confirmation) {}
    public record RegistrationResponse(boolean ok, String username, String teacherId) {}
    public record AuthResponse(String username, String teacherId, boolean admin, Set<String> permissions) {}
    public record PasswordRequest(@NotBlank String currentPassword,
                                  @NotBlank @Size(min = 12, max = 72, message = "新密码长度需为 12-72 个字符") String newPassword,
                                  @NotBlank String confirmation) {}
}
