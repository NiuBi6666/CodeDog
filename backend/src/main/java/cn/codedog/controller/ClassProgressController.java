package cn.codedog.controller;

import cn.codedog.service.AuditService;
import cn.codedog.service.ClassProgressService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/class-progress")
public class ClassProgressController {
    private final ClassProgressService service;
    private final AuditService audit;

    public ClassProgressController(ClassProgressService service, AuditService audit) {
        this.service = service;
        this.audit = audit;
    }

    @GetMapping("/bootstrap")
    public ClassProgressService.Bootstrap bootstrap() { return service.bootstrap(); }

    @GetMapping("/camps/{campId}/classes")
    public List<ClassProgressService.LiveClass> classes(@PathVariable long campId) {
        return service.classes(campId);
    }

    @GetMapping("/classes/{classId}/lessons")
    public List<ClassProgressService.Lesson> lessons(@PathVariable long classId) {
        return service.lessons(classId);
    }

    @GetMapping("/classes/{classId}/lessons/{lessonId}/report")
    public ClassProgressService.Report report(@PathVariable long classId, @PathVariable long lessonId,
                                              HttpServletRequest request) {
        ClassProgressService.Report result = service.report(lessonId);
        audit.record("class_progress:" + classId + ":" + lessonId + ":" + result.summary().questionCount(), request);
        return result;
    }
}
