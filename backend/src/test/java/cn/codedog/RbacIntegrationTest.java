package cn.codedog;

import cn.codedog.model.User;
import cn.codedog.dao.UserRepository;
import cn.codedog.security.PermissionCatalog;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class RbacIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder encoder;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void ensureRankingMappingTables() {
        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS ranking_teacher_mappings (
              crm_teacher_id VARCHAR(100) PRIMARY KEY,
              owner_username VARCHAR(50) NOT NULL UNIQUE,
              created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
              updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP
            )
            """);
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
        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS ranking_student_accounts (
              id BIGINT AUTO_INCREMENT PRIMARY KEY,
              phone VARCHAR(20) NOT NULL UNIQUE,
              owner_username VARCHAR(50) NOT NULL,
              student_id VARCHAR(100) NOT NULL,
              student_name VARCHAR(100) NOT NULL,
              password_hash VARCHAR(100) NOT NULL,
              password_ciphertext CLOB,
              enabled BOOLEAN NOT NULL DEFAULT TRUE,
              created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
              updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
              password_changed_at TIMESTAMP(6),
              UNIQUE(owner_username, student_id)
            )
            """);
        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS crm_external_contacts (
              owner_username VARCHAR(50) NOT NULL,
              crm_user_id VARCHAR(100) NOT NULL,
              external_userid VARCHAR(128) NOT NULL,
              created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
              updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
              PRIMARY KEY (owner_username, crm_user_id)
            )
            """);
        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS crm_external_contact_observations (
              owner_username VARCHAR(50) NOT NULL,
              crm_user_id VARCHAR(100) NOT NULL,
              external_userid VARCHAR(128) NOT NULL,
              first_seen_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
              last_seen_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
              seen_count BIGINT NOT NULL DEFAULT 1,
              PRIMARY KEY (owner_username, crm_user_id, external_userid)
            )
            """);
    }

    @Test
    void registrationCreatesMinimumPermissionUser() throws Exception {
        String username = uniqueUsername("member");
        String password = "member-password-123";

        mvc.perform(post("/api/auth/register").with(csrf())
                .contentType(APPLICATION_JSON)
                .content("""
                    {"username":"%s","password":"%s","confirmation":"%s"}
                    """.formatted(username, password, password)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.username").value(username));

        for (String duplicate : java.util.List.of(username, username.toUpperCase(java.util.Locale.ROOT))) {
            mvc.perform(post("/api/auth/register").with(csrf())
                    .contentType(APPLICATION_JSON)
                    .content("""
                        {"username":"%s","password":"%s","confirmation":"%s"}
                        """.formatted(duplicate, password, password)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("用户名已存在"));
        }
        assertThat(jdbc.queryForObject(
            "SELECT COUNT(*) FROM users WHERE LOWER(username)=LOWER(?)", Integer.class, username
        )).isEqualTo(1);

        User registered = users.findByUsername(username).orElseThrow();
        assertThat(registered.isAdmin()).isFalse();
        assertThat(registered.getPermissions()).containsExactly(PermissionCatalog.DASHBOARD_VIEW);
        assertThat(encoder.matches(password, registered.getPasswordHash())).isTrue();

        MockHttpSession session = login(username, password);
        mvc.perform(get("/api/auth/me").session(session))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.admin").value(false))
            .andExpect(jsonPath("$.permissions.length()").value(1))
            .andExpect(jsonPath("$.permissions[0]").value(PermissionCatalog.DASHBOARD_VIEW));
        mvc.perform(get("/api/dashboard").session(session))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.documentTotal").value(org.hamcrest.Matchers.nullValue()))
            .andExpect(jsonPath("$.documentNormal").value(org.hamcrest.Matchers.nullValue()))
            .andExpect(jsonPath("$.documentOffline").value(org.hamcrest.Matchers.nullValue()))
            .andExpect(jsonPath("$.studentCount").value(org.hamcrest.Matchers.nullValue()))
            .andExpect(jsonPath("$.latestDocument").value(org.hamcrest.Matchers.nullValue()));
        mvc.perform(get("/api/documents").session(session))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error").value("无权限执行此操作"));
        mvc.perform(get("/api/admin/users").session(session)).andExpect(status().isForbidden());
    }

    @Test
    void dashboardDataPermissionsAreIndependentAndRefreshWithinExistingSession() throws Exception {
        String username = uniqueUsername("dashboard");
        String password = "member-password-123";
        User member = users.saveAndFlush(user(username, password));
        MockHttpSession memberSession = login(username, password);
        MockHttpSession admin = login("Liam", "test-only-password");

        mvc.perform(put("/api/admin/users/{id}/permissions", member.getId()).session(admin).with(csrf())
                .contentType(APPLICATION_JSON)
                .content("""
                    {"permissions":["dashboard.view","dashboard.document_stats"]}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.permissions.length()").value(2));

        mvc.perform(get("/api/dashboard").session(memberSession))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.documentTotal").isNumber())
            .andExpect(jsonPath("$.documentNormal").isNumber())
            .andExpect(jsonPath("$.documentOffline").isNumber())
            .andExpect(jsonPath("$.studentCount").value(org.hamcrest.Matchers.nullValue()))
            .andExpect(jsonPath("$.latestDocument").value(org.hamcrest.Matchers.nullValue()));

        mvc.perform(put("/api/admin/users/{id}/permissions", member.getId()).session(admin).with(csrf())
                .contentType(APPLICATION_JSON)
                .content("""
                    {"permissions":["dashboard.view"]}
                    """))
            .andExpect(status().isOk());

        mvc.perform(get("/api/dashboard").session(memberSession))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.documentTotal").value(org.hamcrest.Matchers.nullValue()))
            .andExpect(jsonPath("$.documentNormal").value(org.hamcrest.Matchers.nullValue()))
            .andExpect(jsonPath("$.documentOffline").value(org.hamcrest.Matchers.nullValue()))
            .andExpect(jsonPath("$.studentCount").value(org.hamcrest.Matchers.nullValue()))
            .andExpect(jsonPath("$.latestDocument").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void administratorCanGrantAndImmediatelyRevokeButtonPermission() throws Exception {
        String username = uniqueUsername("grant");
        String password = "member-password-123";
        User member = user(username, password);
        users.saveAndFlush(member);

        MockHttpSession admin = login("Liam", "test-only-password");
        mvc.perform(get("/api/admin/permissions").session(admin))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[1].permissions[0].type").value("page"))
            .andExpect(jsonPath("$[1].permissions[1].type").value("action"));

        mvc.perform(put("/api/admin/users/{id}/permissions", member.getId()).session(admin).with(csrf())
                .contentType(APPLICATION_JSON)
                .content("""
                    {"permissions":["dashboard.view","students.view","students.query"]}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.permissions.length()").value(3));

        MockHttpSession memberSession = login(username, password);
        mvc.perform(post("/api/students/query").session(memberSession).with(csrf())
                .contentType(APPLICATION_JSON)
                .content("""
                    {"mode":"id","values":["missing"]}
                    """))
            .andExpect(status().isOk());

        mvc.perform(put("/api/admin/users/{id}/permissions", member.getId()).session(admin).with(csrf())
                .contentType(APPLICATION_JSON)
                .content("""
                    {"permissions":["dashboard.view"]}
                    """))
            .andExpect(status().isOk());

        mvc.perform(post("/api/students/query").session(memberSession).with(csrf())
                .contentType(APPLICATION_JSON)
                .content("""
                    {"mode":"id","values":["missing"]}
                    """))
            .andExpect(status().isForbidden());
    }


    @Test
    void managementEndpointsRequireTheirExactConfiguredPermission() throws Exception {
        String username = uniqueUsername("manager");
        String password = "member-password-123";
        User manager = users.saveAndFlush(user(username, password));
        User target = users.saveAndFlush(user(uniqueUsername("target"), password));
        MockHttpSession session = login(username, password);

        mvc.perform(get("/api/admin/users").session(session)).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/exams").session(session)).andExpect(status().isForbidden());
        mvc.perform(get("/api/rankings/admin/board").session(session)).andExpect(status().isForbidden());
        mvc.perform(post("/api/rankings/admin/announcements").session(session).with(csrf())
                .contentType(APPLICATION_JSON).content("{\"text\":\"test\"}"))
            .andExpect(status().isForbidden());
        mvc.perform(delete("/api/rankings/admin/rewards/1").session(session).with(csrf()))
            .andExpect(status().isForbidden());

        manager.setPermissions(new LinkedHashSet<>(java.util.Set.of(
            PermissionCatalog.DASHBOARD_VIEW,
            PermissionCatalog.USERS_VIEW,
            PermissionCatalog.USERS_PERMISSIONS_MANAGE)));
        users.saveAndFlush(manager);

        mvc.perform(get("/api/admin/users").session(session)).andExpect(status().isOk());
        mvc.perform(get("/api/admin/permissions").session(session))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[?(@.key == 'rankings')].permissions[?(@.code == 'rankings.rewards.delete')]").exists())
            .andExpect(jsonPath("$[?(@.key == 'exams')].permissions[?(@.code == 'exams.create')]").exists());

        mvc.perform(put("/api/admin/users/{id}/permissions", target.getId()).session(session).with(csrf())
                .contentType(APPLICATION_JSON)
                .content("{\"permissions\":[\"dashboard.view\"]}"))
            .andExpect(status().isOk());
        mvc.perform(put("/api/admin/users/{id}/permissions", manager.getId()).session(session).with(csrf())
                .contentType(APPLICATION_JSON)
                .content("{\"permissions\":[\"dashboard.view\"]}"))
            .andExpect(status().isForbidden());
        mvc.perform(put("/api/admin/users/{id}/permissions", target.getId()).session(session).with(csrf())
                .contentType(APPLICATION_JSON)
                .content("{\"permissions\":[\"dashboard.view\",\"rankings.view\"]}"))
            .andExpect(status().isForbidden());
    }
    @Test
    void questionnaireSsoMarksNormalUsersAsNonAdministrators() throws Exception {
        String username = uniqueUsername("survey");
        String password = "member-password-123";
        User member = user(username, password);
        member.setPermissions(new LinkedHashSet<>(java.util.Set.of(
            PermissionCatalog.DASHBOARD_VIEW, PermissionCatalog.QUESTIONNAIRE_VIEW)));
        users.saveAndFlush(member);

        MockHttpSession session = login(username, password);
        String location = mvc.perform(get("/api/questionnaire/sso").session(session))
            .andExpect(status().isFound())
            .andReturn().getResponse().getHeader("Location");
        assertThat(location).isNotBlank();
        String token = java.net.URI.create(location).getRawQuery().substring("token=".length());
        String payload = token.substring(0, token.indexOf('.'));
        assertThat(json.readTree(Base64.getUrlDecoder().decode(payload)).get("admin").asBoolean()).isFalse();
    }

    @Test
    void permissionAdministrationRejectsUnknownCodesAndAdminMutation() throws Exception {
        String username = uniqueUsername("invalid");
        User member = users.saveAndFlush(user(username, "member-password-123"));
        User adminUser = users.findByUsername("Liam").orElseThrow();
        MockHttpSession admin = login("Liam", "test-only-password");

        mvc.perform(put("/api/admin/users/{id}/permissions", member.getId()).session(admin).with(csrf())
                .contentType(APPLICATION_JSON)
                .content("""
                    {"permissions":["unknown.permission"]}
                    """))
            .andExpect(status().isBadRequest());

        mvc.perform(put("/api/admin/users/{id}/permissions", member.getId()).session(admin).with(csrf())
                .contentType(APPLICATION_JSON)
                .content("""
                    {"permissions":["dashboard.document_stats"]}
                    """))
            .andExpect(status().isUnprocessableEntity());

        mvc.perform(put("/api/admin/users/{id}/permissions", adminUser.getId()).session(admin).with(csrf())
                .contentType(APPLICATION_JSON)
                .content("""
                    {"permissions":[]}
                    """))
            .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void extensionStatusSupportsCrmCorsWithoutAuthentication() throws Exception {
        mvc.perform(options("/api/public/rankings/extension/status")
                .header("Origin", "https://sk-crm.codemao.cn")
                .header("Access-Control-Request-Method", "GET"))
            .andExpect(status().isOk())
            .andExpect(header().string("Access-Control-Allow-Origin", "https://sk-crm.codemao.cn"))
            .andExpect(header().string("Access-Control-Allow-Methods", org.hamcrest.Matchers.containsString("GET")));

        mvc.perform(options("/api/public/rankings/extension/contacts")
                .header("Origin", "chrome-extension://test-extension-id")
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "authorization,content-type"))
            .andExpect(status().isOk())
            .andExpect(header().string("Access-Control-Allow-Origin", "chrome-extension://test-extension-id"))
            .andExpect(header().string("Access-Control-Allow-Methods", org.hamcrest.Matchers.containsString("POST")))
            .andExpect(header().string("Access-Control-Allow-Headers", org.hamcrest.Matchers.containsStringIgnoringCase("authorization")));

        mvc.perform(get("/api/public/rankings/extension/status")
                .header("Origin", "https://sk-crm.codemao.cn"))
            .andExpect(status().isOk())
            .andExpect(header().string("Access-Control-Allow-Origin", "https://sk-crm.codemao.cn"))
            .andExpect(jsonPath("$.ok").value(true))
            .andExpect(jsonPath("$.serverTime").isString());

        mvc.perform(get("/api/public/rankings/extension/session"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error").value("缺少扩展设备令牌"));
    }

    @Test
    void administratorMapsCrmTeacherAndBootstrapUsesMappedOwner() throws Exception {
        String username = uniqueUsername("mapped");
        User member = users.saveAndFlush(user(username, "member-password-123"));
        MockHttpSession admin = login("Liam", "test-only-password");
        String crmTeacherId = "crm" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);

        mvc.perform(put("/api/admin/users/{id}/crm-teacher", member.getId()).session(admin).with(csrf())
                .contentType(APPLICATION_JSON)
                .content("""
                    {"crmTeacherId":"%s"}
                    """.formatted(crmTeacherId)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.teacherId").value(member.getTeacherPublicId()))
            .andExpect(jsonPath("$.crmTeacherId").value(crmTeacherId));

        mvc.perform(post("/api/public/rankings/extension/bootstrap")
                .contentType(APPLICATION_JSON)
                .content("""
                    {"crmTeacherId":"%s","deviceName":"integration-test"}
                    """.formatted(crmTeacherId)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.username").value(username))
            .andExpect(jsonPath("$.teacherId").value(member.getTeacherPublicId()));

        mvc.perform(put("/api/admin/users/{id}/crm-teacher", member.getId()).session(admin).with(csrf())
                .contentType(APPLICATION_JSON)
                .content("{\"crmTeacherId\":null}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.crmTeacherId").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void extensionStoresExternalContactsWithoutOverwritingConflicts() throws Exception {
        String username = uniqueUsername("contacts");
        User member = users.saveAndFlush(user(username, "member-password-123"));
        String crmTeacherId = "crm" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        jdbc.update("INSERT INTO ranking_teacher_mappings(crm_teacher_id,owner_username) VALUES(?,?)", crmTeacherId, username);
        String response = mvc.perform(post("/api/public/rankings/extension/bootstrap")
                .contentType(APPLICATION_JSON)
                .content("""
                    {"crmTeacherId":"%s","deviceName":"contact-sync-test"}
                    """.formatted(crmTeacherId)))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        String token = json.readTree(response).get("token").asText();

        mvc.perform(post("/api/public/rankings/extension/contacts")
                .header("Authorization", "Bearer " + token)
                .contentType(APPLICATION_JSON)
                .content("""
                    {"contacts":[{"crmUserId":"1965973887","externalUserId":"wmKdjSDAAAl1NkxWHoKwGK-yTm1GJmcQ"}]}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.inserted").value(1))
            .andExpect(jsonPath("$.conflicts").value(0));

        mvc.perform(post("/api/public/rankings/extension/contacts")
                .header("Authorization", "Bearer " + token)
                .contentType(APPLICATION_JSON)
                .content("""
                    {"contacts":[{"crmUserId":"1965973887","externalUserId":"wmKdjSDAAAl1NkxWHoKwGK-yTm1GJmcQ"}]}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.unchanged").value(1));

        mvc.perform(post("/api/public/rankings/extension/contacts")
                .header("Authorization", "Bearer " + token)
                .contentType(APPLICATION_JSON)
                .content("""
                    {"contacts":[{"crmUserId":"1965973887","externalUserId":"wmDifferentExternalUserId"}]}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.conflicts").value(1))
            .andExpect(jsonPath("$.errors[0].crmUserId").value("1965973887"));

        mvc.perform(post("/api/public/rankings/extension/contacts")
                .header("Authorization", "Bearer " + token)
                .contentType(APPLICATION_JSON)
                .content("""
                    {"contacts":[
                      {"crmUserId":"200","externalUserId":"wmFirstExternalUserId"},
                      {"crmUserId":"200","externalUserId":"wmSecondExternalUserId"}
                    ]}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.inserted").value(0))
            .andExpect(jsonPath("$.conflicts").value(1))
            .andExpect(jsonPath("$.errors[0].crmUserId").value("200"));
        assertThat(jdbc.queryForObject(
            "SELECT COUNT(*) FROM crm_external_contacts WHERE owner_username=? AND crm_user_id=?",
            Integer.class, member.getUsername(), "200"
        )).isZero();
        assertThat(jdbc.queryForObject(
            "SELECT COUNT(*) FROM crm_external_contact_observations WHERE owner_username=? AND crm_user_id=?",
            Integer.class, member.getUsername(), "1965973887"
        )).isEqualTo(2);
        assertThat(jdbc.queryForObject(
            "SELECT COUNT(*) FROM crm_external_contact_observations WHERE owner_username=? AND crm_user_id=?",
            Integer.class, member.getUsername(), "200"
        )).isEqualTo(2);
        assertThat(jdbc.queryForObject(
            "SELECT SUM(seen_count) FROM crm_external_contact_observations WHERE owner_username=? AND crm_user_id=?",
            Long.class, member.getUsername(), "1965973887"
        )).isEqualTo(3L);

        assertThat(jdbc.queryForObject(
            "SELECT external_userid FROM crm_external_contacts WHERE owner_username=? AND crm_user_id=?",
            String.class, member.getUsername(), "1965973887"
        )).isEqualTo("wmKdjSDAAAl1NkxWHoKwGK-yTm1GJmcQ");
    }

    @Test
    void onlyAdministratorCanRecoverAndResetOwnedStudentPasswords() throws Exception {
        String memberName = uniqueUsername("passwordviewer");
        String memberPassword = "member-password-123";
        users.saveAndFlush(user(memberName, memberPassword));
        String studentId = "student-" + UUID.randomUUID().toString().substring(0, 8);
        String otherStudentId = "other-" + UUID.randomUUID().toString().substring(0, 8);
        String phone = "1" + String.format("%010d", Math.abs(UUID.randomUUID().getLeastSignificantBits()) % 10_000_000_000L);
        String otherPhone = "1" + String.format("%010d", Math.abs(UUID.randomUUID().getMostSignificantBits()) % 10_000_000_000L);
        jdbc.update("INSERT INTO ranking_student_accounts(phone,owner_username,student_id,student_name,password_hash,enabled) VALUES(?,?,?,?,?,TRUE)",
            phone, "Liam", studentId, "测试学生", encoder.encode("123456"));
        jdbc.update("INSERT INTO ranking_student_accounts(phone,owner_username,student_id,student_name,password_hash,enabled) VALUES(?,?,?,?,?,TRUE)",
            otherPhone, memberName, otherStudentId, "其他老师学生", encoder.encode("123456"));

        MockHttpSession member = login(memberName, memberPassword);
        mvc.perform(get("/api/rankings/admin/students/{studentId}/password", studentId).session(member))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error").value("无权限执行此操作"));

        MockHttpSession admin = login("Liam", "test-only-password");
        mvc.perform(get("/api/rankings/admin/students/{studentId}/password", studentId).session(admin))
            .andExpect(status().isOk())
            .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")))
            .andExpect(jsonPath("$.studentName").value("测试学生"))
            .andExpect(jsonPath("$.password").value(org.hamcrest.Matchers.nullValue()));

        mvc.perform(get("/api/rankings/admin/students/{studentId}/password", otherStudentId).session(admin))
            .andExpect(status().isNotFound());

        String resetPassword = "new-student-password";
        mvc.perform(put("/api/rankings/admin/students/{studentId}/password", studentId).session(admin).with(csrf())
                .contentType(APPLICATION_JSON).content("{\"password\":\"" + resetPassword + "\"}"))
            .andExpect(status().isOk())
            .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")))
            .andExpect(jsonPath("$.password").value(resetPassword));

        String hash = jdbc.queryForObject("SELECT password_hash FROM ranking_student_accounts WHERE owner_username='Liam' AND student_id=?", String.class, studentId);
        String ciphertext = jdbc.queryForObject("SELECT password_ciphertext FROM ranking_student_accounts WHERE owner_username='Liam' AND student_id=?", String.class, studentId);
        assertThat(encoder.matches(resetPassword, hash)).isTrue();
        assertThat(ciphertext).startsWith("v1:").doesNotContain(resetPassword);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_log WHERE action=?", Integer.class,
            "student_password_viewed:" + studentId)).isPositive();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_log WHERE action=?", Integer.class,
            "student_password_reset:" + studentId)).isPositive();
    }

    @Test
    void crmTeacherMappingRequiresAdministrator() throws Exception {
        String username = uniqueUsername("nomap");
        User member = users.saveAndFlush(user(username, "member-password-123"));
        MockHttpSession session = login(username, "member-password-123");
        mvc.perform(put("/api/admin/users/{id}/crm-teacher", member.getId()).session(session).with(csrf())
                .contentType(APPLICATION_JSON).content("{\"crmTeacherId\":\"29413\"}"))
            .andExpect(status().isForbidden());
    }

    private MockHttpSession login(String username, String password) throws Exception {
        var result = mvc.perform(post("/api/auth/login").with(csrf())
                .contentType(APPLICATION_JSON)
                .content("""
                    {"username":"%s","password":"%s"}
                    """.formatted(username, password)))
            .andExpect(status().isOk()).andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private User user(String username, String password) {
        User user = new User();
        user.setUsername(username);
        user.setPasswordHash(encoder.encode(password));
        user.setAdmin(false);
        user.setCreatedAt(Instant.now());
        user.setUpdatedAt(Instant.now());
        user.setPermissions(new LinkedHashSet<>(PermissionCatalog.DEFAULT_PERMISSIONS));
        return user;
    }

    private String uniqueUsername(String prefix) {
        return prefix + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }
}
