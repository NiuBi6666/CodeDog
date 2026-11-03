package cn.codedog;
import cn.codedog.news.NewsService;
import cn.codedog.news.NewsAiSettings;
import org.springframework.boot.test.mock.mockito.MockBean;
import static org.mockito.Mockito.*;
import java.util.List;
import java.util.Map;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import javax.sql.DataSource;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:lenstransworker;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE","NEWS_AI_API_KEY=","NEWS_AI_BASE_URL=","NEWS_AI_MODEL="})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class NewsTranslationWorkerIntegrationTest {
 static final AtomicReference<String> response=new AtomicReference<>();
 static HttpServer server;
 static {try{server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);server.createContext("/translate",exchange->{
  String input=new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8);
  assertThat(input).contains("paragraphs").doesNotContain("summary","api_key");
  byte[] bytes=response.get().getBytes(StandardCharsets.UTF_8);exchange.getResponseHeaders().set("Content-Type","application/json");exchange.sendResponseHeaders(200,bytes.length);try(var out=exchange.getResponseBody()){out.write(bytes);}
 });server.start();}catch(Exception e){throw new RuntimeException(e);}}
 @DynamicPropertySource static void properties(DynamicPropertyRegistry registry){registry.add("NEWS_TRANSLATOR_URL",()->"http://127.0.0.1:"+server.getAddress().getPort());}
 @MockBean NewsAiSettings ai;
 @Autowired JdbcTemplate db;@Autowired DataSource ds;@Autowired NewsService news;
 @BeforeAll void schema(){new ResourceDatabasePopulator(new ClassPathResource("db/migration/V132__lens_news.sql"),new ClassPathResource("db/migration/V133__lens_ai_settings.sql"),new ClassPathResource("db/migration/V134__lens_translation_mode.sql")).execute(ds);}
 @BeforeEach void setup(){db.update("DELETE FROM lens_articles");db.update("DELETE FROM lens_batches");db.update("DELETE FROM lens_translation_settings");db.update("DELETE FROM lens_ai_settings");
  db.update("INSERT INTO lens_articles(url_hash,origin_url,source_id,source_name,category,title_en,content_en,published_at) VALUES('test','https://example.com/test',1,'Test source','科技','Original headline unchanged','First paragraph.\n\nSecond paragraph.',CURRENT_TIMESTAMP)");}
 @AfterAll void close(){server.stop(0);}
 @Test void defaultTranslationPublishesOriginalTitleAndAlignedBodyWithoutSummary(){
  response.set("{\"paragraphs\":[\"第一段。\",\"第二段。\"]}");news.process();
  var article=db.queryForMap("SELECT * FROM lens_articles");assertThat(article.get("status")).isEqualTo("ready");assertThat(article.get("title_zh")).isEqualTo(article.get("title_en"));assertThat(article.get("summary_zh")).isEqualTo("");assertThat(article.get("content_zh")).isEqualTo("第一段。\n\n第二段。");
 }
 @Test void apiModeOnlyTranslatesBodyAndMakesNoSummaryOrTitleRequest()throws Exception{
  db.update("INSERT INTO lens_translation_settings(id,mode,engine) VALUES(1,'api','bing')");
  when(ai.configured()).thenReturn(true);
  var config=new NewsAiSettings.RuntimeConfig("https://api.example.com/v1","test","test-only");
  when(ai.runtime()).thenReturn(config);
  when(ai.complete(eq(config),anyList(),isNull())).thenReturn("{\"choices\":[{\"message\":{\"content\":\"{\\\"paragraphs\\\":[{\\\"id\\\":0,\\\"text\\\":\\\"第一段\\\"},{\\\"id\\\":1,\\\"text\\\":\\\"第二段\\\"}]}\"}}]}");
  news.process();
  var article=db.queryForMap("SELECT * FROM lens_articles");assertThat(article.get("status")).isEqualTo("ready");assertThat(article.get("summary_zh")).isEqualTo("");assertThat(article.get("title_zh")).isEqualTo(article.get("title_en"));
  verify(ai,times(1)).complete(eq(config),argThat(messages->messages.get(1).get("content").contains("First paragraph.")&&!messages.get(1).get("content").contains("Original headline")),isNull());
 }
 @Test void misalignedOrEmptyResponseNeverPublishesAndKeepsOriginalForRetry(){
  response.set("{\"paragraphs\":[\"合并的一段\"]}");news.process();
  var article=db.queryForMap("SELECT * FROM lens_articles");assertThat(article.get("status")).isEqualTo("pending");assertThat(article.get("attempts")).isEqualTo(1);assertThat(article.get("content_zh")).isNull();assertThat(article.get("content_en")).isEqualTo("First paragraph.\n\nSecond paragraph.");
  db.update("UPDATE lens_articles SET next_attempt_at=NULL");response.set("{\"paragraphs\":[\"第一段\",\"\"]}");news.process();assertThat(db.queryForObject("SELECT status FROM lens_articles",String.class)).isEqualTo("pending");assertThat(db.queryForObject("SELECT attempts FROM lens_articles",Integer.class)).isEqualTo(2);
 }
}
