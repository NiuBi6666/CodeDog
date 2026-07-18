package cn.codedog.controller;

import cn.codedog.service.AuditService;
import cn.codedog.service.StudentService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/students")
public class StudentController {
    private final StudentService service;
    private final AuditService audit;

    public StudentController(StudentService service, AuditService audit) { this.service = service; this.audit = audit; }

    @PostMapping("/query")
    public StudentService.QueryResult query(@Valid @RequestBody QueryRequest body, HttpServletRequest request) {
        StudentService.QueryResult result = service.query(body.mode(), body.values());
        audit.record("student_query:" + body.mode() + ":" + result.summary().total(), request);
        return result;
    }

    public record QueryRequest(@NotBlank String mode, @NotNull List<Object> values) {}
}
