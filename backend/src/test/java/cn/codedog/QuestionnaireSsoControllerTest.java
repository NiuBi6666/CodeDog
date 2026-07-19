package cn.codedog;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class QuestionnaireSsoControllerTest {
    @Autowired MockMvc mvc;

    @Test
    void ssoRequiresCodeDogLogin() throws Exception {
        mvc.perform(get("/api/questionnaire/sso"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void authenticatedUserReceivesShortLivedSignedRedirect() throws Exception {
        mvc.perform(get("/api/questionnaire/sso").with(user("admin")))
            .andExpect(status().isFound())
            .andExpect(header().string("Location", startsWith("https://tduck.test/tduck-api/codedog/sso?token=")))
            .andExpect(header().string("Location", containsString(".")))
            .andExpect(header().string("Cache-Control", containsString("no-store")));
    }
}
