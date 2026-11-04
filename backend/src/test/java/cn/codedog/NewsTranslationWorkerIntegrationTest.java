package cn.codedog;
import cn.codedog.news.NewsService;
import cn.codedog.news.NewsCollectorSettings;
import cn.codedog.news.NewsTranslation;
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

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:newstransworker;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE","NEWS_AI_API_KEY=","NEWS_AI_BASE_URL=","NEWS_AI_MODEL="})
@org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class NewsTranslationWorkerIntegrationTest {
 static final AtomicReference<String> response=new AtomicReference<>();
 static final AtomicReference<java.util.concurrent.CountDownLatch> block=new AtomicReference<>();
 static final AtomicReference<java.util.concurrent.CountDownLatch> entered=new AtomicReference<>();
 static final AtomicReference<String> requestBody=new AtomicReference<>();
 static volatile boolean streaming=false;
 static HttpServer server;
 static {try{server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);server.createContext("/translate",exchange->{
  String input=new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8);
  requestBody.set(input);
  if(streaming){
   exchange.getResponseHeaders().set("Content-Type","application/x-ndjson");exchange.sendResponseHeaders(200,0);
   try(var out=exchange.getResponseBody()){
    out.write("{\"index\":0,\"text\":\"中文标题\"}\n{\"index\":1,\"text\":\"第一段\"}\n".getBytes(StandardCharsets.UTF_8));out.flush();entered.get().countDown();
    try{block.get().await(5,java.util.concurrent.TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}
    out.write("{\"index\":2,\"text\":null}\n{\"complete\":true,\"success\":false}\n".getBytes(StandardCharsets.UTF_8));
   }return;
  }
  if(block.get()!=null){entered.get().countDown();try{block.get().await(5,java.util.concurrent.TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}}
  assertThat(input).contains("paragraphs").doesNotContain("summary","api_key");
  byte[] bytes=response.get().getBytes(StandardCharsets.UTF_8);exchange.getResponseHeaders().set("Content-Type","application/json");exchange.sendResponseHeaders(200,bytes.length);try(var out=exchange.getResponseBody()){out.write(bytes);}
 });
 server.createContext("/translate-selection",exchange->{
  String input=new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8);
  assertThat(input).contains("Original headline unchanged").doesNotContain("First paragraph","Second paragraph");
  byte[] bytes="{\"paragraphs\":[\"仅标题译文\"]}".getBytes(StandardCharsets.UTF_8);
  exchange.getResponseHeaders().set("Content-Type","application/json");exchange.sendResponseHeaders(200,bytes.length);
  try(var out=exchange.getResponseBody()){out.write(bytes);}
 });
 server.start();}catch(Exception e){throw new RuntimeException(e);}}
 @DynamicPropertySource static void properties(DynamicPropertyRegistry registry){registry.add("NEWS_TRANSLATOR_URL",()->"http://127.0.0.1:"+server.getAddress().getPort());}
 @MockBean NewsAiSettings ai;
 @MockBean NewsCollectorSettings collector;
 @MockBean cn.codedog.news.NewsFastCollector fastCollector;
 @Autowired org.springframework.test.web.servlet.MockMvc mvc;
 @Autowired NewsTranslation translation;
 @Autowired JdbcTemplate db;@Autowired DataSource ds;@Autowired NewsService news;
 @BeforeAll void schema()throws Exception{new ResourceDatabasePopulator(new ClassPathResource("db/migration/V132__lens_news.sql"),new ClassPathResource("db/migration/V133__lens_ai_settings.sql"),new ClassPathResource("db/migration/V134__lens_translation_mode.sql"),new ClassPathResource("db/migration/V135__lens_collector_settings.sql"),new ClassPathResource("db/migration/V136__lens_pipeline_speed.sql"),new ClassPathResource("db/migration/V137__lens_on_demand_translation.sql")).execute(ds);try(var connection=ds.getConnection()){new cn.codedog.news.migration.V138__news_namespace(new cn.codedog.service.PasswordRecoveryCipher("")).migrateConnection(connection);}new ResourceDatabasePopulator(new ClassPathResource("db/migration/V139__news_taxonomy.sql"),new ClassPathResource("db/migration/V140__remove_news_secondary_categories.sql")).execute(ds);}
 @BeforeEach void setup(){streaming=false;block.set(null);entered.set(null);when(collector.ready()).thenReturn(true);db.update("DELETE FROM news_refresh_runs");db.update("DELETE FROM news_translation_cache");db.update("DELETE FROM news_articles");db.update("DELETE FROM news_batches");db.update("DELETE FROM news_translation_settings");db.update("DELETE FROM news_ai_settings");
  db.update("INSERT INTO news_articles(url_hash,origin_url,source_id,source_name,category,title_en,content_en,published_at,body_requested_at) VALUES('test','https://example.com/test',1,'Test source','科技','Original headline unchanged','First paragraph.\n\nSecond paragraph.',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");}
 @AfterAll void close(){server.stop(0);}
 @Test void defaultTranslationPublishesTranslatedTitleAndAlignedBodyWithoutSummary(){
  response.set("{\"paragraphs\":[\"中文新闻标题\",\"第一段。\",\"第二段。\"]}");news.process();
  var article=db.queryForMap("SELECT * FROM news_articles");assertThat(article.get("status")).isEqualTo("ready");assertThat(article.get("title_zh")).isEqualTo("中文新闻标题");assertThat(article.get("title_en")).isEqualTo("Original headline unchanged");assertThat(article.get("summary_zh")).isEqualTo("");assertThat(article.get("content_zh")).isEqualTo("第一段。\n\n第二段。");
 }
 @Test void apiModeTranslatesTitleAndBodyWithoutSummary()throws Exception{
  db.update("INSERT INTO news_translation_settings(id,mode,engine) VALUES(1,'api','bing')");
  when(ai.configured()).thenReturn(true);
  var config=new NewsAiSettings.RuntimeConfig("https://api.example.com/v1","test","test-only");
  when(ai.runtime()).thenReturn(config);
  when(ai.complete(eq(config),anyList(),isNull())).thenReturn("{\"choices\":[{\"message\":{\"content\":\"{\\\"paragraphs\\\":[{\\\"id\\\":0,\\\"text\\\":\\\"中文新闻标题\\\"},{\\\"id\\\":1,\\\"text\\\":\\\"第一段\\\"},{\\\"id\\\":2,\\\"text\\\":\\\"第二段\\\"}]}\"}}]}");
  news.process();
  var article=db.queryForMap("SELECT * FROM news_articles");assertThat(article.get("status")).isEqualTo("ready");assertThat(article.get("summary_zh")).isEqualTo("");assertThat(article.get("title_zh")).isEqualTo("中文新闻标题");assertThat(article.get("title_en")).isEqualTo("Original headline unchanged");
  verify(ai,times(1)).complete(eq(config),argThat(messages->messages.get(1).get("content").contains("First paragraph.")&&messages.get(1).get("content").contains("Original headline")),isNull());
 }
 @Test void misalignedOrEmptyResponseNeverPublishesAndKeepsOriginalForRetry(){
  response.set("{\"paragraphs\":[\"合并的一段\"]}");news.process();
  var article=db.queryForMap("SELECT * FROM news_articles");assertThat(article.get("status")).isEqualTo("pending");assertThat(article.get("attempts")).isEqualTo(1);assertThat(article.get("content_zh")).isNull();assertThat(article.get("content_en")).isEqualTo("First paragraph.\n\nSecond paragraph.");
  db.update("UPDATE news_articles SET next_attempt_at=NULL");response.set("{\"paragraphs\":[\"中文新闻标题\",\"第一段\",\"\"]}");news.process();assertThat(db.queryForObject("SELECT status FROM news_articles",String.class)).isEqualTo("pending");assertThat(db.queryForObject("SELECT attempts FROM news_articles",Integer.class)).isEqualTo(2);
 }
 @Test void blockedTranslationDoesNotBlockConcurrentRefreshOrBatchIngestion()throws Exception{
  response.set("{\"paragraphs\":[\"中文标题\",\"第一段\",\"第二段\"]}");
  var release=new java.util.concurrent.CountDownLatch(1);block.set(release);entered.set(new java.util.concurrent.CountDownLatch(1));
  try(var pool=java.util.concurrent.Executors.newFixedThreadPool(10)){
   var worker=pool.submit(()->news.translateNext());
   try{
    assertThat(entered.get().await(2,java.util.concurrent.TimeUnit.SECONDS)).isTrue();
    long started=System.nanoTime();var futures=new java.util.ArrayList<java.util.concurrent.Future<Map<String,Object>>>();
    for(int n=0;n<8;n++)futures.add(pool.submit(()->news.refresh()));
    var ids=new java.util.HashSet<Object>();for(var f:futures)ids.add(f.get(900,java.util.concurrent.TimeUnit.MILLISECONDS).get("run_id"));
    assertThat(java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-started)).isLessThan(1000);
    assertThat(ids).hasSize(1);assertThat(db.queryForObject("SELECT COUNT(*) FROM news_refresh_runs",Long.class)).isEqualTo(1);
    verify(collector,never()).dispatch(anyLong());
    news.accept("{\"batch_id\":\"parallel-ingest-test\",\"articles\":[],\"source_results\":[]}".getBytes(StandardCharsets.UTF_8));
    news.ingestBatches();assertThat(db.queryForObject("SELECT status FROM news_batches",String.class)).isEqualTo("done");
   }finally{release.countDown();}
   worker.get(3,java.util.concurrent.TimeUnit.SECONDS);
  }
  news.dispatchQueued();news.dispatchQueued();verify(collector,times(1)).dispatch(anyLong());
  assertThat(db.queryForObject("SELECT status FROM news_refresh_runs",String.class)).isEqualTo("dispatched");
 }
 @Test void completedParagraphsAreCachedAndOnlyMissingParagraphIsRetried()throws Exception{
  response.set("{\"paragraphs\":[\"中文标题\",\"第一段\",null]}");news.process();
  assertThat(db.queryForObject("SELECT status FROM news_articles",String.class)).isEqualTo("pending");
  assertThat(db.queryForObject("SELECT COUNT(*) FROM news_translation_cache",Long.class)).isEqualTo(2);
  db.update("UPDATE news_articles SET next_attempt_at=NULL");
  response.set("{\"paragraphs\":[\"第二段\"]}");news.process();
  assertThat(requestBody.get()).doesNotContain("Original headline","First paragraph").contains("Second paragraph");
  var article=db.queryForMap("SELECT * FROM news_articles");
  assertThat(article.get("status")).isEqualTo("ready");assertThat(article.get("title_zh")).isEqualTo("中文标题");
  assertThat(article.get("content_zh")).isEqualTo("第一段\n\n第二段");
  response.set("{\"paragraphs\":[\"新引擎译文\"]}");
  assertThat(translation.translate(new NewsTranslation.Config("translators","alibaba"),List.of("First paragraph."))).containsExactly("新引擎译文");
  assertThat(requestBody.get()).contains("alibaba","First paragraph.");
 }
 @Test void expiredAmbiguousDispatchIsNotRetried()throws Exception{
  news.refresh();db.update("UPDATE news_refresh_runs SET status='dispatching',lease_until=?",java.sql.Timestamp.from(java.time.Instant.now().minusSeconds(1)));
  news.dispatchQueued();assertThat(db.queryForObject("SELECT status FROM news_refresh_runs",String.class)).isEqualTo("uncertain");
  verify(collector,never()).dispatch(anyLong());
 }
 @Test void failedDispatchIsRecordedWithoutLeakingUpstreamErrors()throws Exception{
  news.refresh();doThrow(new RuntimeException("unsafe-provider-response")).when(collector).dispatch(anyLong());news.dispatchQueued();
  var status=news.syncStatus(null);assertThat(status.get("phase")).isEqualTo("uncertain");assertThat(status.toString()).doesNotContain("unsafe-provider-response");
 }
 @Test void expiredBatchLeaseIsRecoveredAndOnlyTenBatchesAreClaimed()throws Exception{
  for(int i=0;i<11;i++)news.accept(("{\"batch_id\":\"recovery-batch-"+i+"\",\"articles\":[],\"source_results\":[]}").getBytes(StandardCharsets.UTF_8));
  db.update("UPDATE news_batches SET status='processing',lease_until=?",java.sql.Timestamp.from(java.time.Instant.now().minusSeconds(1)));
  news.ingestBatches();
  assertThat(db.queryForObject("SELECT COUNT(*) FROM news_batches WHERE status='done'",Long.class)).isEqualTo(10);
  news.ingestBatches();assertThat(db.queryForObject("SELECT COUNT(*) FROM news_batches WHERE status='done'",Long.class)).isEqualTo(11);
 }

 @Test void firstBatchDoesNotFinishCollectionAndCompletionIsMonotonic()throws Exception{
  db.update("DELETE FROM news_articles");
  long id=((Number)news.refresh().get("run_id")).longValue();news.dispatchQueued();
  news.accept(("{\"batch_id\":\"first-run-batch\",\"run_id\":"+id+",\"articles\":[],\"source_results\":[]}").getBytes(StandardCharsets.UTF_8));
  news.ingestBatches();assertThat(news.syncStatus(id).get("phase")).isEqualTo("crawling");
  news.accept(("{\"batch_id\":\"final-run-batch\",\"run_id\":"+id+",\"collection_finished\":true,\"articles\":[],\"source_results\":[]}").getBytes(StandardCharsets.UTF_8));
  news.ingestBatches();assertThat(news.syncStatus(id).get("phase")).isEqualTo("idle");
  news.accept(("{\"batch_id\":\"late-run-batch\",\"run_id\":"+id+",\"articles\":[],\"source_results\":[]}").getBytes(StandardCharsets.UTF_8));
  news.ingestBatches();assertThat(news.syncStatus(id).get("phase")).isEqualTo("idle");
 }

 @Test void streamingWritesSuccessfulParagraphsBeforeArticleFinishes()throws Exception{
  streaming=true;var release=new java.util.concurrent.CountDownLatch(1);block.set(release);entered.set(new java.util.concurrent.CountDownLatch(1));
  try(var pool=java.util.concurrent.Executors.newSingleThreadExecutor()){
   var worker=pool.submit(()->news.translateNext());
   try{
    assertThat(entered.get().await(2,java.util.concurrent.TimeUnit.SECONDS)).isTrue();
    for(int n=0;n<100&&db.queryForObject("SELECT COUNT(*) FROM news_translation_cache",Long.class)<2;n++)Thread.sleep(10);
    assertThat(db.queryForObject("SELECT COUNT(*) FROM news_translation_cache",Long.class)).isEqualTo(2);
    assertThat(worker.isDone()).isFalse();
   }finally{release.countDown();}
   worker.get(3,java.util.concurrent.TimeUnit.SECONDS);
  }
  assertThat(db.queryForObject("SELECT status FROM news_articles",String.class)).isEqualTo("pending");
  streaming=false;block.set(null);db.update("UPDATE news_articles SET next_attempt_at=NULL");
  response.set("{\"paragraphs\":[\"第二段\"]}");news.translateNext();
  assertThat(db.queryForObject("SELECT status FROM news_articles",String.class)).isEqualTo("ready");
  assertThat(requestBody.get()).doesNotContain("Original headline","First paragraph").contains("Second paragraph");
 }

 @Test void unopenedArticleTranslatesOnlyTitleAndListReadDoesNotQueueBody()throws Exception{
  db.update("UPDATE news_articles SET body_requested_at=NULL");
  requestBody.set(null);news.translateTitlesNext();news.translateNext();
  var a=db.queryForMap("SELECT * FROM news_articles");
  assertThat(a.get("title_zh")).isEqualTo("仅标题译文");
  assertThat(a.get("content_zh")).isNull();assertThat(a.get("body_requested_at")).isNull();assertThat(requestBody.get()).isNull();
  assertThat(news.list("","","",0).get("total")).isEqualTo(1L);
  long id=((Number)a.get("id")).longValue();
  assertThat(news.article(id,false).get("translation_status")).isEqualTo("not_requested");
  assertThat(db.queryForObject("SELECT body_requested_at FROM news_articles",java.sql.Timestamp.class)).isNull();
  // Opening is a CSRF-protected, idempotent public queue mutation.
  mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/news/"+id+"/translation"))
   .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isForbidden());
  mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/news/"+id+"/translation").with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf()))
   .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());
  var first=db.queryForObject("SELECT body_requested_at FROM news_articles",java.sql.Timestamp.class);
  try(var pool=java.util.concurrent.Executors.newFixedThreadPool(8)){
   var futures=new java.util.ArrayList<java.util.concurrent.Future<?>>();for(int n=0;n<16;n++)futures.add(pool.submit(()->news.requestBodyTranslation(id)));
   for(var f:futures)f.get(2,java.util.concurrent.TimeUnit.SECONDS);
  }
  assertThat(db.queryForObject("SELECT body_requested_at FROM news_articles",java.sql.Timestamp.class)).isEqualTo(first);
  response.set("{\"paragraphs\":[\"第一段\",\"第二段\"]}");news.translateNext();
  assertThat(requestBody.get()).doesNotContain("Original headline").contains("First paragraph","Second paragraph");
  assertThat(news.article(id,false).get("translation_status")).isEqualTo("ready");
  var cached=db.queryForObject("SELECT translated_at FROM news_articles",java.sql.Timestamp.class);
  news.requestBodyTranslation(id);news.translateNext();
  assertThat(db.queryForObject("SELECT translated_at FROM news_articles",java.sql.Timestamp.class)).isEqualTo(cached);
  assertThat(db.queryForObject("SELECT COUNT(*) FROM audit_log WHERE event_code='body_translation_request' AND target_id=?",Long.class,String.valueOf(id))).isPositive();
 }
 @Test void apiTitleTranslationDoesNotSendBodyUntilOpened()throws Exception{
  db.update("UPDATE news_articles SET body_requested_at=NULL");
  db.update("INSERT INTO news_translation_settings(id,mode,engine) VALUES(1,'api','bing')");
  when(ai.configured()).thenReturn(true);
  var config=new NewsAiSettings.RuntimeConfig("https://api.example.com/v1","test-title","test-only");
  when(ai.runtime()).thenReturn(config);
  when(ai.complete(eq(config),anyList(),eq(1200))).thenReturn("{\"choices\":[{\"message\":{\"content\":\"API 标题\"}}]}");
  news.translateTitlesNext();news.translateNext();
  assertThat(db.queryForObject("SELECT title_zh FROM news_articles",String.class)).isEqualTo("API 标题");
  assertThat(db.queryForObject("SELECT content_zh FROM news_articles",String.class)).isNull();
  verify(ai).complete(eq(config),argThat(messages->messages.get(1).get("content").equals("Original headline unchanged")),eq(1200));
  verify(ai,never()).complete(any(),anyList(),isNull());
 }
 @Test void bodyFailureKeepsTranslatedTitleVisibleAndExpiredLeaseResumes()throws Exception{
  db.update("UPDATE news_articles SET title_zh='标题已翻译',status='processing',next_attempt_at=?",java.sql.Timestamp.from(java.time.Instant.now().minusSeconds(1)));
  response.set("{\"paragraphs\":[\"错误合并译文\"]}");news.translateNext();
  assertThat(news.list("","","",0).get("total")).isEqualTo(1L);
  assertThat(db.queryForObject("SELECT content_zh FROM news_articles",String.class)).isNull();
  db.update("UPDATE news_articles SET next_attempt_at=NULL");
  response.set("{\"paragraphs\":[\"标题已翻译\",\"第一段\",\"第二段\"]}");news.translateNext();
  assertThat(db.queryForObject("SELECT status FROM news_articles",String.class)).isEqualTo("ready");
 }

 @Test void fastCollectionAvoidsGithubAndRetainsDispatchAudit()throws Exception{
  when(fastCollector.collect(anyLong(),any())).thenReturn(true);
  long id=((Number)news.refresh().get("run_id")).longValue();news.dispatchQueued();
  verify(collector,never()).dispatch(anyLong());
  assertThat(news.syncStatus(id).get("status")).isEqualTo("dispatched");
  assertThat(db.queryForObject("SELECT COUNT(*) FROM audit_log WHERE event_code='news_dispatch_completed' AND target_id=?",Long.class,String.valueOf(id))).isPositive();
 }
 @Test void apiTitlesRunConcurrentlyAndCommitIndependently()throws Exception{
  db.update("UPDATE news_articles SET body_requested_at=NULL");
  db.update("INSERT INTO news_translation_settings(id,mode,engine) VALUES(1,'api','bing')");
  when(ai.configured()).thenReturn(true);
  var config=new NewsAiSettings.RuntimeConfig("https://api.example.com/v1","parallel-test","test-only");
  when(ai.runtime()).thenReturn(config);
  db.update("INSERT INTO news_articles(url_hash,origin_url,source_id,source_name,category,title_en,content_en,published_at) SELECT 'parallel-second','https://example.com/second',source_id,source_name,category,'Second headline','English body',published_at FROM news_articles LIMIT 1");
  var entered=new java.util.concurrent.CountDownLatch(2);var release=new java.util.concurrent.CountDownLatch(1);
  when(ai.complete(eq(config),anyList(),eq(1200))).thenAnswer(call->{entered.countDown();assertThat(release.await(3,java.util.concurrent.TimeUnit.SECONDS)).isTrue();return "{\"choices\":[{\"message\":{\"content\":\"并行标题\"}}]}";});
  try(var pool=java.util.concurrent.Executors.newFixedThreadPool(2)){
   var first=pool.submit(()->news.translateTitlesNext());
   for(int n=0;n<100&&db.queryForObject("SELECT COUNT(*) FROM news_articles WHERE title_lease_until IS NOT NULL",Long.class)==0;n++)Thread.sleep(10);
   var second=pool.submit(()->news.translateTitlesNext());
   try{assertThat(entered.await(2,java.util.concurrent.TimeUnit.SECONDS)).isTrue();}finally{release.countDown();}
   first.get(3,java.util.concurrent.TimeUnit.SECONDS);second.get(3,java.util.concurrent.TimeUnit.SECONDS);
  }
  assertThat(db.queryForObject("SELECT COUNT(*) FROM news_articles WHERE title_zh='并行标题' AND content_zh IS NULL",Long.class)).isEqualTo(2);
 }

}
