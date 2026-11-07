package cn.codedog.news;
import cn.codedog.service.StructuredAuditService;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api")
public class NewsEventController {
 private final NewsEventService events;private final StructuredAuditService audit;
 public NewsEventController(NewsEventService events,StructuredAuditService audit){this.events=events;this.audit=audit;}
 private void log(String code,Object id){audit.event("news","management",code);audit.target("NEWS_EVENT",id);}
 @GetMapping("/news/events") public Object list(){return Map.of("events",events.list(false));}
 @GetMapping("/news/events/{id}") public Object detail(@PathVariable long id){return events.event(id,false);}
 @GetMapping("/admin/news/events") public Object adminList(){log("events_read",null);return Map.of("events",events.list(true));}
 @GetMapping("/admin/news/events/{id}") public Object adminDetail(@PathVariable long id){log("event_read",id);return events.event(id,true);}
 @GetMapping("/admin/news/event-articles") public Object search(@RequestParam String q){log("event_articles_search",null);return Map.of("articles",events.search(q));}
 @PostMapping("/admin/news/events") public Object create(@RequestBody JsonNode b){var after=events.create(b);log("event_create",after.get("id"));audit.change("NEWS_EVENT",after.get("id"),Map.of(),after);return after;}
 @PatchMapping("/admin/news/events/{id}") public Object edit(@PathVariable long id,@RequestBody JsonNode b){var before=events.event(id,true);var after=events.edit(id,b);log("event_update",id);audit.change("NEWS_EVENT",id,before,after);return after;}
 @PutMapping("/admin/news/events/{id}/status") public Object status(@PathVariable long id,@RequestBody JsonNode b){var before=events.event(id,true);var after=events.publish(id,b);log("event_publish",id);audit.change("NEWS_EVENT",id,before,after);return after;}
 @PutMapping("/admin/news/events/{id}/articles/{articleId}") public Object link(@PathVariable long id,@PathVariable long articleId,@RequestBody JsonNode b){var before=events.event(id,true);var after=events.link(id,articleId,b);log("event_associate",id);audit.change("NEWS_EVENT",id,before,after);return after;}
}
