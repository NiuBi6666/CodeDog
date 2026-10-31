package cn.codedog.controller;
import cn.codedog.service.AuditService;
import cn.codedog.service.RankingGraduationService;
import jakarta.servlet.http.HttpServletRequest;
import java.security.Principal;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/rankings/admin")
public class RankingGraduationController {
  private final RankingGraduationService service;
  private final AuditService audit;
  public RankingGraduationController(RankingGraduationService service,AuditService audit){this.service=service;this.audit=audit;}
  @GetMapping("/graduates")
  public List<RankingGraduationService.Graduate> graduates(Principal p,HttpServletRequest r){
    var rows=service.graduates(p.getName());
    audit.owner(p.getName());audit.event("rankings","READ","ranking_graduates_viewed");
    audit.target("GRADUATED_STUDENTS",p.getName());audit.detail(Map.of("returnedCount",rows.size()));
    audit.record("ranking_graduates_viewed",r);return rows;
  }
  @PutMapping("/students/{studentId}/graduation")
  public RankingGraduationService.Change graduate(@PathVariable String studentId,Principal p,HttpServletRequest r){return change(studentId,true,p,r);}
  @DeleteMapping("/students/{studentId}/graduation")
  public RankingGraduationService.Change restore(@PathVariable String studentId,Principal p,HttpServletRequest r){return change(studentId,false,p,r);}
  private RankingGraduationService.Change change(String id,boolean graduated,Principal p,HttpServletRequest r){
    var value=service.setGraduated(p.getName(),id,graduated,p.getName());
    audit.owner(p.getName());audit.event("rankings","UPDATE",graduated?"ranking_student_graduated":"ranking_student_restored");
    audit.change("STUDENT_GRADUATION",id,Map.of("graduated",value.graduatedBefore()),Map.of("graduated",value.graduated()));
    var detail=new java.util.LinkedHashMap<String,Object>();detail.put("studentId",id);detail.put("studentName",value.studentName());
    detail.put("graduatedAt",value.graduatedAt());detail.put("changed",value.changed());audit.detail(detail);
    audit.record((graduated?"ranking_student_graduated:":"ranking_student_restored:")+id,r);return value;
  }
}
