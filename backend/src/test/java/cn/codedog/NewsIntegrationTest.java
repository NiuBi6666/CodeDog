
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
 @Autowired cn.codedog.news.NewsEventService events;@Autowired ObjectMapper json;@Autowired NewsService news;@Autowired UserRepository users;@Autowired PasswordEncoder encoder;
 static final String SECRET="test-news-secret-at-least-32-characters";
 @BeforeAll void schema()throws Exception{new ResourceDatabasePopulator(new ClassPathResource("db/migration/V132__lens_news.sql"),new ClassPathResource("db/migration/V133__lens_ai_settings.sql"),new ClassPathResource("db/migration/V134__lens_translation_mode.sql"),new ClassPathResource("db/migration/V135__lens_collector_settings.sql"),new ClassPathResource("db/migration/V136__lens_pipeline_speed.sql"),new ClassPathResource("db/migration/V137__lens_on_demand_translation.sql")).execute(ds);try(var connection=ds.getConnection()){new cn.codedog.news.migration.V138__news_namespace(new cn.codedog.service.PasswordRecoveryCipher("")).migrateConnection(connection);}new ResourceDatabasePopulator(new ClassPathResource("db/migration/V139__news_taxonomy.sql"),new ClassPathResource("db/migration/V140__remove_news_secondary_categories.sql"),new ClassPathResource("db/migration/V141__news_event_tracking.sql")).execute(ds);}
 @BeforeEach void clean(){db.update("DELETE FROM news_event_articles");db.update("DELETE FROM news_event_nodes");db.update("DELETE FROM news_events");db.update("DELETE FROM news_event_scanned_articles");db.update("DELETE FROM news_translation_settings");db.update("INSERT INTO news_translation_settings(id,mode,engine) VALUES(1,'api','bing')");db.update("DELETE FROM news_translation_cache");db.update("DELETE FROM news_articles");db.update("DELETE FROM news_batches");db.update("UPDATE news_sources SET lease_until=NULL,last_crawled_at=NULL");}
 MockHttpSession login(String name,String password)throws Exception{return (MockHttpSession)mvc.perform(post("/api/auth/login").with(csrf()).contentType(APPLICATION_JSON).content(json.writeValueAsString(Map.of("username",name,"password",password)))).andExpect(status().isOk()).andReturn().getRequest().getSession(false);}
 @Test void historicalArticleResponseOmitsFooterAndKeepsStoredOriginal()throws Exception{
  String original="正文事实。\n\n经济新闻精选：\n\n- 2026年11月04日 11:06:43\n\n- 2026年11月03日 17:12:22";
  db.update("INSERT INTO news_articles(source_id,url_hash,origin_url,source_name,category,title_en,title_zh,content_en,content_zh,status,lang,published_at) VALUES((SELECT MIN(id) FROM news_sources),?,?,?,?,?,?,?,?,?,?,CURRENT_TIMESTAMP)",NewsService.digest("https://example.com/footer"),"https://example.com/footer","test","国际","标题","标题",original,original,"ready","zh");
  long id=db.queryForObject("SELECT id FROM news_articles WHERE origin_url='https://example.com/footer'",Long.class);
  mvc.perform(get("/api/news/"+id)).andExpect(status().isOk()).andExpect(jsonPath("$.content_en").value("正文事实。")).andExpect(jsonPath("$.content_zh").value("正文事实。")).andExpect(jsonPath("$.content_original").value("正文事实。"));
  assertThat(db.queryForObject("SELECT content_en FROM news_articles WHERE id=?",String.class,id)).isEqualTo(original);
 }
 @Test void publicReadOnlyAndMissingConfigurationAreHonest()throws Exception{
  mvc.perform(get("/api/news/sync-status")).andExpect(status().isOk()).andExpect(jsonPath("$.pending_articles").value(0)).andExpect(jsonPath("$.phase").value("idle"));
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
  mvc.perform(patch("/api/admin/sources/"+id).session(session).with(csrf()).contentType(APPLICATION_JSON).content("{\"refresh_interval_min\":10}")).andExpect(status().isOk()).andExpect(jsonPath("$.refresh_interval_min").value(10));
  mvc.perform(patch("/api/admin/sources/"+id).session(session).with(csrf()).contentType(APPLICATION_JSON).content("{\"refresh_interval_min\":9}")).andExpect(status().isBadRequest());

  mvc.perform(patch("/api/admin/sources/"+id).session(session).with(csrf()).contentType(APPLICATION_JSON).content("{\"is_active\":false}")).andExpect(status().isOk()).andExpect(jsonPath("$.is_active").value(false));
  assertThat(db.queryForObject("SELECT COUNT(*) FROM audit_log WHERE module='news' AND event_code='source_update'",Long.class)).isPositive();

  for(String category:List.of("国际","军事","科技","财经","教育","文娱","生活")){
   mvc.perform(patch("/api/admin/sources/"+id).session(session).with(csrf()).contentType(APPLICATION_JSON).content(json.writeValueAsString(Map.of("category",category,"lang","zh")))).andExpect(status().isOk()).andExpect(jsonPath("$.category").value(category));
  }
  mvc.perform(patch("/api/admin/sources/"+id).session(session).with(csrf()).contentType(APPLICATION_JSON).content(json.writeValueAsString(Map.of("category","未知分类")))).andExpect(status().isBadRequest());
  mvc.perform(delete("/api/admin/sources/"+id).session(session).with(csrf())).andExpect(status().isOk());
  u.setPermissions(Set.of());users.saveAndFlush(u);
  mvc.perform(patch("/api/admin/sources/1").session(session).with(csrf()).contentType(APPLICATION_JSON).content("{\"refresh_interval_min\":10}")).andExpect(status().isForbidden());
  mvc.perform(get("/api/admin/news").session(session)).andExpect(status().isForbidden());
  mvc.perform(post("/api/admin/sources").session(session).with(csrf()).contentType(APPLICATION_JSON).content("{}")).andExpect(status().isForbidden());
 }


 @Test void cursorPagingKeepsDescendingOrderWithoutRepeatsWhenNewHeadArrives()throws Exception{
  long source=db.queryForObject("SELECT id FROM news_sources LIMIT 1",Long.class);
  var time=java.sql.Timestamp.from(Instant.parse("2026-01-01T12:00:00.123456Z"));
  for(int i=0;i<65;i++)db.update("INSERT INTO news_articles(url_hash,origin_url,source_id,source_name,category,title_en,title_zh,content_en,published_at) VALUES(?,?,?,?,?,?,?,?,?)","cursor-"+i,"https://example.com/cursor/"+i,source,"cursor-source","科技","Title "+i,"标题 "+i,"Original body",time);
  var first=news.list("","科技","cursor-source",0,"");
  var rows=(List<Map<String,Object>>)first.get("articles");
  assertThat(rows).hasSize(30);assertThat(first.get("has_more")).isEqualTo(true);
  var ids=new LinkedHashSet<Long>();long previous=Long.MAX_VALUE;
  for(var row:rows){long id=((Number)row.get("id")).longValue();assertThat(id).isLessThan(previous);ids.add(id);previous=id;}
  db.update("INSERT INTO news_articles(url_hash,origin_url,source_id,source_name,category,title_en,title_zh,content_en,published_at) VALUES('cursor-new','https://example.com/cursor/new',?,'cursor-source','科技','Newest title','最新标题','Original',?)",source,java.sql.Timestamp.from(time.toInstant().plusSeconds(60)));
  String cursor=(String)first.get("next_cursor");
  while(!cursor.isEmpty()){
   var next=news.list("","科技","cursor-source",0,cursor);
   for(var row:(List<Map<String,Object>>)next.get("articles")){
    long id=((Number)row.get("id")).longValue();assertThat(id).isLessThan(previous);assertThat(ids.add(id)).isTrue();previous=id;
   }
   cursor=(String)next.get("next_cursor");
   if(cursor.isEmpty())assertThat(next.get("has_more")).isEqualTo(false);
  }
  assertThat(ids).hasSize(65);
  var refreshed=news.list("","科技","cursor-source",0,"");
  assertThat(((List<Map<String,Object>>)refreshed.get("articles")).getFirst().get("title_en")).isEqualTo("Newest title");
  mvc.perform(get("/api/news").param("cursor","invalid-cursor")).andExpect(status().isBadRequest());
  String encoded=Base64.getUrlEncoder().withoutPadding().encodeToString("bad-date|1".getBytes(StandardCharsets.UTF_8));
  mvc.perform(get("/api/news").param("cursor",encoded)).andExpect(status().isBadRequest());
 }
 @Test void chineseOriginalPublishesWithoutTranslationAndOnlyPrimaryCategoriesAreReturned()throws Exception{
  db.update("INSERT INTO news_sources(source_name,rss_url,category,lang) VALUES('taxonomy-zh','https://example.com/zh-feed','生活','zh')");
  long source=db.queryForObject("SELECT id FROM news_sources WHERE source_name='taxonomy-zh'",Long.class);
  try{
   String title="中文原稿标题",text="这是保留来源原文的中文医学新闻，不应调用英文翻译服务。".repeat(20);
   byte[] body=json.writeValueAsBytes(Map.of("batch_id","taxonomy-chinese-batch","articles",List.of(Map.of("source_id",source,"origin_url","https://example.com/chinese-health","title_en",title,"lang","en","paragraphs_en",List.of(text),"published_at",Instant.now().toString()))));
   news.accept(body);news.ingestBatches();
   long id=db.queryForObject("SELECT id FROM news_articles WHERE source_id=?",Long.class,source);
   var article=news.article(id,false);
   assertThat(article.get("lang")).isEqualTo("zh");
   assertThat(article).doesNotContainKey("sub_tag");
   assertThat(news.source(source)).doesNotContainKey("sub_tag");
   assertThat(article.get("translation_status")).isEqualTo("ready");
   assertThat(article.get("title_original")).isEqualTo(title);
   assertThat(article.get("content_original")).isEqualTo(text);
   assertThat(article.get("content_zh")).isEqualTo(text);
   mvc.perform(get("/api/news").param("category","生活")).andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1)).andExpect(jsonPath("$.articles[0].lang").value("zh"));
   mvc.perform(get("/api/news").param("category","生活")).andExpect(jsonPath("$.sub_tags").doesNotExist()).andExpect(jsonPath("$.articles[0].sub_tag").doesNotExist());
   mvc.perform(post("/api/news/"+id+"/translation").with(csrf()).contentType(APPLICATION_JSON).content("{}")).andExpect(status().isOk()).andExpect(jsonPath("$.translation_status").value("ready"));
   assertThat(db.queryForObject("SELECT COUNT(*) FROM news_articles WHERE body_requested_at IS NOT NULL",Long.class)).isZero();
   assertThat(db.queryForObject("SELECT COUNT(*) FROM news_translation_cache",Long.class)).isZero();
  }finally{db.update("DELETE FROM news_sources WHERE id=?",source);}
 }
 @Test void webhookIsSignedDurableIdempotentAndDeduplicated()throws Exception{
  long source=((Number)db.queryForMap("SELECT id FROM news_sources LIMIT 1").get("id")).longValue();
  String text="A public article about science and technology. ".repeat(40);
  byte[] body=json.writeValueAsBytes(Map.of("batch_id","signed-batch-123456","articles",List.of(Map.of("source_id",source,"origin_url","https://example.com/news?utm_source=rss","title_en","A real article","paragraphs_en",List.of(text),"published_at",Instant.now().toString())),"source_results",List.of(Map.of("source_id",source,"success",true))));
  String ts=String.valueOf(Instant.now().getEpochSecond());String sig=sign(body,ts);
  mvc.perform(post("/api/crawler/ingest").contentType(APPLICATION_JSON).content(body).header("X-Webhook-Secret",SECRET).header("X-Webhook-Timestamp",ts).header("X-Webhook-Signature","bad")).andExpect(status().isUnauthorized());
  for(int i=0;i<2;i++)mvc.perform(post("/api/crawler/ingest").contentType(APPLICATION_JSON).content(body).header("X-Webhook-Secret",SECRET).header("X-Webhook-Timestamp",ts).header("X-Webhook-Signature",sig)).andExpect(status().isOk());
  assertThat(db.queryForObject("SELECT COUNT(*) FROM news_batches",Long.class)).isEqualTo(1);
  news.process();assertThat(db.queryForObject("SELECT COUNT(*) FROM news_articles",Long.class)).isEqualTo(1);
  assertThat(db.queryForObject("SELECT status FROM news_articles LIMIT 1",String.class)).isEqualTo("pending");
  mvc.perform(get("/api/news")).andExpect(jsonPath("$.total").value(0));
  long id=db.queryForObject("SELECT id FROM news_articles LIMIT 1",Long.class);
  db.update("UPDATE news_articles SET status='ready',title_zh='测试标题',summary_zh='一\n二\n三',content_zh='正文' WHERE id=?",id);
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
 @Test void preflightRequiresCollectorSecretBoundsInputAndSkipsPendingArticles()throws Exception{
  mvc.perform(post("/api/crawler/filter").contentType(APPLICATION_JSON).content("{\"urls\":[]}")).andExpect(status().isUnauthorized());
  mvc.perform(post("/api/crawler/filter").header("X-Webhook-Secret","wrong").contentType(APPLICATION_JSON).content("{\"urls\":[]}")).andExpect(status().isUnauthorized());
  mvc.perform(get("/api/crawler/tasks?fast=true").header("X-Webhook-Secret",SECRET)).andExpect(status().isOk());
  assertThat(db.queryForObject("SELECT COUNT(*) FROM news_sources WHERE lease_until IS NOT NULL",Long.class)).isZero();
  String url="https://example.com/existing";
  db.update("INSERT INTO news_articles(url_hash,origin_url,source_id,source_name,category,title_en,content_en,published_at) VALUES(?,?,1,'Test','科技','Existing','Body',CURRENT_TIMESTAMP)",NewsService.digest(url),url);
  mvc.perform(post("/api/crawler/filter").header("X-Webhook-Secret",SECRET).contentType(APPLICATION_JSON).content(json.writeValueAsBytes(Map.of("urls",List.of(url+"?utm_source=rss",url,"https://example.com/new","https://example.com/new")))))
   .andExpect(status().isOk()).andExpect(jsonPath("$.urls.length()").value(1)).andExpect(jsonPath("$.urls[0]").value("https://example.com/new"));
  mvc.perform(post("/api/crawler/filter").header("X-Webhook-Secret",SECRET).contentType(APPLICATION_JSON).content(json.writeValueAsBytes(Map.of("urls",Collections.nCopies(101,url)))))
   .andExpect(status().isBadRequest());
  mvc.perform(post("/api/crawler/filter").header("X-Webhook-Secret",SECRET).contentType(APPLICATION_JSON).content("{\"urls\":[\"http://invalid.example\"]}")).andExpect(status().isBadRequest());
 }
 static String sign(byte[] bytes,String ts)throws Exception{Mac m=Mac.getInstance("HmacSHA256");m.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8),"HmacSHA256"));m.update((ts+".").getBytes(StandardCharsets.UTF_8));return HexFormat.of().formatHex(m.doFinal(bytes));}

 long eventArticle(String title,String date){
  String url="https://example.com/events/"+UUID.randomUUID();
  db.update("INSERT INTO news_articles(source_id,url_hash,origin_url,source_name,category,title_en,title_zh,content_en,content_zh,status,published_at) VALUES((SELECT MIN(id) FROM news_sources),?,?,'Event Test','科技',?,?,'Original paragraph','中文段落','ready',?)",NewsService.digest(url),url,title,"译文 "+title,java.sql.Timestamp.from(Instant.parse(date)));
  return db.queryForObject("SELECT id FROM news_articles WHERE origin_url=?",Long.class,url);
 }
 @Test void eventTimelineIsDraftUntilConfirmedPublishedAndDistinguishesTimeBasis()throws Exception{
  long a=eventArticle("Company Alpha announces satellite launch mission","2026-11-01T10:00:00Z"),b=eventArticle("Company Alpha completes satellite launch mission","2026-11-03T10:00:00Z");
  long id=((Number)events.create(json.readTree("{\"title\":\"卫星发射任务\",\"category\":\"科技\"}")).get("id")).longValue();
  mvc.perform(get("/api/news/events/"+id)).andExpect(status().isNotFound());
  assertThatThrownBy(()->events.publish(id,json.readTree("{\"status\":\"published\"}"))).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
  events.link(id,a,json.readTree("{\"stage\":\"background\"}"));
  events.link(id,b,json.readTree("{\"stage\":\"outcome\",\"occurred_at\":\"2026-10-31T08:00:00Z\"}"));
  events.publish(id,json.readTree("{\"status\":\"published\"}"));
  mvc.perform(get("/api/news/events/"+id)).andExpect(status().isOk()).andExpect(jsonPath("$.article_count").value(2)).andExpect(jsonPath("$.nodes[0].time_basis").value("occurred")).andExpect(jsonPath("$.nodes[0].reports[0].article_id").value(b)).andExpect(jsonPath("$.nodes[1].time_basis").value("published"));
  mvc.perform(get("/api/news/"+a)).andExpect(status().isOk()).andExpect(jsonPath("$.events[0].id").value(id));
  long second=((Number)events.create(json.readTree("{\"title\":\"另一个事件\",\"category\":\"科技\"}")).get("id")).longValue();
  events.link(second,a,json.readTree("{}"));
  assertThat(db.queryForObject("SELECT COUNT(*) FROM news_event_articles WHERE article_id=?",Long.class,a)).isEqualTo(2);
  long node=((Number)((List<Map<String,Object>>)events.event(id,true).get("nodes")).getFirst().get("id")).longValue();
  assertThatThrownBy(()->events.link(second,b,json.readTree("{\"node_id\":"+node+"}"))).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
  events.link(id,a,json.readTree("{\"state\":\"excluded\"}"));
  mvc.perform(get("/api/news/events/"+id)).andExpect(status().isNotFound());
  assertThat(db.queryForObject("SELECT COUNT(*) FROM news_articles WHERE id IN (?,?)",Long.class,a,b)).isEqualTo(2);
 }
 @Test void eventSuggestionsNeverPublishAndExcludedMatchesStayExcluded()throws Exception{
  long a=eventArticle("Company Alpha announces satellite launch mission today","2026-11-01T10:00:00Z");
  long b=eventArticle("Company Alpha announces satellite launch mission tomorrow","2026-11-02T10:00:00Z");
  events.propose();
  assertThat(events.list(false)).isEmpty();
  long id=((Number)events.list(true).getFirst().get("id")).longValue();
  assertThat((List<?>)events.event(id,true).get("suggestions")).hasSize(2);
  events.link(id,b,json.readTree("{\"state\":\"excluded\"}"));
  db.update("DELETE FROM news_event_scanned_articles WHERE article_id=?",b);
  events.propose();
  assertThat(db.queryForObject("SELECT state FROM news_event_articles WHERE event_id=? AND article_id=?",String.class,id,b)).isEqualTo("excluded");
  events.propose();
  assertThat(events.list(true)).hasSize(1);
  // A pending title is scanned once it becomes available, even below the scan cursor.
  db.update("DELETE FROM news_event_scanned_articles WHERE article_id=?",a);events.propose();
  assertThat(events.list(false)).isEmpty();
 }
 @Test void eventPermissionsAreIndependentAndRevocationImmediate()throws Exception{
  User u=users.findByUsername("event-test-user").orElseGet(User::new);u.setUsername("event-test-user");u.setPasswordHash(encoder.encode("event-test-password"));u.setPermissions(Set.of());u=users.saveAndFlush(u);
  var session=login("event-test-user","event-test-password");
  long article=eventArticle("Event permission original article report","2026-11-01T10:00:00Z");
  long id=((Number)events.create(json.readTree("{\"title\":\"权限事件\",\"category\":\"科技\"}")).get("id")).longValue();
  var endpoints=List.of(new String[]{"GET","/api/admin/news/events","news.events.read",""},
   new String[]{"GET","/api/admin/news/event-articles?q=Event","news.events.read",""},
   new String[]{"POST","/api/admin/news/events","news.events.create","{\"title\":\"新事件\",\"category\":\"科技\"}"},
   new String[]{"PATCH","/api/admin/news/events/"+id,"news.events.update","{\"title\":\"修改事件\",\"category\":\"科技\"}"},
   new String[]{"PUT","/api/admin/news/events/"+id+"/status","news.events.publish","{\"status\":\"draft\"}"},
   new String[]{"PUT","/api/admin/news/events/"+id+"/articles/"+article,"news.events.associate","{}"});
  for(var endpoint:endpoints){
   u.setPermissions(Set.of());users.saveAndFlush(u);
   mvc.perform(request(org.springframework.http.HttpMethod.valueOf(endpoint[0]),endpoint[1]).session(session).with(csrf()).contentType(APPLICATION_JSON).content(endpoint[3])).andExpect(status().isForbidden());
   u.setPermissions(Set.of(endpoint[2]));users.saveAndFlush(u);
   mvc.perform(request(org.springframework.http.HttpMethod.valueOf(endpoint[0]),endpoint[1]).session(session).with(csrf()).contentType(APPLICATION_JSON).content(endpoint[3])).andExpect(status().isOk());
   u.setPermissions(Set.of());users.saveAndFlush(u);
   mvc.perform(request(org.springframework.http.HttpMethod.valueOf(endpoint[0]),endpoint[1]).session(session).with(csrf()).contentType(APPLICATION_JSON).content(endpoint[3])).andExpect(status().isForbidden());
  }
  assertThat(db.queryForObject("SELECT COUNT(*) FROM audit_log WHERE module='news' AND event_code='event_associate' AND result='success'",Long.class)).isPositive();
  assertThat(db.queryForObject("SELECT COUNT(*) FROM audit_log WHERE request_path LIKE '/api/admin/news/events%' AND status_code=403",Long.class)).isPositive();
  mvc.perform(get("/api/admin/news/events")).andExpect(status().isUnauthorized());
  mvc.perform(put("/api/admin/news/events/"+id+"/status").session(session).contentType(APPLICATION_JSON).content("{}")).andExpect(status().isForbidden());
 }
}
