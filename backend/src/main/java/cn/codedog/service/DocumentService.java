package cn.codedog.service;

import cn.codedog.model.Document;
import cn.codedog.model.DocumentStatus;
import cn.codedog.repository.DocumentRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.ArrayList;
import java.util.Locale;

@Service
public class DocumentService {
    private static final int MAX_CONTENT_BYTES = 10 * 1024 * 1024;
    private static final ZoneId CHINA = ZoneId.of("Asia/Shanghai");
    private final DocumentRepository repository;
    private final HtmlSanitizer sanitizer;

    public DocumentService(DocumentRepository repository, HtmlSanitizer sanitizer) {
        this.repository = repository;
        this.sanitizer = sanitizer;
    }

    public Page<Document> list(LocalDate createdDate, LocalDate updatedDate, DocumentStatus status, int page) {
        Specification<Document> specification = (root, query, cb) -> {
            var predicates = new ArrayList<Predicate>();
            if (createdDate != null) {
                Instant start = createdDate.atStartOfDay(CHINA).toInstant();
                predicates.add(cb.greaterThanOrEqualTo(root.<Instant>get("createdAt"), start));
                predicates.add(cb.lessThan(root.<Instant>get("createdAt"), start.plus(1, java.time.temporal.ChronoUnit.DAYS)));
            }
            if (updatedDate != null) {
                Instant start = updatedDate.atStartOfDay(CHINA).toInstant();
                predicates.add(cb.greaterThanOrEqualTo(root.<Instant>get("updatedAt"), start));
                predicates.add(cb.lessThan(root.<Instant>get("updatedAt"), start.plus(1, java.time.temporal.ChronoUnit.DAYS)));
            }
            if (status != null) predicates.add(cb.equal(root.get("status"), status));
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        return repository.findAll(specification, PageRequest.of(Math.max(page, 0), 20,
            Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))));
    }

    public Document require(String identifier) {
        String normalized = identifier == null ? "" : identifier.trim().toLowerCase(Locale.ROOT);
        var document = repository.findByPublicId(normalized);
        if (document.isPresent()) return document.get();
        if (normalized.matches("[0-9]+")) {
            try {
                return repository.findById(Long.parseLong(normalized))
                    .orElseThrow(() -> new NotFoundException("文档不存在"));
            } catch (NumberFormatException ignored) {
                // Fall through to the same not-found response used for unknown public IDs.
            }
        }
        throw new NotFoundException("文档不存在");
    }

    public Document latestPublished() {
        return repository.findFirstByStatusOrderByCreatedAtDescIdDesc(DocumentStatus.NORMAL).orElse(null);
    }

    @Transactional
    public Document create(String title, String content) {
        validate(title, content);
        Document document = new Document();
        document.setTitle(title.trim());
        document.setContent(sanitizer.clean(content));
        return repository.saveAndFlush(document);
    }

    @Transactional
    public Document update(String id, long expectedVersion, String title, String content) {
        validate(title, content);
        Document document = require(id);
        if (!document.getVersion().equals(expectedVersion)) throw new VersionConflictException(document);
        document.setTitle(title.trim());
        document.setContent(sanitizer.clean(content));
        return repository.saveAndFlush(document);
    }

    @Transactional
    public Document changeStatus(String id, DocumentStatus status) {
        Document document = require(id);
        document.setStatus(status);
        return repository.saveAndFlush(document);
    }

    private void validate(String title, String content) {
        String normalizedTitle = title == null ? "" : title.trim();
        if (normalizedTitle.isEmpty() || normalizedTitle.length() > 200)
            throw new ValidationException("标题不能为空，且不能超过 200 个字符");
        if ((content == null ? "" : content).getBytes(StandardCharsets.UTF_8).length > MAX_CONTENT_BYTES)
            throw new ValidationException("文档内容不能超过 10 MB");
    }

    public static class NotFoundException extends RuntimeException { public NotFoundException(String message) { super(message); } }
    public static class ValidationException extends RuntimeException { public ValidationException(String message) { super(message); } }
    public static class VersionConflictException extends RuntimeException {
        private final Document current;
        public VersionConflictException(Document current) { super("文档已在其他页面更新"); this.current = current; }
        public Document getCurrent() { return current; }
    }
}
