package cn.codedog.controller;

import cn.codedog.service.AuditService;
import cn.codedog.service.StudentService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.bind.annotation.RequestParam;

@RestController
@RequestMapping("/api/students")
public class StudentController {
    private final StudentService service;
    private final AuditService audit;
    private final cn.codedog.service.StudentInfoExcelReader reader;

    public StudentController(StudentService service, AuditService audit, cn.codedog.service.StudentInfoExcelReader reader) { this.service = service; this.audit = audit; this.reader = reader; }

    @PostMapping("/query")
    public StudentService.QueryResult query(@Valid @RequestBody QueryRequest body, HttpServletRequest request) {
        StudentService.QueryResult result = service.query(body.mode(), body.values());
        audit.record("student_query:" + body.mode() + ":" + result.summary().total(), request);
        return result;
    }

    @PostMapping(value = "/import", consumes = "multipart/form-data")
    public StudentService.ImportResult importStudents(@RequestParam("file") MultipartFile file, HttpServletRequest request) {
        StudentService.ImportResult result = service.importStudents(file, reader);
        audit.fileMetadata(file);
        audit.detail(java.util.Map.of("total", result.total(), "created", result.created(), "updated", result.updated()));
        audit.record("student_import", request);
        return result;
    }

    public record QueryRequest(@NotBlank String mode, @NotNull List<Object> values) {}
}
