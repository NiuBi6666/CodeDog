package cn.codedog.controller;
import org.springframework.web.bind.annotation.*;import org.springframework.jdbc.core.JdbcTemplate;import org.springframework.beans.factory.ObjectProvider;import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;import com.fasterxml.jackson.databind.ObjectMapper;import java.util.*;
@RestController public class RankingRecoveryDrillController {
 private final JdbcTemplate j;private final ObjectMapper json;private final ObjectProvider<ScheduledAnnotationBeanPostProcessor> scheduled;
 public RankingRecoveryDrillController(JdbcTemplate j,ObjectMapper m,ObjectProvider<ScheduledAnnotationBeanPostProcessor> s){this.j=j;json=m;scheduled=s;}
 @GetMapping("/api/rankings/admin/recovery-drill") public Object latest(){var rows=j.queryForList("SELECT report_json FROM ranking_recovery_drills ORDER BY finished_at DESC LIMIT 1");Object report=Map.of("status","NOT_RUN");if(!rows.isEmpty())try{report=json.readValue((String)rows.getFirst().get("report_json"),Object.class);}catch(Exception e){throw new IllegalStateException("恢复演练报告解析失败");}return Map.of("report",report,"schedulingEnabled",scheduled.getIfAvailable()!=null);}
}
