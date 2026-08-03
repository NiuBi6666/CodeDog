package cn.codedog.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@Order(3)
public class RankingTeacherMappingInitializer implements ApplicationRunner {
    private final JdbcTemplate jdbc;
    private final String crmTeacherId;
    private final String ownerUsername;

    public RankingTeacherMappingInitializer(JdbcTemplate jdbc,
        @Value("${codedog.ranking.default-crm-teacher-id:}") String crmTeacherId,
        @Value("${codedog.admin-username}") String ownerUsername) {
        this.jdbc = jdbc;
        this.crmTeacherId = crmTeacherId;
        this.ownerUsername = ownerUsername;
    }

    @Override public void run(ApplicationArguments args) {
        String normalized = crmTeacherId == null ? "" : crmTeacherId.trim();
        if (normalized.isEmpty()) return;
        jdbc.update("""
            INSERT IGNORE INTO ranking_teacher_mappings(crm_teacher_id,owner_username) VALUES(?,?)
            """, normalized, ownerUsername);
    }
}
