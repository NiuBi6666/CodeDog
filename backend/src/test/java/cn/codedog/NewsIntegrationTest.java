
package cn.codedog;
import cn.codedog.news.NewsService;
import cn.codedog.dao.UserRepository;
import cn.codedog.model.User;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import java.time.Instant;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import javax.sql.DataSource;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"NEWS_WEBHOOK_SECRET=test-news-secret-at-least-32-characters","NEWS_AI_API_KEY=","NEWS_GITHUB_TOKEN="})
@AutoConfigureMockMvc @TestInstance(TestInstance.Lifecycle.PER_CLASS)
class NewsIntegrationTest {
 @Autowired MockMvc mvc;@Autowired JdbcTemplate db;@Autowired DataSource ds;
 @Autowired ObjectMapper json;@Autowired NewsService news;@Autowired UserRepository users;@Autowired PasswordEncoder encoder;
 static final String SECRET="test-news-secret-at-least-32-characters";
 @BeforeAll void schema(){new ResourceDatabasePopulator(new ClassPathResource("db/migration/V132__lens_news.sql"),new ClassPathResource("db/migration/V133__lens_ai_settings.sql")).execute(ds);}
 @BeforeEach void clean(){db.update("DELETE FROM lens_articles");db.update("DELETE FROM lens_batches");db.update("UPDATE lens_sources SET lease_until=NULL,last_crawled_at=NULL");}
 MockHttpSession login(String name,String password)throws Exception{return (MockHttpSession)mvc.perform(post("/api/auth/login").with(csrf()).contentType(APPLICATION_JSON).content(json.writeValueAsString(Map.of("username",name,"password",password)))).andExpect(status().isOk()).andReturn().getRequest().getSession(false);}
 @Test void publicReadOnlyAndMissingConfigurationAreHonest()throws Exception{
  mvc.perform(get("/api/news")).andExpect(status().isOk()).andExpect(jsonPath("$.total").value(0));
  mvc.perform(get("/api/admin/news")).andExpect(status().isUnauthorized());
  mvc.perform(post("/api/user/refresh")).andExpect(status().isServiceUnavailable());
  mvc.perform(get("/api/crawler/tasks")).andExpect(status().isUnauthorized());
 }
 @Test void permissionGrantAndRevocationApplyInSameSessionAndAreAudited()throws Exception{
  User u=users.findByUsername("news-test-user").orElseGet(User::new);u.setUsername("news-test-user");u.setPasswordHash(encoder.encode("test-news-password"));u.setPermissions(Set.of());u=users.saveAndFlush(u);
  var session=login("news-test-user","test-news-password");
  mvc.perform(get("/api/admin/news").session(session)).andExpect(status().isForbidden());
  u.setPermissions(Set.of("news.read","news.sources.create","news.sources.update","news.sources.delete"));users.saveAndFlush(u);
  mvc.perform(get("/api/admin/news").session(session)).andExpect(status().isOk());
  var created=json.readTree(mvc.perform(post("/api/admin/sources").session(session).with(csrf()).contentType(APPLICATION_JSON).content("{\"source_name\":\"test-source\",\"rss_url\":\"https://example.com/feed\",\"category\":\"科技\",\"priority\":70,\"refresh_interval_min\":60}")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
  long id=created.path("id").asLong();
  mvc.perform(patch("/api/admin/sources/"+id).session(session).with(csrf()).contentType(APPLICATION_JSON).content("{\"is_active\":false}")).andExpect(status().isOk()).andExpect(jsonPath("$.is_active").value(false));
  assertThat(db.queryForObject("SELECT COUNT(*) FROM audit_log WHERE module='news' AND event_code='source_update'",Long.class)).isPositive();
  mvc.perform(delete("/api/admin/sources/"+id).session(session).with(csrf())).andExpect(status().isOk());
  u.setPermissions(Set.of());users.saveAndFlush(u);
  mvc.perform(get("/api/admin/news").session(session)).andExpect(status().isForbidden());
  mvc.perform(post("/api/admin/sources").session(session).with(csrf()).contentType(APPLICATION_JSON).content("{}")).andExpect(status().isForbidden());
 }
 @Test void webhookIsSignedDurableIdempotentAndDeduplicated()throws Exception{
  long source=((Number)db.queryForMap("SELECT id FROM lens_sources LIMIT 1").get("id")).longValue();
  String text="A public article about science and technology. ".repeat(40);
  byte[] body=json.writeValueAsBytes(Map.of("batch_id","signed-batch-123456","articles",List.of(Map.of("source_id",source,"origin_url","https://example.com/news?utm_source=rss","title_en","A real article","paragraphs_en",List.of(text),"published_at",Instant.now().toString())),"source_results",List.of(Map.of("source_id",source,"success",true))));
  String ts=String.valueOf(Instant.now().getEpochSecond());String sig=sign(body,ts);
  mvc.perform(post("/api/crawler/ingest").contentType(APPLICATION_JSON).content(body).header("X-Webhook-Secret",SECRET).header("X-Webhook-Timestamp",ts).header("X-Webhook-Signature","bad")).andExpect(status().isUnauthorized());
  for(int i=0;i<2;i++)mvc.perform(post("/api/crawler/ingest").contentType(APPLICATION_JSON).content(body).header("X-Webhook-Secret",SECRET).header("X-Webhook-Timestamp",ts).header("X-Webhook-Signature",sig)).andExpect(status().isOk());
  assertThat(db.queryForObject("SELECT COUNT(*) FROM lens_batches",Long.class)).isEqualTo(1);
  news.process();assertThat(db.queryForObject("SELECT COUNT(*) FROM lens_articles",Long.class)).isEqualTo(1);
  assertThat(db.queryForObject("SELECT status FROM lens_articles LIMIT 1",String.class)).isEqualTo("pending");
  mvc.perform(get("/api/news")).andExpect(jsonPath("$.total").value(0));
  long id=db.queryForObject("SELECT id FROM lens_articles LIMIT 1",Long.class);
  db.update("UPDATE lens_articles SET status='ready',title_zh='测试标题',summary_zh='一\n二\n三',content_zh='正文' WHERE id=?",id);
  mvc.perform(get("/api/news/"+id)).andExpect(status().isOk()).andExpect(jsonPath("$.origin_url").value("https://example.com/news"));
 }
 @Test void expiredLargePacketsAndInvalidTranslationAreRejected()throws Exception{
  byte[] b="{}".getBytes(StandardCharsets.UTF_8);String old=String.valueOf(Instant.now().minusSeconds(600).getEpochSecond());
  mvc.perform(post("/api/crawler/ingest").contentType(APPLICATION_JSON).content(b).header("X-Webhook-Secret",SECRET).header("X-Webhook-Timestamp",old).header("X-Webhook-Signature",sign(b,old))).andExpect(status().isUnauthorized());
  byte[] large=new byte[2*1024*1024+1];String now=String.valueOf(Instant.now().getEpochSecond());
  mvc.perform(post("/api/crawler/ingest").contentType(APPLICATION_JSON).content(large).header("X-Webhook-Secret",SECRET).header("X-Webhook-Timestamp",now).header("X-Webhook-Signature",sign(large,now))).andExpect(status().isPayloadTooLarge());
  var expected=List.<Map<String,Object>>of(Map.of("id",0,"text","hello"));
  assertThatThrownBy(()->NewsService.aligned(json.readTree("{\"paragraphs\":[{\"id\":1,\"text\":\"错位\"}]}"),expected)).isInstanceOf(IllegalArgumentException.class);
  assertThat(NewsService.aligned(json.readTree("{\"paragraphs\":[{\"id\":0,\"text\":\"你好\"}]}"),expected)).containsExactly("你好");
 }
 static String sign(byte[] bytes,String ts)throws Exception{Mac m=Mac.getInstance("HmacSHA256");m.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8),"HmacSHA256"));m.update((ts+".").getBytes(StandardCharsets.UTF_8));return HexFormat.of().formatHex(m.doFinal(bytes));}
}
