package cn.codedog.controller;
import cn.codedog.service.*;import org.springframework.web.bind.annotation.*;import jakarta.servlet.http.HttpServletRequest;import java.security.Principal;import java.util.*;
@RestController public class RankingContinuityController {
 private final RankingOwnershipService owners;private final RankingCompletenessService complete;private final AuditService audit;
 public RankingContinuityController(RankingOwnershipService o,RankingCompletenessService c,AuditService a){owners=o;complete=c;audit=a;}
 @GetMapping("/api/rankings/admin/transfers") public Object owners(){return Map.of("ownerships",owners.ownerships(),"teachers",owners.teachers());}
 public record Transfer(String from,String to,List<String> studentIds,boolean offboard,String reason){}public record Resolve(String owner,String reason){}
 @PostMapping("/api/rankings/admin/transfers/preview") public Object preview(@RequestBody Transfer b,Principal p,HttpServletRequest r){var v=owners.preview(b.from(),b.to(),b.studentIds(),b.offboard(),b.reason(),p.getName());audit.detail(v);audit.record("ranking_transfer_previewed",r);return v;}
 @PostMapping("/api/rankings/admin/transfers/{id}/confirm") public Object confirm(@PathVariable String id,Principal p,HttpServletRequest r){var v=owners.confirm(id,p.getName());audit.change("STUDENT_TRANSFER",id,Map.of("state","PREVIEW"),v);audit.record("ranking_transfer_confirmed",r);return v;}
 @PostMapping("/api/rankings/admin/transfers/conflicts/{id}") public Object resolve(@PathVariable String id,@RequestBody Resolve b,Principal p,HttpServletRequest r){var v=owners.resolve(id,b.owner(),b.reason(),p.getName());audit.change("STUDENT_OWNERSHIP",id,Map.of("state","CONFLICT"),v);audit.record("ranking_ownership_resolved",r);return v;}
 @GetMapping("/api/rankings/admin/completeness") public Object panel(Principal p){return complete.panel(p.getName());}
 @GetMapping("/api/rankings/admin/expected-lessons") public Object expected(Principal p){return complete.expected(p.getName());}
 @PostMapping("/api/rankings/admin/expected-lessons") public Object create(@RequestBody RankingCompletenessService.Expected b,Principal p,HttpServletRequest r){var v=complete.save(p.getName(),null,b);audit.change("EXPECTED_LESSON","new",Map.of(),v);audit.record("ranking_expected_created",r);return v;}
 @PutMapping("/api/rankings/admin/expected-lessons/{id}") public Object edit(@PathVariable long id,@RequestBody RankingCompletenessService.Expected b,Principal p,HttpServletRequest r){var v=complete.save(p.getName(),id,b);audit.change("EXPECTED_LESSON",id,Map.of(),v);audit.record("ranking_expected_updated",r);return v;}
 @DeleteMapping("/api/rankings/admin/expected-lessons/{id}") public Object delete(@PathVariable long id,Principal p,HttpServletRequest r){var v=complete.delete(p.getName(),id);audit.change("EXPECTED_LESSON",id,v,Map.of());audit.record("ranking_expected_deleted",r);return Map.of("deleted",true);}
}
