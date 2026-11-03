
package cn.codedog.news;
import cn.codedog.service.StructuredAuditService;
import cn.codedog.service.AuditContext;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@RestController @RequestMapping("/api")
public class NewsController {
 private final NewsService news;private final StructuredAuditService audit;
 public NewsController(NewsService news,StructuredAuditService audit){this.news=news;this.audit=audit;}
 private void event(String action,Object id){audit.event("news","management",action);audit.target("NEWS",id);}
 @GetMapping("/news") public Object list(@RequestParam(defaultValue="")String q,@RequestParam(defaultValue="")String category,@RequestParam(defaultValue="")String source,@RequestParam(defaultValue="0")int page){return news.list(q,category,source,page);}
 @GetMapping("/news/sync-status") public Object syncStatus(@RequestParam(name="run_id",required=false)Long runId){return news.syncStatus(runId);}
 @GetMapping("/news/{id}") public Object article(@PathVariable long id){return news.article(id,false);}
 @PostMapping("/news/{id}/translation") public Object requestTranslation(@PathVariable long id){audit.event("news","public","body_translation_request");audit.target("NEWS_ARTICLE",id);return news.requestBodyTranslation(id);}
 @GetMapping("/admin/news")public Object overview(){event("overview",null);return news.overview();}
 @GetMapping("/admin/sources")public Object sources(){event("sources_read",null);return Map.of("sources",news.sources());}
 @PostMapping("/admin/sources")public Object create(@RequestBody JsonNode body){var result=news.save(null,body);event("source_create",result.get("id"));audit.change("NEWS_SOURCE",result.get("id"),Map.of(),result);return result;}
 @PatchMapping("/admin/sources/{id}")public Object update(@PathVariable long id,@RequestBody JsonNode body){var before=news.source(id);var after=news.save(id,body);event("source_update",id);audit.change("NEWS_SOURCE",id,before,after);return after;}
 @DeleteMapping("/admin/sources/{id}")public Object delete(@PathVariable long id){var before=news.source(id);news.delete(id);event("source_delete",id);audit.change("NEWS_SOURCE",id,before,Map.of());return Map.of("deleted",true);}
 @GetMapping("/admin/news/articles/{id}")public Object review(@PathVariable long id){event("article_review",id);return news.article(id,true);}
 @PostMapping("/admin/news/articles/{id}/retry")public Object retry(@PathVariable long id){news.retry(id);event("article_retry",id);audit.detail(Map.of("queued",1));return Map.of("status","pending");}
 @PostMapping({"/user/refresh","/admin/news/refresh"})public Object refresh(HttpServletRequest request){event("refresh",null);var principal=request.getUserPrincipal();return news.refresh(principal==null?"anonymous":principal.getName(),request.getRemoteAddr(),AuditContext.current().requestId());}
 @GetMapping("/crawler/tasks")public Object tasks(@RequestHeader(value="X-Webhook-Secret",required=false)String token,@RequestParam(name="force_all",defaultValue="false")boolean force,@RequestParam(defaultValue="false")boolean fast){news.authenticate(token);return Map.of("sources",fast?news.fastSources():news.tasks(force));}
 @PostMapping(value="/crawler/filter",consumes="application/json")public Object filter(HttpServletRequest request,@RequestHeader(value="X-Webhook-Secret",required=false)String token)throws Exception{
  news.authenticate(token);byte[] body=request.getInputStream().readNBytes(256*1024+1);
  if(body.length>256*1024)throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE,"URL 列表过大");
  try{return Map.of("urls",news.unseen(new com.fasterxml.jackson.databind.ObjectMapper().readTree(body)));}catch(com.fasterxml.jackson.core.JsonProcessingException e){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"JSON 无效");}
 }
 @PostMapping(value="/crawler/ingest",consumes="application/json")public Object ingest(HttpServletRequest request,@RequestHeader(value="X-Webhook-Secret",required=false)String token,@RequestHeader(value="X-Webhook-Timestamp",required=false)String timestamp,@RequestHeader(value="X-Webhook-Signature",required=false)String signature)throws Exception{
  byte[] body=request.getInputStream().readNBytes(2*1024*1024+1);news.verify(body,token,timestamp,signature);return news.accept(body);
 }
}
