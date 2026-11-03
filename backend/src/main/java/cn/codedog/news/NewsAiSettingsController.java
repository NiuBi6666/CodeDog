
package cn.codedog.news;
import cn.codedog.service.StructuredAuditService;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/admin/news/ai-settings")
public class NewsAiSettingsController {
 private final NewsTranslation settings;private final StructuredAuditService audit;
 public NewsAiSettingsController(NewsTranslation settings,StructuredAuditService audit){this.settings=settings;this.audit=audit;}
 @GetMapping public Object read(){audit.event("news","management","ai_settings_read");audit.target("NEWS_AI_SETTINGS",1);return settings.view();}
 @PutMapping public Object save(@RequestBody JsonNode body){
  var before=safe(settings.view());var after=settings.save(body);
  audit.event("news","management","ai_settings_update");audit.target("NEWS_AI_SETTINGS",1);audit.change("NEWS_AI_SETTINGS",1,before,safe(after));
  audit.detail(Map.of("credential_changed",!body.path("api_key").asText("").isBlank()));return after;
 }
 @PostMapping("/test")public Object test(){audit.event("news","management","ai_settings_test");audit.target("NEWS_AI_SETTINGS",1);return settings.test();}
 private Map<String,Object> safe(Map<String,Object> value){return Map.of("base_url",value.get("base_url"),"model",value.get("model"),"configured",value.get("configured"),"source",value.get("source"),"mode",value.get("mode"),"engine",value.get("engine"));}
}
