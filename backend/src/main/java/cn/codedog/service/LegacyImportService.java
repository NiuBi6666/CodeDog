package cn.codedog.service;

import cn.codedog.model.DocumentIds;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.time.*;

@Component
@Order(1)
public class LegacyImportService implements ApplicationRunner {
    private final JdbcTemplate mysql;
    private final boolean enabled;
    private final Path publicDocDb;
    private final Path codeMaoDb;

    public LegacyImportService(JdbcTemplate mysql,
        @Value("${codedog.legacy-import-enabled:false}") boolean enabled,
        @Value("${codedog.legacy-public-doc-db}") Path publicDocDb,
        @Value("${codedog.legacy-codemao-db}") Path codeMaoDb) {
        this.mysql = mysql; this.enabled = enabled; this.publicDocDb = publicDocDb; this.codeMaoDb = codeMaoDb;
    }

    @Override public void run(ApplicationArguments args) throws Exception {
        if (!enabled) return;
        if (count("documents") == 0 && Files.isRegularFile(publicDocDb)) importDocuments();
        if (count("students") == 0 && Files.isRegularFile(codeMaoDb)) importStudents();
    }

    private long count(String table) { return mysql.queryForObject("SELECT COUNT(*) FROM " + table, Long.class); }

    private void importDocuments() throws SQLException {
        try (Connection source = DriverManager.getConnection(sqliteReadOnlyUrl(publicDocDb));
             Statement statement = source.createStatement();
             ResultSet rows = statement.executeQuery("SELECT id,title,content,status,version,created_at,updated_at FROM documents ORDER BY id")) {
            while (rows.next()) mysql.update("""
                INSERT INTO documents(id,public_id,title,content,status,version,created_at,updated_at)
                VALUES (?,?,?,?,?,?,?,?)
                """, rows.getLong("id"), uniqueDocumentId(), rows.getString("title"), rows.getString("content"),
                rows.getString("status").toUpperCase(), rows.getLong("version"),
                Timestamp.from(parseUtc(rows.getString("created_at"))), Timestamp.from(parseUtc(rows.getString("updated_at"))));
        }
    }

    private void importStudents() throws SQLException {
        try (Connection source = DriverManager.getConnection(sqliteReadOnlyUrl(codeMaoDb));
             Statement statement = source.createStatement();
             ResultSet rows = statement.executeQuery("SELECT user_id,name,gender,age,grade,class_name FROM students ORDER BY id")) {
            while (rows.next()) mysql.update("""
                INSERT INTO students(user_id,name,gender,age,grade,class_name) VALUES (?,?,?,?,?,?)
                """, rows.getString("user_id"), rows.getString("name"), rows.getString("gender"),
                rows.getString("age"), rows.getString("grade"), rows.getString("class_name"));
        }
    }

    private Instant parseUtc(String value) {
        return Timestamp.valueOf(value).toInstant();
    }

    private String uniqueDocumentId() {
        String id;
        do id = DocumentIds.newId();
        while (mysql.queryForObject("SELECT COUNT(*) FROM documents WHERE public_id = ?", Long.class, id) > 0);
        return id;
    }

    private String sqliteReadOnlyUrl(Path path) {
        return "jdbc:sqlite:file:" + path.toAbsolutePath() + "?mode=ro&immutable=1";
    }
}
