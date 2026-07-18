package cn.codedog.controller;

import cn.codedog.model.User;
import cn.codedog.repository.UserRepository;
import cn.codedog.service.AuditService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
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
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthenticationManager authenticationManager;
    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final AuditService audit;
    private final HttpSessionSecurityContextRepository contextRepository = new HttpSessionSecurityContextRepository();

    public AuthController(AuthenticationManager authenticationManager, UserRepository users,
                          PasswordEncoder encoder, AuditService audit) {
        this.authenticationManager = authenticationManager; this.users = users;
        this.encoder = encoder; this.audit = audit;
    }

    @GetMapping("/csrf")
    public Map<String, String> csrf(CsrfToken token) { return Map.of("token", token.getToken()); }

    @PostMapping("/login")
    public Map<String, Object> login(@Valid @RequestBody LoginRequest body,
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
            return Map.of("username", authentication.getName());
        } catch (AuthenticationException error) {
            audit.record("login_failed", request);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "用户名或密码不正确");
        }
    }

    @GetMapping("/me")
    public Map<String, String> me(Principal principal) { return Map.of("username", principal.getName()); }

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

    public record LoginRequest(@NotBlank String username, @NotBlank String password) {}
    public record PasswordRequest(@NotBlank String currentPassword,
                                  @NotBlank @Size(min = 12, message = "新密码至少需要 12 个字符") String newPassword,
                                  @NotBlank String confirmation) {}
}
