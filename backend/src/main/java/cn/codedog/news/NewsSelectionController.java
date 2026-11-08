package cn.codedog.news;

import cn.codedog.service.StructuredAuditService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import java.util.concurrent.Semaphore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/** Public reader action, restricted to text in published articles; management permissions are unchanged. */
@RestController
public class NewsSelectionController {
 private final JdbcTemplate db;private final NewsTranslation translation;private final ObjectMapper json;private final StructuredAuditService audit;
 private final Semaphore active=new Semaphore(2);
 private final Map<String,Window> clients=new HashMap<>();
 private long minute;private int total;
 private static class Window {long minute;int count;Window(long minute){this.minute=minute;}}
 public NewsSelectionController(JdbcTemplate db,NewsTranslation translation,ObjectMapper json,StructuredAuditService audit){this.db=db;this.translation=translation;this.json=json;this.audit=audit;}
 private static String normalize(String text){return text.replaceAll("(?U)\\s+"," ").strip();}
 private synchronized void limit(String client){
  long now=System.nanoTime()/60_000_000_000L;
  if(minute!=now){minute=now;total=0;clients.entrySet().removeIf(e->e.getValue().minute!=now);}
  if(!clients.containsKey(client)&&clients.size()>=2048)throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,"翻译请求较多，请稍后再试");
  Window w=clients.computeIfAbsent(client,k->new Window(now));
  if(w.minute!=now){w.minute=now;w.count=0;}
  if(w.count>=60||total>=240)throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,"翻译过于频繁，请稍后再试");
  w.count++;total++;
 }
 @PostMapping(value="/api/news/translate",consumes="application/json")
 public Object translate(HttpServletRequest request)throws Exception {
  audit.event("news","public","selection_translate");
  byte[] bytes=request.getInputStream().readNBytes(4097);
  if(bytes.length>4096)throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE,"选中内容过长");
  com.fasterxml.jackson.databind.JsonNode body;
  try{body=json.readTree(bytes);}catch(Exception e){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"请求格式无效");}
  if(body==null||!body.path("article_id").isIntegralNumber()||!body.path("article_id").canConvertToLong()||body.path("article_id").asLong()<=0||!body.path("text").isTextual())throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"请选择文章中的英文");
  long id=body.path("article_id").asLong();String raw=body.path("text").asText();
  audit.target("NEWS_ARTICLE",id);
  audit.detail(Map.of("selected_characters",raw.length(),"target_language","zh"));
  if(raw.length()>1000)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"每次最多翻译 1000 个字符，请缩小选区");
  String text=normalize(raw);
  if(text.isBlank()||!text.matches("(?s).*[A-Za-z].*"))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"请选择英文单词、短语或句子");
  var rows=db.queryForList("SELECT title_en,content_en,category FROM news_articles WHERE id=? AND (status='ready' OR (title_zh IS NOT NULL AND title_zh<>''))",id);
  if(rows.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"文章不可用");
  var article=rows.getFirst();
  if(!normalize(Objects.toString(article.get("content_en"),"")).contains(text)&&!normalize(Objects.toString(article.get("title_en"),"")).contains(text))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"请从当前文章的英文原文中选择内容");
  // Shared networks must not share a reader allowance; cached results consume no provider quota.
  String client=request.getSession(true).getId();
  var cached=translation.cachedSelection(text,Objects.toString(article.get("category"),"国际"));
  if(cached!=null)return cached;
  if(!active.tryAcquire())throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,"翻译正在处理中，请稍后再试");
  try{limit(client);return translation.translateSelection(text,Objects.toString(article.get("category"),"国际"));}
  finally{active.release();}
 }
}
