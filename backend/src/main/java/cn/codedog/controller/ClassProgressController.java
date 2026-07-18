package cn.codedog.controller;

import cn.codedog.service.AuditService;
import cn.codedog.service.ClassProgressService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.function.Function;

@RestController
@RequestMapping("/api/class-progress")
public class ClassProgressController {
    private static final String SESSION_COOKIE = ClassProgressController.class.getName() + ".cookie";
    private final ClassProgressService service;
    private final AuditService audit;

    public ClassProgressController(ClassProgressService service, AuditService audit) {
        this.service = service;
        this.audit = audit;
    }

    @GetMapping("/bootstrap")
    public ClassProgressService.Bootstrap bootstrap(HttpServletRequest request) {
        return withCredential(request, service::bootstrap);
    }

    @PostMapping("/credential")
    public ClassProgressService.Bootstrap credential(@Valid @RequestBody CookieCredential body,
                                                      HttpServletRequest request) {
        try {
            ClassProgressService.Bootstrap result = service.bootstrap(body.cookie());
            request.getSession().setAttribute(SESSION_COOKIE, body.cookie().trim());
            return result;
        } catch (ClassProgressService.UpstreamAuthenticationException error) {
            clearCredential(request);
            throw error;
        }
    }

    @GetMapping("/camps/{campId}/classes")
    public List<ClassProgressService.LiveClass> classes(@PathVariable long campId, HttpServletRequest request) {
        return withCredential(request, cookie -> service.classes(campId, cookie));
    }

    @GetMapping("/classes/{classId}/lessons")
    public List<ClassProgressService.Lesson> lessons(@PathVariable long classId, HttpServletRequest request) {
        return withCredential(request, cookie -> service.lessons(classId, cookie));
    }

    @GetMapping("/classes/{classId}/lessons/{lessonId}/report")
    public ClassProgressService.Report report(@PathVariable long classId, @PathVariable long lessonId,
                                              HttpServletRequest request) {
        ClassProgressService.Report result = withCredential(request, cookie -> service.report(lessonId, cookie));
        audit.record("class_progress:" + classId + ":" + lessonId + ":" + result.summary().questionCount(), request);
        return result;
    }

    private <T> T withCredential(HttpServletRequest request, Function<String, T> action) {
        try {
            return action.apply(sessionCookie(request));
        } catch (ClassProgressService.UpstreamAuthenticationException error) {
            clearCredential(request);
            throw error;
        }
    }

    private String sessionCookie(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        Object value = session == null ? null : session.getAttribute(SESSION_COOKIE);
        return value instanceof String cookie ? cookie : null;
    }

    private void clearCredential(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) session.removeAttribute(SESSION_COOKIE);
    }

    public record CookieCredential(@NotBlank @Size(max = 16384) String cookie) {}
}
