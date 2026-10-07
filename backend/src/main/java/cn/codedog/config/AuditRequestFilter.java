package cn.codedog.config;

import cn.codedog.service.AuditContext;
import cn.codedog.service.StructuredAuditService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class AuditRequestFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(AuditRequestFilter.class);
    private static final String SSE_PATH = "/api/public/rankings/announcement/events";
    private final StructuredAuditService audit;
    public AuditRequestFilter(StructuredAuditService audit) { this.audit=audit; }

    @Override protected boolean shouldNotFilter(HttpServletRequest request) {
        String path=request.getRequestURI();
        return !path.startsWith("/api/") || SSE_PATH.equals(path);
    }

    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                               FilterChain chain) throws ServletException, IOException {
        long started=System.nanoTime();
        AuditContext.State state=AuditContext.begin(request.getMethod(),request.getRequestURI());
        response.setHeader("X-Request-ID",state.requestId());
        try {
            chain.doFilter(request,response);
        } catch (RuntimeException error) {
            AuditContext.error(error.getClass().getSimpleName());
            throw error;
        } finally {
            try {
                audit.finish(request,response,(System.nanoTime()-started)/1_000_000L);
            } catch (RuntimeException writeFailure) {
                log.error("Structured audit write failed for request {}",state.requestId(),writeFailure);
            } finally {
                AuditContext.clear();
            }
        }
    }
}
