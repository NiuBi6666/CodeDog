package cn.codedog.repository;

import cn.codedog.model.Document;
import cn.codedog.model.DocumentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import java.util.Optional;

public interface DocumentRepository extends JpaRepository<Document, Long>, JpaSpecificationExecutor<Document> {
    long countByStatus(DocumentStatus status);
    Optional<Document> findByPublicId(String publicId);
    Optional<Document> findFirstByStatusOrderByCreatedAtDescIdDesc(DocumentStatus status);
}
