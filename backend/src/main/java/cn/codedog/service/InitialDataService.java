package cn.codedog.service;

import cn.codedog.model.Document;
import cn.codedog.model.User;
import cn.codedog.repository.DocumentRepository;
import cn.codedog.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@Order(2)
public class InitialDataService implements ApplicationRunner {
    private final UserRepository users;
    private final DocumentRepository documents;
    private final PasswordEncoder encoder;
    private final String username;
    private final String password;

    public InitialDataService(UserRepository users, DocumentRepository documents, PasswordEncoder encoder,
        @Value("${codedog.admin-username}") String username,
        @Value("${codedog.admin-password}") String password) {
        this.users = users; this.documents = documents; this.encoder = encoder;
        this.username = username; this.password = password;
    }

    @Override public void run(ApplicationArguments args) {
        if (users.count() == 0) {
            if (password == null || password.isBlank()) throw new IllegalStateException("ADMIN_PASSWORD is required");
            User user = new User(); user.setUsername(username); user.setPasswordHash(encoder.encode(password)); users.save(user);
        }
        if (documents.count() == 0) {
            Document document = new Document(); document.setTitle("新的公开文档");
            document.setContent("<p>内容将在这里发布。</p>"); documents.save(document);
        }
    }
}
