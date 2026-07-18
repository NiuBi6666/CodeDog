package cn.codedog.controller;

import cn.codedog.model.Document;
import cn.codedog.model.DocumentStatus;
import cn.codedog.repository.DocumentRepository;
import cn.codedog.service.AuditService;
import cn.codedog.service.DocumentService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class DocumentController {
    private final DocumentService service;
    private final DocumentRepository repository;
    private final AuditService audit;

    public DocumentController(DocumentService service, DocumentRepository repository, AuditService audit) {
        this.service = service; this.repository = repository; this.audit = audit;
    }

    @GetMapping("/public/documents/latest")
    public ResponseEntity<DocumentDto> latest() {
        Document document = service.latestPublished();
        return document == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(DocumentDto.from(document, true));
    }

    @GetMapping("/public/documents/{id}")
    public ResponseEntity<DocumentDto> publicDocument(@PathVariable String id) {
        Document document = service.require(id);
        HttpStatus status = document.getStatus() == DocumentStatus.OFFLINE ? HttpStatus.GONE : HttpStatus.OK;
        return ResponseEntity.status(status).body(DocumentDto.from(document, true));
    }

    @GetMapping("/documents")
    public PageResponse documents(
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate createdDate,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate updatedDate,
        @RequestParam(required = false) String status,
        @RequestParam(defaultValue = "0") int page) {
        DocumentStatus parsed = status == null || status.isBlank() ? null : DocumentStatus.valueOf(status.toUpperCase(Locale.ROOT));
        Page<Document> result = service.list(createdDate, updatedDate, parsed, page);
        return new PageResponse(result.getContent().stream().map(document -> DocumentDto.from(document, false)).toList(),
            result.getTotalElements(), result.getNumber(), result.getTotalPages());
    }

    @GetMapping("/documents/{id}")
    public DocumentDto document(@PathVariable String id) { return DocumentDto.from(service.require(id), true); }

    @PostMapping("/documents")
    @ResponseStatus(HttpStatus.CREATED)
    public DocumentDto create(@Valid @RequestBody SaveRequest body, HttpServletRequest request) {
        Document document = service.create(body.title(), body.content());
        audit.record("document_created:" + document.getPublicId(), request);
        return DocumentDto.from(document, true);
    }

    @PutMapping("/documents/{id}")
    public DocumentDto update(@PathVariable String id, @Valid @RequestBody SaveRequest body, HttpServletRequest request) {
        Document document = service.update(id, body.version(), body.title(), body.content());
        audit.record("document_updated:" + id, request);
        return DocumentDto.from(document, true);
    }

    @PatchMapping("/documents/{id}/status")
    public DocumentDto status(@PathVariable String id, @Valid @RequestBody StatusRequest body, HttpServletRequest request) {
        DocumentStatus status = DocumentStatus.valueOf(body.status().toUpperCase(Locale.ROOT));
        Document document = service.changeStatus(id, status);
        audit.record("document_" + body.status().toLowerCase() + ":" + id, request);
        return DocumentDto.from(document, false);
    }

    public record SaveRequest(@NotBlank String title, @NotNull String content, @NotNull Long version) {}
    public record StatusRequest(@NotBlank String status) {}
    public record PageResponse(List<DocumentDto> documents, long total, int page, int pageCount) {}
}
