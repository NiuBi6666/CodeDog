package cn.codedog.controller;
import cn.codedog.service.*;import org.springframework.web.bind.annotation.*;import jakarta.servlet.http.HttpServletRequest;import java.security.Principal;import java.util.*;
@RestController public class RankingEngagementController {
 private final RankingRewardRuleService rules;private final RankingMessageService messages;private final StudentRankingAuthService auth;private final AuditService audit;
 public RankingEngagementController(RankingRewardRuleService q,RankingMessageService n,StudentRankingAuthService a,AuditService l){rules=q;messages=n;auth=a;audit=l;}
 @GetMapping("/api/rankings/admin/reward-rules") public Object rules(Principal p){return rules.rules(p.getName(),false);}
 @PutMapping("/api/rankings/admin/reward-rules/{id}") public Object rule(@PathVariable long id,@RequestBody RankingRewardRuleService.Rule b,Principal p,HttpServletRequest r){var v=rules.save(p.getName(),id,b);audit.change("REWARD_RULE",id,Map.of(),v);audit.record("ranking_reward_rule_updated",r);return v;}
 @GetMapping("/api/public/rankings/student-reward-rules") public Object studentRules(HttpServletRequest r){var s=auth.current(r);return rules.rules(s.ownerUsername(),true);}
 @GetMapping("/api/rankings/admin/messages") public Object messages(Principal p,@RequestParam(defaultValue="0") int page){return messages.list(p.getName(),"TEACHER",p.getName(),page);}
 @PostMapping("/api/rankings/admin/messages/{id}/read") public Object read(@PathVariable long id,Principal p,HttpServletRequest r){messages.read(p.getName(),"TEACHER",p.getName(),id);audit.change("MESSAGE",id,Map.of("read",false),Map.of("read",true));audit.record("ranking_message_read",r);return Map.of("read",true);}
 @GetMapping("/api/public/rankings/student-messages") public Object studentMessages(HttpServletRequest r,@RequestParam(defaultValue="0") int page){var s=auth.current(r);return messages.list(s.ownerUsername(),"STUDENT",s.studentId(),page);}
 @PostMapping("/api/public/rankings/student-messages/{id}/read") public Object studentRead(@PathVariable long id,HttpServletRequest r){var s=auth.current(r);messages.read(s.ownerUsername(),"STUDENT",s.studentId(),id);audit.change("MESSAGE",id,Map.of("read",false),Map.of("read",true));audit.record("student_message_read",r);return Map.of("read",true);}
 @GetMapping("/api/rankings/admin/alerts") public Object alerts(Principal p){return messages.alerts(p.getName());}
}
