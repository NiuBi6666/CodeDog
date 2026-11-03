package cn.codedog.news;
import cn.codedog.service.StructuredAuditService;
import cn.codedog.service.AuditContext;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.http.HttpServletRequest;
import java.security.Principal;
import java.util.*;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/admin/news/collector-settings")
public class NewsCollectorSettingsController {
 private final NewsCollectorSettings settings;private final StructuredAuditService audit;
 public NewsCollectorSettingsController(NewsCollectorSettings settings,StructuredAuditService audit){this.settings=settings;this.audit=audit;}
 private void event(String code){audit.event("news","management",code);audit.target("NEWS_COLLECTOR_SETTINGS",1);}
 @GetMapping public Object read(){event("collector_settings_read");return settings.view();}
 @PutMapping public Object save(@RequestBody JsonNode body){event("collector_settings_update");var before=settings.view();var after=settings.save(body);audit.change("NEWS_COLLECTOR_SETTINGS",1,safe(before),safe(after));audit.detail(Map.of("credential_changed",!body.path("github_token").asText("").isBlank()));return after;}
 @PostMapping("/provision") public Object provision(Principal principal,HttpServletRequest request){event("collector_setup_requested");return settings.queue(principal.getName(),request.getRemoteAddr(),AuditContext.current().requestId());}
 private Map<String,Object> safe(Map<String,Object> view){return Map.of("repository",view.get("repository"),"workflow",view.get("workflow"),"api_url",view.get("api_url"),"configured",view.get("configured"),"setup_state",view.get("setup_state"));}
}
