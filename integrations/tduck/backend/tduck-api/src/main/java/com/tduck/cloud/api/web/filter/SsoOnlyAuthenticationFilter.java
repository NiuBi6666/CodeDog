package com.tduck.cloud.api.web.filter;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class SsoOnlyAuthenticationFilter extends OncePerRequestFilter {
    private static final List<String> BLOCKED_PREFIXES = Arrays.asList(
            "/login/account",
            "/login/wx/",
            "/login/qq",
            "/register/",
            "/retrieve/password/",
            "/user/update/password",
            "/user/update/email",
            "/user/update-email/",
            "/user/bind/",
            "/mange/user"
    );

    @Value("${codedog.sso-only:true}")
    private boolean ssoOnly;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (!ssoOnly || !isBlocked(request.getRequestURI())) {
            filterChain.doFilter(request, response);
            return;
        }

        byte[] body = "{\"code\":403,\"msg\":\"Please sign in through CodeDog\",\"data\":null}"
                .getBytes(StandardCharsets.UTF_8);
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("application/json");
        response.setHeader("Cache-Control", "no-store");
        response.setContentLength(body.length);
        response.getOutputStream().write(body);
    }

    private boolean isBlocked(String path) {
        for (String prefix : BLOCKED_PREFIXES) {
            if (path.equals(prefix) || path.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }
}
