package cn.codedog.config;

import cn.codedog.model.User;
import cn.codedog.repository.UserRepository;
import cn.codedog.security.PermissionCatalog;
import cn.codedog.security.PermissionService;
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
                .requestMatchers("/api/admin/**").access(admin(permissions))
                .requestMatchers(HttpMethod.GET, "/api/dashboard").access(permission(permissions, PermissionCatalog.DASHBOARD_VIEW))
                .requestMatchers(HttpMethod.POST, "/api/students/query").access(permission(permissions, PermissionCatalog.STUDENTS_QUERY))
                .requestMatchers(HttpMethod.POST, "/api/class-progress/import").access(permission(permissions, PermissionCatalog.CLASS_PROGRESS_IMPORT))
                .requestMatchers(HttpMethod.GET, "/api/questionnaire/sso").access(permission(permissions, PermissionCatalog.QUESTIONNAIRE_VIEW))
                .requestMatchers(HttpMethod.GET, "/api/logs").access(permission(permissions, PermissionCatalog.LOGS_VIEW))
                .requestMatchers(HttpMethod.GET, "/api/documents").access(permission(permissions, PermissionCatalog.DOCUMENTS_VIEW))
                .requestMatchers(HttpMethod.GET, "/api/documents/*").access(permission(permissions, PermissionCatalog.DOCUMENTS_EDIT))
                .requestMatchers(HttpMethod.POST, "/api/documents").access(permission(permissions, PermissionCatalog.DOCUMENTS_CREATE))
                .requestMatchers(HttpMethod.PUT, "/api/documents/*").access(permission(permissions, PermissionCatalog.DOCUMENTS_EDIT))
                .requestMatchers(HttpMethod.PATCH, "/api/documents/*/status").access(permission(permissions, PermissionCatalog.DOCUMENTS_STATUS))
                .anyRequest().authenticated())
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
