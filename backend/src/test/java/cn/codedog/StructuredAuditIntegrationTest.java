package cn.codedog;

import cn.codedog.dao.UserRepository;
import cn.codedog.model.User;
import cn.codedog.security.PermissionCatalog;
import cn.codedog.service.StructuredAuditService;
import cn.codedog.service.StudentRankingAuthService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class StructuredAuditIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder encoder;
    @Autowired StructuredAuditService audit;

    @BeforeEach
    void extensionTable() {
        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS ranking_extension_devices (
              id BIGINT AUTO_INCREMENT PRIMARY KEY,
              token_hash CHAR(64) NOT NULL UNIQUE,
              owner_username VARCHAR(50) NOT NULL,
              device_name VARCHAR(100) NOT NULL,
              created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
              last_seen_at TIMESTAMP(6),
              revoked_at TIMESTAMP(6)
            )
            """);
    }

    @Test
    void recordsSuccessRejectionCsrfAndSanitizedQueryValues() throws Exception {
        MvcResult success = mvc.perform(get("/api/public/documents/latest")
                .param("password", "plain-secret")
                .param("phone", "15801493928")
                .header("User-Agent", "audit-test-agent"))
            .andExpect(status().isOk()).andReturn();
        Map<String,Object> successRow = row(success);
        assertThat(successRow).containsEntry("STATUS_CODE", 200).containsEntry("RESULT", "success")
            .containsEntry("ACTOR_TYPE", "ANONYMOUS").containsEntry("USER_AGENT", "audit-test-agent");
        assertThat(String.valueOf(successRow.get("QUERY_JSON")))
            .contains("[REDACTED]", "158****3928").doesNotContain("plain-secret", "15801493928");

        MvcResult unauthorized = mvc.perform(get("/api/dashboard"))
            .andExpect(status().isUnauthorized()).andReturn();
        assertThat(row(unauthorized)).containsEntry("STATUS_CODE", 401).containsEntry("RESULT", "rejected");

        MvcResult csrfFailure = mvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"Liam\",\"password\":\"must-never-be-logged\"}"))
            .andExpect(status().isForbidden()).andReturn();
        Map<String,Object> csrfRow = row(csrfFailure);
        assertThat(csrfRow).containsEntry("STATUS_CODE", 403).containsEntry("RESULT", "rejected");
        assertThat(String.valueOf(csrfRow)).doesNotContain("must-never-be-logged");
    }

    @Test
    void identifiesTeacherStudentAndExtensionActors() throws Exception {
        MockHttpSession teacher = login("Liam", "test-only-password");
        MvcResult teacherRequest = mvc.perform(get("/api/auth/me").session(teacher))
            .andExpect(status().isOk()).andReturn();
        assertThat(row(teacherRequest)).containsEntry("ACTOR_TYPE", "TEACHER")
            .containsEntry("ACTOR_ID", "Liam").containsEntry("OWNER_USERNAME", "Liam");

        MockHttpSession student = new MockHttpSession();
        student.setAttribute(StudentRankingAuthService.OWNER_ATTRIBUTE, "teacher-a");
        student.setAttribute(StudentRankingAuthService.STUDENT_ID_ATTRIBUTE, "student-42");
        student.setAttribute(StudentRankingAuthService.STUDENT_NAME_ATTRIBUTE, "张睿宸");
        MvcResult studentRequest = mvc.perform(get("/api/public/documents/latest").session(student))
            .andExpect(status().isOk()).andReturn();
        Map<String,Object> studentRow = row(studentRequest);
        assertThat(studentRow).containsEntry("ACTOR_TYPE", "STUDENT")
            .containsEntry("ACTOR_ID", "student-42").containsEntry("OWNER_USERNAME", "teacher-a");
        assertThat(studentRow.get("ACTOR_NAME")).isEqualTo("张*宸");

        String token = "audit-device-token-" + UUID.randomUUID();
        jdbc.update("INSERT INTO ranking_extension_devices(token_hash,owner_username,device_name) VALUES(?,?,?)",
            sha256(token), "teacher-device", "Chrome 教室电脑");
        MvcResult deviceRequest = mvc.perform(get("/api/public/rankings/extension/status")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk()).andReturn();
        Map<String,Object> deviceRow = row(deviceRequest);
        assertThat(deviceRow).containsEntry("ACTOR_TYPE", "EXTENSION_DEVICE")
            .containsEntry("OWNER_USERNAME", "teacher-device").containsEntry("ACTOR_NAME", "Chrome 教室电脑");
        assertThat(String.valueOf(deviceRow)).doesNotContain(token);
    }

    @Test
    void preservesFullDocumentChangesWithoutCredentialMaterial() throws Exception {
        MockHttpSession teacher = login("Liam", "test-only-password");
        String title = "审计全文-" + UUID.randomUUID();
        String content = "<p>需要完整追溯的正文内容</p>";
        MvcResult created = mvc.perform(post("/api/documents").session(teacher).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"" + title + "\",\"content\":\"" + content + "\",\"version\":0}"))
            .andExpect(status().isCreated()).andReturn();
        Map<String,Object> auditRow = row(created);
        assertThat(auditRow).containsEntry("EVENT_CODE", "document_created")
            .containsEntry("TARGET_TYPE", "DOCUMENT").containsEntry("OWNER_USERNAME", "Liam");
        assertThat(String.valueOf(auditRow.get("CHANGES_JSON"))).contains(title, "需要完整追溯的正文内容")
            .doesNotContain("password", "ciphertext", "token");
        assertThat((Long)auditRow.get("DURATION_MS")).isGreaterThanOrEqualTo(0L);
    }

    @Test
    void ordinaryTeacherIsTenantIsolatedAndExportNeedsSeparatePermission() throws Exception {
        String username = "auditteacher" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        String password = "audit-password-123";
        User teacher = new User();
        teacher.setUsername(username);
        teacher.setPasswordHash(encoder.encode(password));
        teacher.setAdmin(false);
        teacher.setPermissions(new LinkedHashSet<>(java.util.Set.of(PermissionCatalog.LOGS_VIEW)));
        users.saveAndFlush(teacher);
        String marker = "tenant-marker-" + UUID.randomUUID();
        long ownId = insertLog(username, "own-" + marker, Instant.now());
        long otherId = insertLog("other-tenant", "other-" + marker, Instant.now());
        MockHttpSession session = login(username, password);

        mvc.perform(get("/api/logs").session(session).param("keyword", marker))
            .andExpect(status().isOk())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.total").value(1))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.logs[0].id").value(ownId));
        mvc.perform(get("/api/logs/{id}", otherId).session(session)).andExpect(status().isForbidden());
        mvc.perform(get("/api/logs/export").session(session).param("keyword", marker))
            .andExpect(status().isForbidden());

        teacher.setPermissions(new LinkedHashSet<>(java.util.Set.of(PermissionCatalog.LOGS_VIEW, PermissionCatalog.LOGS_EXPORT)));
        users.saveAndFlush(teacher);
        String csv = mvc.perform(get("/api/logs/export").session(session).param("keyword", marker))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(csv).contains("own-" + marker).doesNotContain("other-" + marker);
    }

    @Test
    void administratorCanReadCrossTenantAndLegacyRows() throws Exception {
        long id = insertLog("another-teacher", "cross-tenant", Instant.now());
        MockHttpSession admin = login("Liam", "test-only-password");
        mvc.perform(get("/api/logs/{id}", id).session(admin))
            .andExpect(status().isOk())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.ownerUsername").value("another-teacher"));

        String requestId = "legacy-test-" + UUID.randomUUID();
        jdbc.update("""
            INSERT INTO audit_log(action,request_id,ip_address,created_at)
            VALUES(?,?,?,?)
            """, "document_created:legacy-doc", requestId, "127.0.0.1", Timestamp.from(Instant.now()));
        Long legacyId = jdbc.queryForObject("SELECT id FROM audit_log WHERE request_id=?", Long.class, requestId);
        mvc.perform(get("/api/logs/{id}", legacyId).session(admin))
            .andExpect(status().isOk())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.actorName").value("未知"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.module").value("documents"));
    }

    @Test
    void retentionDeletesOnlyExpiredRowsAndRecordsCleanup() {
        String oldCode = "expired-" + UUID.randomUUID();
        String currentCode = "current-" + UUID.randomUUID();
        insertLog("retention", oldCode, Instant.now().minus(366, ChronoUnit.DAYS));
        insertLog("retention", currentCode, Instant.now());

        audit.purgeExpired();

        assertThat(countByAction(oldCode)).isZero();
        assertThat(countByAction(currentCode)).isOne();
        assertThat(countByAction("audit_retention_cleanup")).isGreaterThanOrEqualTo(1);
    }

    @Test
    void exportRejectsMoreThanFiftyThousandMatchingRows() {
        String prefix = "bulk-" + UUID.randomUUID() + "-";
        jdbc.update("""
            INSERT INTO audit_log(action,owner_username,request_id,ip_address,created_at)
            SELECT ?, 'Liam', CONCAT(?, N), '127.0.0.1', CURRENT_TIMESTAMP
            FROM SYSTEM_RANGE(1,50001) R(N)
            """, "bulk-export-limit", prefix);
        var query = new StructuredAuditService.AuditQuery(null, null, null, null, null, null,
            null, null, null, null, null, "bulk-export-limit", null, 0);

        assertThatThrownBy(() -> audit.export(query, "Liam", true))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("50,000");
        jdbc.update("DELETE FROM audit_log WHERE request_id LIKE ?", prefix + "%");
    }

    private Map<String,Object> row(MvcResult result) {
        String requestId = result.getResponse().getHeader("X-Request-ID");
        assertThat(requestId).isNotBlank();
        return jdbc.queryForMap("SELECT * FROM audit_log WHERE request_id=?", requestId);
    }

    private long insertLog(String owner, String action, Instant createdAt) {
        String requestId = "test-" + UUID.randomUUID();
        jdbc.update("""
            INSERT INTO audit_log(action,owner_username,actor_type,actor_id,actor_name,request_id,
              http_method,request_path,status_code,duration_ms,module,event_type,event_code,result,
              ip_address,created_at)
            VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
            """, action, owner, "TEACHER", owner, owner, requestId, "GET", "/api/test", 200, 1,
            "system", "read", action, "success", "127.0.0.1", Timestamp.from(createdAt));
        return jdbc.queryForObject("SELECT id FROM audit_log WHERE request_id=?", Long.class, requestId);
    }

    private int countByAction(String action) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM audit_log WHERE action=?", Integer.class, action);
    }

    private MockHttpSession login(String username, String password) throws Exception {
        MvcResult result = mvc.perform(post("/api/auth/login").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
            .andExpect(status().isOk()).andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private String sha256(String value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
            .digest(value.getBytes(StandardCharsets.UTF_8)));
    }
}
