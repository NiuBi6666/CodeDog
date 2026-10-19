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
  public record PublicStyle(String frame,String title,String titleName) {}
  public record State(Selection selection, int level, List<Option> options) {}
  private static final List<Option> OPTIONS = List.of(
    new Option("frame","base","基础光环",1), new Option("frame","cyan","电光轨道",2),
    new Option("frame","prism","棱彩星环",4), new Option("frame","nova","星耀星芒",6), new Option("frame","regal","王者冠冕",7),
    new Option("title","rookie","代码新星",1), new Option("title","solver","解题先锋",3),
    new Option("title","architect","算法探索者",5), new Option("title","sovereign","巅峰王者",7),
    new Option("background","grid","深蓝网格",1), new Option("background","aurora","极光终端",3),
    new Option("background","orbit","星际轨道",5));
  private final JdbcTemplate jdbc;
  private final StudentRankingAuthService auth;
  private final AuditService audit;
  private final RankingAnnouncementBroadcaster broadcaster;
  public StudentStyleService(JdbcTemplate jdbc, StudentRankingAuthService auth, AuditService audit, RankingAnnouncementBroadcaster broadcaster) {
    this.jdbc=jdbc; this.auth=auth; this.audit=audit;this.broadcaster=broadcaster;
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
    if(org.springframework.transaction.support.TransactionSynchronizationManager.isSynchronizationActive())
      org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(new org.springframework.transaction.support.TransactionSynchronization(){@Override public void afterCommit(){broadcaster.styleUpdated(student.ownerUsername());}});
    else broadcaster.styleUpdated(student.ownerUsername());
    return new State(input,level,OPTIONS);
  }
  private Map<String,Object> values(Selection s) {return Map.of("frame",s.frame(),"title",s.title(),"background",s.background());}
  private int level(HttpServletRequest request,String studentId) {
    return auth.board(request).rankings().stream().filter(e->studentId.equals(e.studentId()))
      .mapToInt(e->HonorLevels.of(e.totalPoints()).level()).findFirst().orElse(1);
  }
  /** Only board-visible names may expose frames/titles; backgrounds remain private. */
  public Map<String,PublicStyle> publicStyles(String owner,List<cn.codedog.model.RankingPayload.Entry> visible) {
    if(visible.isEmpty())return Map.of();
    if(visible.size()>11)throw new IllegalArgumentException("Only the visible student board may request styles");
    Map<String,Integer> levels=new HashMap<>();visible.forEach(e->levels.put(e.studentId(),HonorLevels.of(e.totalPoints()).level()));
    Map<String,Selection> stored=new HashMap<>();
    List<Object> args=new ArrayList<>();args.add(owner);args.addAll(levels.keySet());
    String slots=String.join(",",Collections.nCopies(levels.size(),"?"));
    jdbc.query("SELECT student_id,avatar_frame,title_code,background_code FROM ranking_student_styles WHERE owner_username=? AND student_id IN ("+slots+")",rs->{stored.put(rs.getString(1),new Selection(rs.getString(2),rs.getString(3),rs.getString(4)));},args.toArray());
    Map<String,PublicStyle> result=new LinkedHashMap<>();
    levels.forEach((id,level)->{var selected=normalize(stored.getOrDefault(id,new Selection("base","rookie","grid")),level);String name=OPTIONS.stream().filter(o->o.type().equals("title")&&o.code().equals(selected.title())).findFirst().orElseThrow().name();result.put(id,new PublicStyle(selected.frame(),selected.title(),name));});
    return Map.copyOf(result);
  }
  private Selection stored(StudentRankingAuthService.StudentSession student) {
    var found=jdbc.query("SELECT avatar_frame,title_code,background_code FROM ranking_student_styles WHERE owner_username=? AND student_id=?",
      (rs,i)->new Selection(rs.getString(1),rs.getString(2),rs.getString(3)),student.ownerUsername(),student.studentId());
    return found.isEmpty()?new Selection("base","rookie","grid"):found.get(0);
  }
  private boolean allowed(String type,String code,int level) {return OPTIONS.stream().anyMatch(o->o.type().equals(type)&&o.code().equals(code)&&o.level()<=level);}
  private Selection normalize(Selection s,int level) {return new Selection(allowed("frame",s.frame(),level)?s.frame():"base",allowed("title",s.title(),level)?s.title():"rookie",allowed("background",s.background(),level)?s.background():"grid");}
}
