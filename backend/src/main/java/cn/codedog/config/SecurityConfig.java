package cn.codedog.config;

import cn.codedog.model.User;
import cn.codedog.dao.UserRepository;
import cn.codedog.security.PermissionCatalog;
import cn.codedog.service.PermissionService;
import cn.codedog.service.AuditContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;

import java.util.Map;

@Configuration
public class SecurityConfig {
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, ObjectMapper objectMapper,
                                            PermissionService permissions) throws Exception {
        CookieCsrfTokenRepository csrf = CookieCsrfTokenRepository.withHttpOnlyFalse();
        csrf.setCookiePath("/");
        http
            .csrf(configurer -> configurer.csrfTokenRepository(csrf)
                .ignoringRequestMatchers("/api/public/rankings/extension/**"))
            .cors(Customizer.withDefaults())
            .authorizeHttpRequests(requests -> requests
                .requestMatchers("/api/public/**", "/api/auth/csrf", "/api/auth/login",
                    "/api/auth/register", "/actuator/health/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/auth/me").authenticated()
                .requestMatchers(HttpMethod.POST, "/api/auth/password", "/api/auth/logout").authenticated()
                .requestMatchers(HttpMethod.GET, "/api/admin/permissions", "/api/admin/users")
                    .access(permission(permissions, PermissionCatalog.USERS_VIEW))
                .requestMatchers(HttpMethod.PUT, "/api/admin/users/*/permissions")
                    .access(permission(permissions, PermissionCatalog.USERS_PERMISSIONS_MANAGE))
                .requestMatchers(HttpMethod.PUT, "/api/admin/users/*/crm-teacher")
                    .access(permission(permissions, PermissionCatalog.USERS_CRM_MANAGE))
                .requestMatchers(HttpMethod.GET, "/api/admin/exams")
                    .access(permission(permissions, PermissionCatalog.EXAMS_READ))
                .requestMatchers(HttpMethod.POST, "/api/admin/exams/inspect")
                    .access(permission(permissions, PermissionCatalog.EXAMS_INSPECT))
                .requestMatchers(HttpMethod.POST, "/api/admin/exams")
                    .access(permission(permissions, PermissionCatalog.EXAMS_CREATE))
                .requestMatchers(HttpMethod.PATCH, "/api/admin/exams/*/link")
                    .access(permission(permissions, PermissionCatalog.EXAMS_LINK_EDIT))
                .requestMatchers(HttpMethod.PATCH, "/api/admin/exams/*/status")
                    .access(permission(permissions, PermissionCatalog.EXAMS_STATUS))
                .requestMatchers("/api/admin/**").denyAll()
                .requestMatchers(HttpMethod.GET, "/api/rankings/admin/students/*/password").access(admin(permissions))
                .requestMatchers(HttpMethod.PUT, "/api/rankings/admin/students/*/password").access(admin(permissions))
                .requestMatchers(HttpMethod.PUT, "/api/rankings/admin/students/*/points")
                    .access(permission(permissions, PermissionCatalog.RANKINGS_POINTS_EDIT))
                .requestMatchers(HttpMethod.GET, "/api/rankings/admin/board")
                    .access(permission(permissions, PermissionCatalog.RANKINGS_BOARD_READ))
                .requestMatchers(HttpMethod.GET, "/api/rankings/admin/announcement", "/api/rankings/admin/announcements")
                    .access(permission(permissions, PermissionCatalog.RANKINGS_ANNOUNCEMENTS_READ))
                .requestMatchers(HttpMethod.POST, "/api/rankings/admin/announcements")
                    .access(permission(permissions, PermissionCatalog.RANKINGS_ANNOUNCEMENTS_CREATE))
                .requestMatchers(HttpMethod.PUT, "/api/rankings/admin/announcement")
                    .access(permission(permissions, PermissionCatalog.RANKINGS_ANNOUNCEMENTS_EDIT))
                .requestMatchers(HttpMethod.PATCH, "/api/rankings/admin/announcements/*")
                    .access(permission(permissions, PermissionCatalog.RANKINGS_ANNOUNCEMENTS_EDIT))
                .requestMatchers(HttpMethod.PATCH, "/api/rankings/admin/announcements/*/status")
                    .access(permission(permissions, PermissionCatalog.RANKINGS_ANNOUNCEMENTS_STATUS))
                .requestMatchers(HttpMethod.GET, "/api/rankings/admin/rewards", "/api/rankings/admin/rewards/*/image")
                    .access(permission(permissions, PermissionCatalog.RANKINGS_REWARDS_READ))
                .requestMatchers(HttpMethod.POST, "/api/rankings/admin/rewards")
                    .access(permission(permissions, PermissionCatalog.RANKINGS_REWARDS_CREATE))
                .requestMatchers(HttpMethod.PUT, "/api/rankings/admin/rewards/*")
                    .access(permission(permissions, PermissionCatalog.RANKINGS_REWARDS_EDIT))
                .requestMatchers(HttpMethod.DELETE, "/api/rankings/admin/rewards/*")
                    .access(permission(permissions, PermissionCatalog.RANKINGS_REWARDS_DELETE))
                .requestMatchers(HttpMethod.GET, "/api/rankings/admin/redemptions")
                    .access(permission(permissions, PermissionCatalog.RANKINGS_REDEMPTIONS_READ))
                .requestMatchers(HttpMethod.POST, "/api/rankings/admin/redemptions")
                    .access(permission(permissions, PermissionCatalog.RANKINGS_REDEMPTIONS_CREATE))
                .requestMatchers(HttpMethod.PATCH, "/api/rankings/admin/redemptions/*/fulfillment")
                    .access(permission(permissions, PermissionCatalog.RANKINGS_REDEMPTIONS_FULFILL))
                .requestMatchers(HttpMethod.POST, "/api/rankings/admin/imports/xlsx")
                    .access(permission(permissions, PermissionCatalog.RANKINGS_IMPORT))
                .requestMatchers(HttpMethod.GET, "/api/rankings/admin/devices")
                    .access(permission(permissions, PermissionCatalog.RANKINGS_DEVICES_READ))
                .requestMatchers(HttpMethod.POST, "/api/rankings/admin/pairing-codes")
                    .access(permission(permissions, PermissionCatalog.RANKINGS_DEVICES_PAIR))
                .requestMatchers(HttpMethod.DELETE, "/api/rankings/admin/devices/*")
                    .access(permission(permissions, PermissionCatalog.RANKINGS_DEVICES_REVOKE))
                .requestMatchers("/api/rankings/admin/**").denyAll()
                .requestMatchers(HttpMethod.GET, "/api/dashboard").access(permission(permissions, PermissionCatalog.DASHBOARD_VIEW))
                .requestMatchers(HttpMethod.POST, "/api/students/query").access(permission(permissions, PermissionCatalog.STUDENTS_QUERY))
                .requestMatchers(HttpMethod.POST, "/api/class-progress/import").access(permission(permissions, PermissionCatalog.CLASS_PROGRESS_IMPORT))
                .requestMatchers(HttpMethod.GET, "/api/questionnaire/sso").access(permission(permissions, PermissionCatalog.QUESTIONNAIRE_VIEW))
                .requestMatchers(HttpMethod.GET, "/api/logs/export")
                    .access(permission(permissions, PermissionCatalog.LOGS_EXPORT))
                .requestMatchers(HttpMethod.GET, "/api/logs", "/api/logs/*")
                    .access(permission(permissions, PermissionCatalog.LOGS_VIEW))
                .requestMatchers(HttpMethod.GET, "/api/documents").access(permission(permissions, PermissionCatalog.DOCUMENTS_VIEW))
                .requestMatchers(HttpMethod.GET, "/api/documents/*").access(permission(permissions, PermissionCatalog.DOCUMENTS_EDIT))
                .requestMatchers(HttpMethod.POST, "/api/documents").access(permission(permissions, PermissionCatalog.DOCUMENTS_CREATE))
                .requestMatchers(HttpMethod.PUT, "/api/documents/*").access(permission(permissions, PermissionCatalog.DOCUMENTS_EDIT))
                .requestMatchers(HttpMethod.PATCH, "/api/documents/*/status").access(permission(permissions, PermissionCatalog.DOCUMENTS_STATUS))
                .anyRequest().denyAll())
            .exceptionHandling(errors -> errors
                .authenticationEntryPoint((request, response, exception) -> writeError(
                    response, objectMapper, HttpServletResponse.SC_UNAUTHORIZED, "需要登录"))
                .accessDeniedHandler((request, response, exception) -> writeError(
                    response, objectMapper, HttpServletResponse.SC_FORBIDDEN,
                    exception instanceof CsrfException ? "安全令牌已失效，请重试" : "无权限执行此操作")))
            .logout(logout -> logout
                .logoutUrl("/api/auth/logout")
                .logoutSuccessHandler((request, response, authentication) -> {
                    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    objectMapper.writeValue(response.getWriter(), Map.of("ok", true));
                })
                .invalidateHttpSession(true)
                .deleteCookies("JSESSIONID"))
            .securityContext(context -> context
                .securityContextRepository(new HttpSessionSecurityContextRepository()));
        return http.build();
    }

    private static AuthorizationManager<RequestAuthorizationContext> permission(
        PermissionService permissions, String code) {
        return (authentication, context) ->
            new AuthorizationDecision(permissions.has(authentication.get(), code));
    }

    private static AuthorizationManager<RequestAuthorizationContext> admin(PermissionService permissions) {
        return (authentication, context) ->
            new AuthorizationDecision(permissions.isAdmin(authentication.get()));
    }

    private static void writeError(HttpServletResponse response, ObjectMapper objectMapper,
                                   int status, String message) throws java.io.IOException {
        AuditContext.error(message);
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), Map.of("error", message));
    }

    @Bean
    UserDetailsService userDetailsService(UserRepository repository) {
        return username -> {
            User user = repository.findByUsername(username)
                .orElseThrow(() -> new org.springframework.security.core.userdetails.UsernameNotFoundException(username));
            var builder = org.springframework.security.core.userdetails.User
                .withUsername(user.getUsername())
                .password(user.getPasswordHash());
            return (user.isAdmin() ? builder.roles("ADMIN") : builder.roles("USER")).build();
        };
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }
}
