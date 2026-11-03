package cn.codedog;

import cn.codedog.news.NewsCollectorSettings;
import cn.codedog.news.NewsService;
import cn.codedog.dao.UserRepository;
import cn.codedog.model.User;
import com.fasterxml.jackson.databind.*;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:lenscollectorsettings;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE","NEWS_GITHUB_TOKEN=","NEWS_GITHUB_REPO=","NEWS_WEBHOOK_SECRET="})
@AutoConfigureMockMvc @TestInstance(TestInstance.Lifecycle.PER_CLASS)
class NewsCollectorSettingsIntegrationTest {
 static final String URL="/api/admin/news/collector-settings",TOKEN="test-only-github-credential-never-real";
 static final List<String> calls=new CopyOnWriteArrayList<>();
 static volatile String fail="";static final Set<String> variables=new HashSet<>();static HttpServer server;
 static {try{server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);server.createContext("/",exchange->{
  String method=exchange.getRequestMethod(),path=exchange.getRequestURI().getPath(),body=new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8);
  int code=200;String response="{}";
  if(path.equals("/seal")){assertThat(body).contains("public_key","secret");response="{\"encrypted_value\":\"test-sealed-box-ciphertext\"}";}
  else {
   assertThat(exchange.getRequestHeaders().getFirst("Authorization")).isEqualTo("Bearer "+TOKEN);
   calls.add(method+" "+path+" "+body);
   if(fail.equals("secrets")&&path.endsWith("/secrets/public-key"))code=403;
   else if(fail.equals("variables")&&path.contains("/variables"))code=403;
   else if(path.endsWith("/secrets/public-key"))response="{\"key_id\":\"test-key-id\",\"key\":\"test-public-key\"}";
   else if(path.contains("/secrets/NEWS_WEBHOOK_SECRET")){assertThat(body).contains("test-sealed-box-ciphertext");code=201;}
   else if(path.contains("/variables/")&&method.equals("PATCH")){String name=path.substring(path.lastIndexOf('/')+1);code=variables.contains(name)?204:404;}
   else if(path.endsWith("/variables")&&method.equals("POST")){if(body.contains("NEWS_ENABLED"))variables.add("NEWS_ENABLED");else variables.add("NEWS_API_URL");code=201;}
   else if(path.endsWith("/enable")||path.endsWith("/dispatches"))code=204;
  }
  if(code==403)response="{\"message\":\"untrusted response should never be returned: "+TOKEN+"\"}";
  byte[] bytes=response.getBytes(StandardCharsets.UTF_8);exchange.getResponseHeaders().set("Content-Type","application/json");exchange.sendResponseHeaders(code,code==204?-1:bytes.length);try(var out=exchange.getResponseBody()){if(code!=204)out.write(bytes);}
 });server.start();}catch(Exception e){throw new RuntimeException(e);}}
 @DynamicPropertySource static void properties(DynamicPropertyRegistry registry){registry.add("NEWS_GITHUB_API_URL",()->"http://127.0.0.1:"+server.getAddress().getPort());registry.add("NEWS_TRANSLATOR_URL",()->"http://127.0.0.1:"+server.getAddress().getPort());}
 @Autowired MockMvc mvc;@Autowired JdbcTemplate db;@Autowired DataSource ds;@Autowired ObjectMapper json;
 @Autowired NewsCollectorSettings settings;@Autowired NewsService news;@Autowired UserRepository users;@Autowired PasswordEncoder encoder;
 MockHttpSession admin;
 @BeforeAll void schema(){new ResourceDatabasePopulator(new ClassPathResource("db/migration/V132__lens_news.sql"),new ClassPathResource("db/migration/V133__lens_ai_settings.sql"),new ClassPathResource("db/migration/V134__lens_translation_mode.sql"),new ClassPathResource("db/migration/V135__lens_collector_settings.sql"),new ClassPathResource("db/migration/V136__lens_pipeline_speed.sql"),new ClassPathResource("db/migration/V137__lens_on_demand_translation.sql")).execute(ds);db.execute("CREATE UNIQUE INDEX idx_collector_test_audit_request_id ON audit_log(request_id)");}
 @BeforeEach void setup()throws Exception{db.update("DELETE FROM lens_collector_settings");db.update("DELETE FROM lens_refresh_runs");calls.clear();variables.clear();fail="";admin=login("Liam","test-only-password");}
 @AfterAll void stop(){server.stop(0);}
 MockHttpSession login(String name,String password)throws Exception{return (MockHttpSession)mvc.perform(post("/api/auth/login").with(csrf()).contentType(APPLICATION_JSON).content(json.writeValueAsString(Map.of("username",name,"password",password)))).andExpect(status().isOk()).andReturn().getRequest().getSession(false);}
 String body(String token)throws Exception{return json.writeValueAsString(Map.of("repository","NiuBi6666/CodeDog","workflow","lens-crawl.yml","api_url","https://codedog.online","github_token",token));}
 void save()throws Exception{mvc.perform(put(URL).session(admin).with(csrf()).contentType(APPLICATION_JSON).content(body(TOKEN))).andExpect(status().isOk()).andExpect(content().string(not(containsString(TOKEN))));}
 void queue()throws Exception{mvc.perform(post(URL+"/provision").session(admin).with(csrf())).andExpect(status().isOk()).andExpect(jsonPath("$.setup_state").value("queued"));}
 @Test void onlySystemAdministratorCanReadSaveAndProvisionEvenAfterNewsPermissionGrants()throws Exception{
  mvc.perform(get(URL)).andExpect(status().isUnauthorized());
  mvc.perform(put(URL).with(csrf()).contentType(APPLICATION_JSON).content("{}")).andExpect(status().isUnauthorized());
  User user=new User();user.setUsername("collector-permission-test");user.setPasswordHash(encoder.encode("test-only-user-password"));user.setPermissions(Set.of());user=users.saveAndFlush(user);var session=login(user.getUsername(),"test-only-user-password");
  for(int phase=0;phase<3;phase++){
   user.setPermissions(phase==1?Set.of("news.read","news.refresh","news.sources.update"):Set.of());users.saveAndFlush(user);
   mvc.perform(get(URL).session(session)).andExpect(status().isForbidden());
   mvc.perform(put(URL).session(session).with(csrf()).contentType(APPLICATION_JSON).content(body(TOKEN))).andExpect(status().isForbidden());
   mvc.perform(post(URL+"/provision").session(session).with(csrf())).andExpect(status().isForbidden());
  }
  mvc.perform(get(URL).session(admin)).andExpect(status().isOk());
  mvc.perform(put(URL).session(admin).contentType(APPLICATION_JSON).content(body(TOKEN))).andExpect(status().isForbidden());
  mvc.perform(post(URL+"/provision").session(admin)).andExpect(status().isForbidden());assertThat(calls).isEmpty();
 }
 @Test void credentialsAreEncryptedRetainedAndNeverReturnedOrAudited()throws Exception{
  save();String secret=settings.secret();assertThat(secret).hasSize(64);assertThat(news.githubReady()).isFalse();
  var row=db.queryForMap("SELECT github_credential,webhook_credential FROM lens_collector_settings");assertThat(row.toString()).doesNotContain(TOKEN).doesNotContain(secret);assertThat(settings.runtime().toString()).doesNotContain(TOKEN).doesNotContain(secret);
  mvc.perform(get(URL).session(admin)).andExpect(content().string(not(containsString(TOKEN)))).andExpect(content().string(not(containsString(secret))));
  mvc.perform(put(URL).session(admin).with(csrf()).contentType(APPLICATION_JSON).content(body(""))).andExpect(status().isOk());assertThat(settings.runtime().token()).isEqualTo(TOKEN);assertThat(settings.secret()).isEqualTo(secret);
  var audits=db.queryForList("SELECT changes_json,detail_json FROM audit_log WHERE module='news' AND event_code='collector_settings_update'");assertThat(audits).isNotEmpty();assertThat(audits.toString()).doesNotContain(TOKEN).doesNotContain(secret).doesNotContain((String)row.get("github_credential"));
 }
 @Test void automaticProvisionSetsOnlyRequiredGithubValuesDispatchesAndSurvivesRetry()throws Exception{
  save();String secret=settings.secret();queue();settings.processProvision();assertThat(news.githubReady()).isTrue();assertThat(settings.view().get("setup_state")).isEqualTo("ready");
  assertThat(calls.toString()).contains("PUT /repos/NiuBi6666/CodeDog/actions/secrets/NEWS_WEBHOOK_SECRET","NEWS_API_URL","NEWS_ENABLED","/enable","/dispatches").doesNotContain(secret).doesNotContain(TOKEN);
  assertThat(calls.stream().filter(c->c.contains("/dispatches"))).hasSize(1);assertThat(variables).containsExactlyInAnyOrder("NEWS_API_URL","NEWS_ENABLED");
  news.authenticate(secret);assertThatThrownBy(()->news.authenticate("wrong")).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
  assertThat(db.queryForObject("SELECT COUNT(*) FROM lens_refresh_runs WHERE status='dispatched'",Long.class)).isEqualTo(1);
  queue();settings.processProvision();assertThat(news.githubReady()).isTrue();assertThat(settings.secret()).isEqualTo(secret);assertThat(calls.stream().filter(c->c.startsWith("POST")&&c.contains("/variables"))).hasSize(2);
  assertThat(db.queryForObject("SELECT COUNT(*) FROM audit_log WHERE event_code='collector_setup_completed' AND result='success' AND owner_username='Liam'",Long.class)).isGreaterThanOrEqualTo(2);
 }
 @Test void permissionFailuresAreSanitizedAndRetryRepairsPartialSetupWithoutDispatchingEarly()throws Exception{
  save();fail="secrets";queue();settings.processProvision();assertThat(settings.view().get("setup_state")).isEqualTo("failed");assertThat(news.githubReady()).isFalse();assertThat(calls.stream().anyMatch(c->c.contains("/dispatches"))).isFalse();assertThat(settings.view().get("last_error").toString()).contains("权限").doesNotContain(TOKEN);
  fail="variables";queue();settings.processProvision();assertThat(news.githubReady()).isFalse();assertThat(calls.stream().anyMatch(c->c.contains("/dispatches"))).isFalse();
  fail="";queue();settings.processProvision();assertThat(news.githubReady()).isTrue();assertThat(calls.stream().filter(c->c.contains("/dispatches"))).hasSize(1);
  assertThat(db.queryForList("SELECT error_message FROM audit_log WHERE event_code='collector_setup_completed' AND result='failed'").toString()).doesNotContain(TOKEN).contains("权限");
 }
 @Test void completionAuditHasIndependentIdAndPreservesOriginUnderProductionUniqueness()throws Exception{
  save();queue();String origin=db.queryForObject("SELECT request_id FROM lens_collector_settings WHERE id=1",String.class);
  assertThat(db.queryForObject("SELECT COUNT(*) FROM audit_log WHERE request_id=?",Long.class,origin)).isEqualTo(1);
  settings.processProvision();assertThat(settings.view().get("setup_state")).isEqualTo("ready");
  var completion=db.queryForMap("SELECT request_id,detail_json FROM audit_log WHERE event_code='collector_setup_completed' AND detail_json LIKE ?", "%"+origin+"%");
  assertThat(completion.get("request_id")).isNotEqualTo(origin);
  assertThat(json.readTree((String)completion.get("detail_json")).path("origin_request_id").asText()).isEqualTo(origin);
  assertThat(db.queryForObject("SELECT COUNT(*) FROM audit_log WHERE request_id=?",Long.class,origin)).isEqualTo(1);
 }
 @Test void expiredSetupLeaseRecoversWithoutDuplicateDispatch()throws Exception{
  save();queue();db.update("UPDATE lens_collector_settings SET setup_state='processing',lease_until=? WHERE id=1",java.sql.Timestamp.from(java.time.Instant.now().minusSeconds(1)));
  settings.processProvision();settings.processProvision();assertThat(news.githubReady()).isTrue();assertThat(calls.stream().filter(c->c.contains("/dispatches"))).hasSize(1);
 }
 @Test void invalidConfigurationAndChangesDuringSetupAreRejected()throws Exception{
  mvc.perform(put(URL).session(admin).with(csrf()).contentType(APPLICATION_JSON).content(body(""))).andExpect(status().isBadRequest());
  for(String body:List.of("{\"repository\":\"bad/repo/extra\",\"github_token\":\"test\"}","{\"repository\":\"owner/repo\",\"workflow\":\"../evil.yml\",\"github_token\":\"test\"}","{\"repository\":\"owner/repo\",\"api_url\":\"http://127.0.0.1\",\"github_token\":\"test\"}"))mvc.perform(put(URL).session(admin).with(csrf()).contentType(APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
  save();queue();mvc.perform(put(URL).session(admin).with(csrf()).contentType(APPLICATION_JSON).content(body(TOKEN))).andExpect(status().isConflict());assertThat(calls).isEmpty();
 }
}
