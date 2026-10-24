package cn.codedog.config;

import cn.codedog.dao.UserRepository;
import cn.codedog.service.AuditContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.Map;

/** Rechecks account state and login generation for every authenticated teacher request. */
public class TeacherAccountFilter extends OncePerRequestFilter {
    public static final String VERSION = "CODEDOG_TEACHER_ACCESS_VERSION";
    private final UserRepository users;
    private final ObjectMapper json;
    public TeacherAccountFilter(UserRepository users, ObjectMapper json){this.users=users;this.json=json;}
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                              FilterChain chain) throws ServletException, IOException {
        var auth=SecurityContextHolder.getContext().getAuthentication();
        if(auth!=null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken)){
            var user=users.findByUsername(auth.getName()).orElse(null);
            var session=request.getSession(false);
            Object saved=session==null?null:session.getAttribute(VERSION);
            long version=saved instanceof Number number?number.longValue():0L;
            if(user==null || !user.isEnabled() || user.getAccessVersion()!=version){
                if(AuditContext.current()!=null){
                    AuditContext.current().actor("TEACHER",auth.getName(),auth.getName());
                    AuditContext.current().owner(auth.getName());
                }
                AuditContext.error("账号已停用或登录已失效");
                if(session!=null)session.invalidate();
                SecurityContextHolder.clearContext();
                response.setStatus(401);response.setContentType("application/json");response.setCharacterEncoding("UTF-8");
                json.writeValue(response.getWriter(),Map.of("error","账号已停用或登录已失效，请重新登录"));
                return;
            }
        }
        chain.doFilter(request,response);
    }
}
