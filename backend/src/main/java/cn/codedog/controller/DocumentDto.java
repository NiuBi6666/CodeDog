package cn.codedog.controller;

import cn.codedog.model.Document;
import java.time.Instant;

public record DocumentDto(String id, String title, String content, String status, long version,
                          Instant createdAt, Instant updatedAt) {
    static DocumentDto from(Document document, boolean includeContent) {
        return new DocumentDto(document.getPublicId(), document.getTitle(), includeContent ? document.getContent() : null,
            document.getStatus().name().toLowerCase(), document.getVersion(),
            document.getCreatedAt(), document.getUpdatedAt());
    }
}
