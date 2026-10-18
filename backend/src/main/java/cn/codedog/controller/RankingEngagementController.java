package cn.codedog.controller;
import cn.codedog.service.*;import org.springframework.web.bind.annotation.*;import jakarta.servlet.http.HttpServletRequest;import java.security.Principal;import java.util.*;
@RestController public class RankingEngagementController {
 private final RankingRewardRuleService rules;private final RankingAlertService alerts;private final StudentRankingAuthService auth;private final AuditService audit;
 public RankingEngagementController(RankingRewardRuleService q,RankingAlertService n,StudentRankingAuthService a,AuditService l){rules=q;alerts=n;auth=a;audit=l;}
 @GetMapping("/api/rankings/admin/reward-rules") public Object rules(Principal p){return rules.rules(p.getName(),false);}
 @PutMapping("/api/rankings/admin/reward-rules/{id}") public Object rule(@PathVariable long id,@RequestBody RankingRewardRuleService.Rule b,Principal p,HttpServletRequest r){var v=rules.save(p.getName(),id,b);audit.change("REWARD_RULE",id,Map.of(),v);audit.record("ranking_reward_rule_updated",r);return v;}
 @GetMapping("/api/public/rankings/student-reward-rules") public Object studentRules(HttpServletRequest r){var s=auth.current(r);return rules.rules(s.ownerUsername(),true);}
 @GetMapping("/api/rankings/admin/alerts") public Object alerts(Principal p){return alerts.alerts(p.getName());}
}
