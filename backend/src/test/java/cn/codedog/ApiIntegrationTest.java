package cn.codedog;

import cn.codedog.model.Student;
import cn.codedog.repository.DocumentRepository;
import cn.codedog.repository.StudentRepository;
import cn.codedog.service.HtmlSanitizer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class ApiIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired StudentRepository students;
    @Autowired DocumentRepository documents;
    @Autowired HtmlSanitizer sanitizer;
    MockHttpSession session;

    @BeforeEach
    void login() throws Exception {
        var result = mvc.perform(post("/api/auth/login").with(csrf())
                .contentType(APPLICATION_JSON)
                .content("{\"username\":\"admin\",\"password\":\"test-only-password\"}"))
            .andExpect(status().isOk()).andReturn();
        session = (MockHttpSession) result.getRequest().getSession(false);
    }

    @Test
    void anonymousCanReadButCannotManage() throws Exception {
        mvc.perform(get("/api/public/documents/latest"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").isString())
            .andExpect(jsonPath("$.id").value(org.hamcrest.Matchers.matchesPattern("^[0-9a-f]{8}$")))
            .andExpect(jsonPath("$.title").isNotEmpty());
        mvc.perform(get("/api/dashboard")).andExpect(status().isUnauthorized());
    }

    @Test
    void documentCreateUpdateSanitizeAndOfflineFlow() throws Exception {
        String createdBody = mvc.perform(post("/api/documents").session(session).with(csrf())
                .contentType(APPLICATION_JSON)
                .content("{\"title\":\"每日文档\",\"content\":\"<p>内容</p>\",\"version\":0}"))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        JsonNode created = json.readTree(createdBody);
        String id = created.get("id").asText();
        assertThat(id).matches("^[0-9a-f]{8}$");
        long version = created.get("version").asLong();

        String update = "{\"title\":\"安全文档\",\"content\":\"<p onclick='bad()'>保留</p><script>alert(1)</script>\",\"version\":" + version + "}";
        mvc.perform(put("/api/documents/{id}", id).session(session).with(csrf())
                .contentType(APPLICATION_JSON).content(update))
            .andExpect(status().isOk()).andExpect(jsonPath("$.content").value("<p>保留</p>"));
        mvc.perform(patch("/api/documents/{id}/status", id).session(session).with(csrf())
                .contentType(APPLICATION_JSON).content("{\"status\":\"offline\"}"))
            .andExpect(status().isOk());
        mvc.perform(get("/api/public/documents/{id}", id)).andExpect(status().isGone());
    }

    @Test
    void staleDocumentVersionReturnsConflict() throws Exception {
        JsonNode current = json.readTree(mvc.perform(post("/api/documents").session(session).with(csrf())
                .contentType(APPLICATION_JSON)
                .content("{\"title\":\"并发文档\",\"content\":\"<p>初始</p>\",\"version\":0}"))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        String id = current.get("id").asText();
        long version = current.get("version").asLong();
        String body = "{\"title\":\"新版\",\"content\":\"<p>新版</p>\",\"version\":" + version + "}";
        mvc.perform(put("/api/documents/{id}", id).session(session).with(csrf()).contentType(APPLICATION_JSON).content(body))
            .andExpect(status().isOk());
        mvc.perform(put("/api/documents/{id}", id).session(session).with(csrf()).contentType(APPLICATION_JSON).content(body))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.document.title").value("新版"));
    }

    @Test
    void legacyNumericDocumentIdsRemainReadable() throws Exception {
        var document = documents.findAll().getFirst();
        mvc.perform(get("/api/documents/{id}", document.getId()).session(session))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(document.getPublicId()));
    }

    @Test
    void studentLookupSupportsNamesIdsDeduplicationAndAmbiguity() throws Exception {
        if (students.count() == 0) {
            students.save(student("1001", "张三"));
            students.save(student("1002", "张三"));
        }
        mvc.perform(post("/api/students/query").session(session).with(csrf())
                .contentType(APPLICATION_JSON)
                .content("{\"mode\":\"name\",\"values\":[\"张三\",\"张三\"]}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.summary.total").value(1))
            .andExpect(jsonPath("$.summary.ambiguous").value(1))
            .andExpect(jsonPath("$.results[0].matches.length()").value(2));
        mvc.perform(post("/api/students/query").session(session).with(csrf())
                .contentType(APPLICATION_JSON)
                .content("{\"mode\":\"id\",\"values\":[\"1001\"]}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.results[0].student.name").value("张三"));
    }

    @Test
    void invalidLoginAndCsrfAreRejected() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(APPLICATION_JSON)
                .content("{\"username\":\"admin\",\"password\":\"wrong\"}"))
            .andExpect(status().isForbidden());
        mvc.perform(post("/api/auth/login").with(csrf()).contentType(APPLICATION_JSON)
                .content("{\"username\":\"admin\",\"password\":\"wrong\"}"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void sanitizerKeepsSafeRasterImagesAndDropsSvg() {
        String clean = sanitizer.clean("<img src=\"data:image/png;base64,AAAA\" alt=\"示例\"><img src=\"data:image/svg+xml;base64,AAAA\"><p align=\"center\">居中</p><p align=\"sideways\">无效</p><script>bad()</script>");
        assertThat(clean).contains("data:image/png;base64,AAAA").contains("示例");
        assertThat(clean).contains("align=\"center\"");
        assertThat(clean).doesNotContain("image/svg+xml", "bad()", "sideways");
    }

    @Test
    void sanitizerKeepsSafeDingTalkFormattingAndMathSources() {
        String clean = sanitizer.clean("""
            <p style="text-align:center; margin-left:24px; font-size:18px; position:fixed; background-image:url(javascript:bad)">
              x<sup>2</sup>+a<sub>n</sub>
              <span class="math-formula other" data-latex="\\frac{3^{30}-1}{2}" data-display="true"><b>discard rendered markup</b></span>
            </p>
            """);
        assertThat(clean).contains("doc-align-center", "doc-indent-1", "doc-font-18");
        assertThat(clean).contains("<sup>2</sup>", "<sub>n</sub>");
        assertThat(clean).contains("class=\"math-formula\"", "data-latex=\"\\frac{3^{30}-1}{2}\"", "data-display=\"true\"");
        assertThat(clean).doesNotContain("style=", "position", "background-image", "discard rendered markup", "other");
    }

    @Test
    void auditLogsCanBeFilteredAndRemainPrivate() throws Exception {
        String createdBody = mvc.perform(post("/api/documents").session(session).with(csrf())
                .contentType(APPLICATION_JSON)
                .content("{\"title\":\"日志测试文档\",\"content\":\"<p>内容</p>\",\"version\":0}"))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String documentId = json.readTree(createdBody).get("id").asText();

        mvc.perform(get("/api/logs").session(session)
                .param("module", "documents").param("result", "success").param("keyword", documentId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.total").value(1))
            .andExpect(jsonPath("$.logs[0].operation").value("新建文档"))
            .andExpect(jsonPath("$.logs[0].detail").value("文档 #" + documentId));
        mvc.perform(get("/api/logs").session(session).param("module", "unknown"))
            .andExpect(status().isBadRequest());
        mvc.perform(get("/api/logs")).andExpect(status().isUnauthorized());
    }

    private Student student(String id, String name) {
        Student student = new Student(); student.setUserId(id); student.setName(name);
        student.setGender("男"); student.setAge("13"); student.setGrade("七年级"); student.setClassName("一班");
        return student;
    }
}
