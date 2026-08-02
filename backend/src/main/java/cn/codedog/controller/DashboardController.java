package cn.codedog.controller;

import cn.codedog.model.DocumentStatus;
import cn.codedog.repository.DocumentRepository;
import cn.codedog.repository.StudentRepository;
import cn.codedog.security.PermissionCatalog;
import cn.codedog.security.PermissionService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {
    private final DocumentRepository documents;
    private final StudentRepository students;
    private final PermissionService permissions;

    public DashboardController(DocumentRepository documents, StudentRepository students,
                               PermissionService permissions) {
        this.documents = documents;
        this.students = students;
        this.permissions = permissions;
    }

    @GetMapping
    public Dashboard dashboard(Authentication authentication) {
        var granted = permissions.permissions(authentication);
        boolean documentStats = granted.contains(PermissionCatalog.DASHBOARD_DOCUMENT_STATS);
        boolean studentStats = granted.contains(PermissionCatalog.DASHBOARD_STUDENT_STATS);
        boolean latestDocument = granted.contains(PermissionCatalog.DASHBOARD_LATEST_DOCUMENT);
        return new Dashboard(
            documentStats ? documents.count() : null,
            documentStats ? documents.countByStatus(DocumentStatus.NORMAL) : null,
            documentStats ? documents.countByStatus(DocumentStatus.OFFLINE) : null,
            studentStats ? students.count() : null,
            latestDocument ? documents.findFirstByStatusOrderByCreatedAtDescIdDesc(DocumentStatus.NORMAL)
                .map(document -> DocumentDto.from(document, false)).orElse(null) : null);
    }

    public record Dashboard(Long documentTotal, Long documentNormal, Long documentOffline,
                            Long studentCount, DocumentDto latestDocument) {}
}
