
package cn.codedog;
import cn.codedog.news.NewsAiSettings;
import cn.codedog.news.NewsService;
import cn.codedog.dao.UserRepository;
import cn.codedog.model.User;
import cn.codedog.service.PasswordRecoveryCipher;
import com.fasterxml.jackson.databind.*;
import java.util.*;
import javax.sql.DataSource;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:newsaisettings;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE","NEWS_AI_API_KEY=","NEWS_AI_BASE_URL=","NEWS_AI_MODEL="})
@AutoConfigureMockMvc @TestInstance(TestInstance.Lifecycle.PER_CLASS)
class NewsAiSettingsIntegrationTest {
 @Autowired MockMvc mvc;@Autowired JdbcTemplate db;@Autowired DataSource ds;@Autowired ObjectMapper json;
 @Autowired NewsAiSettings settings;@Autowired NewsService news;@Autowired UserRepository users;@Autowired PasswordEncoder encoder;
 static final String URL="/api/admin/news/ai-settings";
 static final String KEY="test-only-model-secret-never-a-real-key";
 MockHttpSession admin;
 @BeforeAll void schema()throws Exception{new ResourceDatabasePopulator(new ClassPathResource("db/migration/V132__lens_news.sql"),new ClassPathResource("db/migration/V133__lens_ai_settings.sql"),new ClassPathResource("db/migration/V134__lens_translation_mode.sql"),new ClassPathResource("db/migration/V135__lens_collector_settings.sql"),new ClassPathResource("db/migration/V136__lens_pipeline_speed.sql"),new ClassPathResource("db/migration/V137__lens_on_demand_translation.sql")).execute(ds);try(var connection=ds.getConnection()){new cn.codedog.news.migration.V138__news_namespace(new cn.codedog.service.PasswordRecoveryCipher("")).migrateConnection(connection);}new ResourceDatabasePopulator(new ClassPathResource("db/migration/V139__news_taxonomy.sql"),new ClassPathResource("db/migration/V140__remove_news_secondary_categories.sql")).execute(ds);}
 @BeforeEach void setup()throws Exception{db.update("DELETE FROM news_translation_settings");db.update("INSERT INTO news_translation_settings(id,mode,engine) VALUES(1,'api','bing')");db.update("DELETE FROM news_ai_settings");admin=login("Liam","test-only-password");}
 MockHttpSession login(String name,String password)throws Exception{return (MockHttpSession)mvc.perform(post("/api/auth/login").with(csrf()).contentType(APPLICATION_JSON).content(json.writeValueAsString(Map.of("username",name,"password",password)))).andExpect(status().isOk()).andReturn().getRequest().getSession(false);}
 String body(String base,String model,String key)throws Exception{return json.writeValueAsString(Map.of("base_url",base,"model",model,"api_key",key));}
 @Test void onlyRealSystemAdministratorCanReadWriteOrTestCredentials()throws Exception{
  mvc.perform(get(URL)).andExpect(status().isUnauthorized());
  mvc.perform(put(URL).with(csrf()).contentType(APPLICATION_JSON).content("{}")).andExpect(status().isUnauthorized());
  User u=new User();u.setUsername("ai-config-ordinary");u.setPasswordHash(encoder.encode("test-only-user-password"));u.setPermissions(Set.of("news.view","news.read","news.sources.update"));users.saveAndFlush(u);
  var session=login(u.getUsername(),"test-only-user-password");
  mvc.perform(get(URL).session(session)).andExpect(status().isForbidden());
  mvc.perform(put(URL).session(session).with(csrf()).contentType(APPLICATION_JSON).content("{}")).andExpect(status().isForbidden());
  mvc.perform(post(URL+"/test").session(session).with(csrf())).andExpect(status().isForbidden());
  mvc.perform(get(URL).session(admin)).andExpect(status().isOk());
  mvc.perform(put(URL).session(admin).contentType(APPLICATION_JSON).content("{}")).andExpect(status().isForbidden());
 }
 @Test void encryptedSettingsAreImmediatelyUsedAndNeverReturnedOrAuditedAsPlaintext()throws Exception{
  assertThat(news.aiReady()).isFalse();
  mvc.perform(put(URL).session(admin).with(csrf()).contentType(APPLICATION_JSON).content(body("https://api.siliconflow.cn/v1","test-model",KEY)))
   .andExpect(status().isOk()).andExpect(jsonPath("$.configured").value(true)).andExpect(jsonPath("$.api_key_configured").value(true)).andExpect(jsonPath("$.api_key").doesNotExist()).andExpect(jsonPath("$.api_key_ciphertext").doesNotExist()).andExpect(content().string(not(containsString(KEY))));
  String encrypted=db.queryForObject("SELECT api_key_ciphertext FROM news_ai_settings WHERE id=1",String.class);
  assertThat(encrypted).startsWith("v1:").doesNotContain(KEY);
  assertThat(settings.runtime().apiKey()).isEqualTo(KEY);assertThat(settings.runtime().toString()).doesNotContain(KEY);
  assertThat(news.aiReady()).isTrue();
  mvc.perform(get(URL).session(admin)).andExpect(status().isOk()).andExpect(content().string(not(containsString(KEY))));
  mvc.perform(put(URL).session(admin).with(csrf()).contentType(APPLICATION_JSON).content(body("https://api.siliconflow.cn/v1","new-model",""))).andExpect(status().isOk());
  assertThat(settings.runtime().model()).isEqualTo("new-model");assertThat(settings.runtime().apiKey()).isEqualTo(KEY);
  assertThat(db.queryForObject("SELECT api_key_ciphertext FROM news_ai_settings WHERE id=1",String.class)).isEqualTo(encrypted);
  mvc.perform(put(URL).session(admin).with(csrf()).contentType(APPLICATION_JSON).content(body("https://api.siliconflow.cn/v1","new-model","replacement-test-only-key"))).andExpect(status().isOk());
  assertThat(settings.runtime().apiKey()).isEqualTo("replacement-test-only-key");
  var audit=db.queryForList("SELECT changes_json,detail_json FROM audit_log WHERE module='news' AND event_code='ai_settings_update'");assertThat(audit).isNotEmpty();assertThat(audit.toString()).doesNotContain(KEY).doesNotContain("replacement-test-only-key").doesNotContain(encrypted);
 }
 @Test void changingProviderRequiresNewCredentialAndInvalidUrlsNeverPersist()throws Exception{
  mvc.perform(put(URL).session(admin).with(csrf()).contentType(APPLICATION_JSON).content(body("https://api.siliconflow.cn/v1","model",KEY))).andExpect(status().isOk());
  mvc.perform(put(URL).session(admin).with(csrf()).contentType(APPLICATION_JSON).content(body("https://api.deepseek.com/v1","model",""))).andExpect(status().isBadRequest());
  for(String base:List.of("http://api.example.com/v1","https://127.0.0.1/v1","https://api.example.com/v1?key=secret","https://user:password@api.example.com/v1","https://api.example.com/v1/chat/completions","https://localhost/v1"))
   mvc.perform(put(URL).session(admin).with(csrf()).contentType(APPLICATION_JSON).content(body(base,"model",KEY))).andExpect(status().isBadRequest());
  assertThat(settings.runtime().baseUrl()).isEqualTo("https://api.siliconflow.cn/v1");
 }
 @Test void defaultModeNeedsNoKeyAndSwitchingRetainsEncryptedApiSettings()throws Exception{
  db.update("DELETE FROM news_translation_settings");
  mvc.perform(get(URL).session(admin)).andExpect(status().isOk()).andExpect(jsonPath("$.mode").value("translators")).andExpect(jsonPath("$.configured").value(true)).andExpect(jsonPath("$.api_key_configured").value(false));
  mvc.perform(put(URL).session(admin).with(csrf()).contentType(APPLICATION_JSON).content("{\"mode\":\"translators\",\"engine\":\"alibaba\"}")).andExpect(status().isOk()).andExpect(jsonPath("$.engine").value("alibaba"));
  assertThat(news.aiReady()).isTrue();
  assertThat(db.queryForObject("SELECT COUNT(*) FROM news_ai_settings",Long.class)).isZero();
  mvc.perform(put(URL).session(admin).with(csrf()).contentType(APPLICATION_JSON).content(body("https://api.siliconflow.cn/v1","model",KEY))).andExpect(status().isOk()).andExpect(jsonPath("$.mode").value("api"));
  String encrypted=db.queryForObject("SELECT api_key_ciphertext FROM news_ai_settings WHERE id=1",String.class);
  mvc.perform(put(URL).session(admin).with(csrf()).contentType(APPLICATION_JSON).content("{\"mode\":\"translators\",\"engine\":\"bing\"}")).andExpect(status().isOk()).andExpect(jsonPath("$.mode").value("translators"));
  assertThat(db.queryForObject("SELECT api_key_ciphertext FROM news_ai_settings WHERE id=1",String.class)).isEqualTo(encrypted);
  assertThat(settings.runtime().apiKey()).isEqualTo(KEY);
  mvc.perform(put(URL).session(admin).with(csrf()).contentType(APPLICATION_JSON).content("{\"mode\":\"invalid\"}")).andExpect(status().isBadRequest());
  mvc.perform(put(URL).session(admin).with(csrf()).contentType(APPLICATION_JSON).content("{\"mode\":\"translators\",\"engine\":\"arbitrary\"}")).andExpect(status().isBadRequest());
  assertThat(db.queryForList("SELECT changes_json FROM audit_log WHERE module='news' AND event_code='ai_settings_update'").toString()).contains("translators").doesNotContain(KEY);
 }
 @Test void missingConfigAndMissingEncryptionFailClosed()throws Exception{
  mvc.perform(post(URL+"/test").session(admin).with(csrf())).andExpect(status().isServiceUnavailable());
  mvc.perform(put(URL).session(admin).with(csrf()).contentType(APPLICATION_JSON).content(body("https://api.example.com/v1","model",""))).andExpect(status().isBadRequest());
  var unavailable=new NewsAiSettings(db,new PasswordRecoveryCipher(""),json,"","","");
  assertThatThrownBy(()->unavailable.save(json.readTree(body("https://api.example.com/v1","model",KEY)))).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
  assertThat(db.queryForObject("SELECT COUNT(*) FROM news_ai_settings",Long.class)).isZero();
 }
}
