package cn.codedog.service;

import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class StudentStyleService {
  public record Selection(String frame, String title, String background) {}
  public record Option(String type, String code, String name, int level) {}
  public record State(Selection selection, int level, List<Option> options) {}
  private static final List<Option> OPTIONS = List.of(
    new Option("frame","base","基础光环",1), new Option("frame","cyan","电光轨道",2),
    new Option("frame","prism","棱彩星环",4), new Option("frame","nova","钻石星芒",6),
    new Option("title","rookie","代码新星",1), new Option("title","solver","解题先锋",3),
    new Option("title","architect","算法探索者",5),
    new Option("background","grid","深蓝网格",1), new Option("background","aurora","极光终端",3),
    new Option("background","orbit","星际轨道",5));
  private final JdbcTemplate jdbc;
  private final StudentRankingAuthService auth;
  private final AuditService audit;
  public StudentStyleService(JdbcTemplate jdbc, StudentRankingAuthService auth, AuditService audit) {
    this.jdbc=jdbc; this.auth=auth; this.audit=audit;
  }
  public State read(HttpServletRequest request) {
    var student=auth.current(request);
    int level=level(request,student.studentId());
    audit.owner(student.ownerUsername()); audit.actor("STUDENT",student.studentId(),student.studentName());
    audit.event("rankings","READ","student_style_viewed"); audit.target("STUDENT_STYLE",student.studentId());
    audit.detail(Map.of("count",1,"level",level)); audit.record("student_style_viewed:student_id="+student.studentId(),request);
    return new State(normalize(stored(student),level),level,OPTIONS);
  }
  @Transactional
  public State save(Selection input,HttpServletRequest request) {
    var student=auth.current(request);
    int level=level(request,student.studentId());
    audit.owner(student.ownerUsername()); audit.actor("STUDENT",student.studentId(),student.studentName());
    audit.event("rankings","UPDATE","student_style_updated"); audit.target("STUDENT_STYLE",student.studentId());
    if(input==null || !allowed("frame",input.frame(),level) || !allowed("title",input.title(),level)
        || !allowed("background",input.background(),level))
      throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,"装扮尚未解锁或选项无效");
    var before=stored(student);
    jdbc.update("INSERT INTO ranking_student_styles(owner_username,student_id,avatar_frame,title_code,background_code) VALUES(?,?,?,?,?) ON DUPLICATE KEY UPDATE avatar_frame=VALUES(avatar_frame),title_code=VALUES(title_code),background_code=VALUES(background_code),updated_at=CURRENT_TIMESTAMP",student.ownerUsername(),student.studentId(),input.frame(),input.title(),input.background());
    audit.change("STUDENT_STYLE",student.studentId(),values(before),values(input));
    audit.record("student_style_updated:student_id="+student.studentId(),request);
    return new State(input,level,OPTIONS);
  }
  private Map<String,Object> values(Selection s) {return Map.of("frame",s.frame(),"title",s.title(),"background",s.background());}
  private int level(HttpServletRequest request,String studentId) {
    return auth.board(request).rankings().stream().filter(e->studentId.equals(e.studentId()))
      .mapToInt(e->e.level()).findFirst().orElse(1);
  }
  private Selection stored(StudentRankingAuthService.StudentSession student) {
    var found=jdbc.query("SELECT avatar_frame,title_code,background_code FROM ranking_student_styles WHERE owner_username=? AND student_id=?",
      (rs,i)->new Selection(rs.getString(1),rs.getString(2),rs.getString(3)),student.ownerUsername(),student.studentId());
    return found.isEmpty()?new Selection("base","rookie","grid"):found.get(0);
  }
  private boolean allowed(String type,String code,int level) {return OPTIONS.stream().anyMatch(o->o.type().equals(type)&&o.code().equals(code)&&o.level()<=level);}
  private Selection normalize(Selection s,int level) {return new Selection(allowed("frame",s.frame(),level)?s.frame():"base",allowed("title",s.title(),level)?s.title():"rookie",allowed("background",s.background(),level)?s.background():"grid");}
}
