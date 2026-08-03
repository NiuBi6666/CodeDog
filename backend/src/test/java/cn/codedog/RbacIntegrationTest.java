package cn.codedog;

import cn.codedog.model.User;
import cn.codedog.repository.UserRepository;
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
        MockHttpSession admin = login("admin", "test-only-password");

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

        MockHttpSession admin = login("admin", "test-only-password");
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
        User adminUser = users.findByUsername("admin").orElseThrow();
        MockHttpSession admin = login("admin", "test-only-password");

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
        MockHttpSession admin = login("admin", "test-only-password");
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
