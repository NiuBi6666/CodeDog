package cn.codedog;
import cn.codedog.news.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:newsselection;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE","NEWS_AI_API_KEY="})
@AutoConfigureMockMvc @TestInstance(TestInstance.Lifecycle.PER_CLASS)
class NewsSelectionTranslationIntegrationTest {
 static final AtomicInteger calls=new AtomicInteger(),ip=new AtomicInteger();
 static HttpServer server;
 static {try{
  server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
  server.createContext("/translate-selection",exchange->{
   String input=new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8);
   assertThat(input).contains("paragraphs","bing").doesNotContain("api_key");
   calls.incrementAndGet();byte[] bytes="{\"paragraphs\":[\"人工智能\"]}".getBytes(StandardCharsets.UTF_8);
   exchange.getResponseHeaders().set("Content-Type","application/json");exchange.sendResponseHeaders(200,bytes.length);
   try(var out=exchange.getResponseBody()){out.write(bytes);}
  });server.start();
 }catch(Exception e){throw new RuntimeException(e);}}
 @DynamicPropertySource static void endpoint(DynamicPropertyRegistry registry){registry.add("NEWS_TRANSLATOR_URL",()->"http://127.0.0.1:"+server.getAddress().getPort());}
 @Autowired MockMvc mvc;@Autowired JdbcTemplate db;@Autowired DataSource ds;@Autowired ObjectMapper json;
 @MockBean NewsAiSettings ai;
 long id;
 @BeforeAll void schema()throws Exception{new ResourceDatabasePopulator(new ClassPathResource("db/migration/V132__lens_news.sql"),new ClassPathResource("db/migration/V133__lens_ai_settings.sql"),new ClassPathResource("db/migration/V134__lens_translation_mode.sql"),new ClassPathResource("db/migration/V135__lens_collector_settings.sql"),new ClassPathResource("db/migration/V136__lens_pipeline_speed.sql"),new ClassPathResource("db/migration/V137__lens_on_demand_translation.sql")).execute(ds);try(var connection=ds.getConnection()){new cn.codedog.news.migration.V138__news_namespace(new cn.codedog.service.PasswordRecoveryCipher("")).migrateConnection(connection);}}
 @BeforeEach void setup(){
  db.update("DELETE FROM news_translation_settings");db.update("DELETE FROM news_translation_cache");db.update("DELETE FROM news_articles");
  db.update("INSERT INTO news_articles(url_hash,origin_url,source_id,source_name,category,title_en,title_zh,content_en,content_zh,status,published_at) VALUES('selection-test','https://example.com/selection',1,'Test','科技','New AI research','新研究','Artificial intelligence improves research.\\n\\nFirst sentence. Second sentence.','译文','ready',CURRENT_TIMESTAMP)");
  id=db.queryForObject("SELECT id FROM news_articles",Long.class);
 }
 @AfterAll void close(){server.stop(0);}
 MockHttpServletRequestBuilder request(String text)throws Exception{return post("/api/news/translate").with(csrf()).header("X-Real-IP","198.51.100."+ip.incrementAndGet()).contentType(APPLICATION_JSON).content(json.writeValueAsString(Map.of("article_id",id,"text",text)));}
 @Test void publicSelectionUsesDefaultEngineCacheAndSanitizedAudit()throws Exception{
  int before=calls.get();
  mvc.perform(request("Artificial intelligence")).andExpect(status().isOk()).andExpect(jsonPath("$.translation").value("人工智能")).andExpect(jsonPath("$.provider").value("translators / bing")).andExpect(jsonPath("$.cached").value(false));
  mvc.perform(request("Artificial intelligence")).andExpect(status().isOk()).andExpect(jsonPath("$.cached").value(true));
  assertThat(calls.get()-before).isEqualTo(1);
  var events=db.queryForList("SELECT detail_json FROM audit_log WHERE event_code='selection_translate'");
  assertThat(events).isNotEmpty();assertThat(events.toString()).doesNotContain("Artificial intelligence","api_key");
 }
 @Test void rejectsMissingCsrfUnpublishedArticlesAndUnrelatedOrOversizeText()throws Exception{
  mvc.perform(post("/api/news/translate").contentType(APPLICATION_JSON).content("{}")).andExpect(status().isForbidden());
  mvc.perform(request("Text from somewhere else")).andExpect(status().isBadRequest());
  mvc.perform(request("a".repeat(1001))).andExpect(status().isBadRequest());
  mvc.perform(post("/api/news/translate").with(csrf()).contentType(APPLICATION_JSON).content(" ".repeat(4097))).andExpect(status().isPayloadTooLarge());
  mvc.perform(post("/api/news/translate").with(csrf()).contentType(APPLICATION_JSON).content("{bad")).andExpect(status().isBadRequest());
  db.update("UPDATE news_articles SET status='pending',title_zh=NULL WHERE id=?",id);
  mvc.perform(request("Artificial intelligence")).andExpect(status().isNotFound());
  mvc.perform(get("/api/admin/news")).andExpect(status().isUnauthorized());
 }
 @Test void respectsConfiguredApiAndDoesNotExposeProviderErrors()throws Exception{
  db.update("INSERT INTO news_translation_settings(id,mode,engine) VALUES(1,'api','bing')");
  when(ai.configured()).thenReturn(true);
  var runtime=new NewsAiSettings.RuntimeConfig("https://api.example.com/v1","test-model","never-expose-key");
  when(ai.runtime()).thenReturn(runtime);
  when(ai.complete(eq(runtime),anyList(),eq(1200))).thenReturn("{\"choices\":[{\"message\":{\"content\":\"人工智能\"}}]}");
  mvc.perform(request("Artificial intelligence")).andExpect(status().isOk()).andExpect(jsonPath("$.provider").value("大模型 API"));
  verify(ai).complete(eq(runtime),argThat(messages->messages.get(1).get("content").equals("Artificial intelligence")),eq(1200));
  when(ai.complete(eq(runtime),anyList(),eq(1200))).thenThrow(new java.io.IOException("never-expose-key"));
  var response=mvc.perform(request("First sentence.")).andExpect(status().isBadGateway()).andReturn().getResponse().getContentAsString();
  assertThat(response).doesNotContain("never-expose-key","api.example.com");
 }
 @Test void limitsRepeatedPublicRequestsWithoutCallingProviderAgain()throws Exception{
  var request=request("Artificial intelligence");request.header("X-Real-IP","192.0.2.7");
  for(int n=0;n<10;n++)mvc.perform(request).andExpect(status().isOk());
  mvc.perform(request).andExpect(status().isTooManyRequests());
 }
 @Test void concurrentSelectionsAreBoundedAndCapacityIsReleased()throws Exception{
  db.update("INSERT INTO news_translation_settings(id,mode,engine) VALUES(1,'api','bing')");
  when(ai.configured()).thenReturn(true);when(ai.runtime()).thenReturn(new NewsAiSettings.RuntimeConfig("https://api.example.com/v1","test","test-only"));
  var entered=new CountDownLatch(2);var release=new CountDownLatch(1);
  when(ai.complete(any(),anyList(),eq(1200))).thenAnswer(invocation->{entered.countDown();release.await(4,TimeUnit.SECONDS);return "{\"choices\":[{\"message\":{\"content\":\"译文\"}}]}";});
  try(var pool=Executors.newFixedThreadPool(2)){
   var first=pool.submit(()->mvc.perform(request("Artificial intelligence")).andExpect(status().isOk()));
   var second=pool.submit(()->mvc.perform(request("First sentence.")).andExpect(status().isOk()));
   try{assertThat(entered.await(3,TimeUnit.SECONDS)).isTrue();mvc.perform(request("Second sentence.")).andExpect(status().isTooManyRequests());}finally{release.countDown();}
   first.get(4,TimeUnit.SECONDS);second.get(4,TimeUnit.SECONDS);
  }
  mvc.perform(request("Second sentence.")).andExpect(status().isOk());
 }
}
