package cn.codedog.controller;

import cn.codedog.service.AuditService;
import cn.codedog.service.ClassProgressImportService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/class-progress")
public class ClassProgressController {
    private final ClassProgressImportService importer;
    private final AuditService audit;

    public ClassProgressController(ClassProgressImportService importer, AuditService audit) {
        this.importer = importer;
        this.audit = audit;
    }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ClassProgressImportService.ImportResult importFiles(
        @RequestParam("files") List<MultipartFile> files, HttpServletRequest request) {
        ClassProgressImportService.ImportResult result = importer.parse(files);
        audit.record("class_progress_import:" + result.fileCount() + ":" + result.rowCount(), request);
        return result;
    }
}
