package cn.codedog.controller;

import cn.codedog.model.DocumentStatus;
import cn.codedog.repository.DocumentRepository;
import cn.codedog.repository.StudentRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {
    private final DocumentRepository documents;
    private final StudentRepository students;

    public DashboardController(DocumentRepository documents, StudentRepository students) {
        this.documents = documents; this.students = students;
    }

    @GetMapping
    public Dashboard dashboard() {
        return new Dashboard(documents.count(), documents.countByStatus(DocumentStatus.NORMAL),
            documents.countByStatus(DocumentStatus.OFFLINE), students.count(),
            documents.findFirstByStatusOrderByCreatedAtDescIdDesc(DocumentStatus.NORMAL)
                .map(document -> DocumentDto.from(document, false)).orElse(null));
    }

    public record Dashboard(long documentTotal, long documentNormal, long documentOffline,
                            long studentCount, DocumentDto latestDocument) {}
}
