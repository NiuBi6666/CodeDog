package cn.codedog.service;
import cn.codedog.model.RankingPayload;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StudentHonorService {
  public record BoardView(@JsonUnwrapped RankingPayload.Board board,
    Map<String,HonorLevels.Honor> honors,Map<String,StudentStyleService.PublicStyle> styles,
    HonorLevels.Honor myHonor,List<HonorLevels.Tier> honorTiers,boolean promotionDue,String motivation) {}
  public record Celebration(boolean celebrate,HonorLevels.Honor honor) {}
  private final StudentRankingAuthService auth;
  private final RankingBoardService boards;
  private final StudentStyleService styles;
  private final JdbcTemplate jdbc;
  private final AuditService audit;
  public StudentHonorService(StudentRankingAuthService auth,RankingBoardService boards,StudentStyleService styles,JdbcTemplate jdbc,AuditService audit) {
    this.auth=auth;this.boards=boards;this.styles=styles;this.jdbc=jdbc;this.audit=audit;
  }
  public BoardView board(HttpServletRequest request) {
    var student=auth.current(request);
    var full=boards.allBoardForOwner(student.ownerUsername());
    var mine=full.rankings().stream().filter(r->r.studentId().equals(student.studentId())).findFirst().orElseThrow(()->new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND,"学员不在当前排行榜中"));
    var visible=new ArrayList<>(full.rankings().stream().limit(10).toList());
    if(visible.stream().noneMatch(r->r.studentId().equals(student.studentId())))visible.add(mine);
    var board=new RankingPayload.Board(full.campId(),full.campName(),full.classId(),full.className(),full.scope(),full.studentCount(),full.updatedAt(),full.trendBaselineDate(),List.copyOf(visible));
    Map<String,HonorLevels.Honor> honors=new LinkedHashMap<>();
    visible.forEach(r->honors.put(r.studentId(),HonorLevels.of(r.totalPoints())));
    var honor=honors.get(student.studentId());
    String motivation=full.rankings().stream().filter(r->r.rank()<mine.rank()).reduce((a,b)->b)
      .map(r->"距离超越上一名还差 "+((long)r.availablePoints()-mine.availablePoints()+1)+" 积分，冲鸭！")
      .orElse("你已站上榜首，继续积攒积分，守住荣耀！");
    audit.owner(student.ownerUsername());audit.actor("STUDENT",student.studentId(),student.studentName());
    audit.event("rankings","READ","student_honor_board_viewed");audit.target("RANKING_BOARD",student.ownerUsername());
    audit.detail(Map.of("returnedCount",visible.size(),"includesSelf",true));audit.record("student_honor_board_viewed",request);
    return new BoardView(board,Map.copyOf(honors),styles.publicStyles(student.ownerUsername(),visible),honor,HonorLevels.TIERS,honor.level()>announced(student.ownerUsername(),student.studentId()),motivation);
  }
  @Transactional
  public Celebration claim(HttpServletRequest request) {
    var student=auth.current(request);
    var mine=auth.board(request).rankings().stream().filter(r->r.studentId().equals(student.studentId())).findFirst();
    var honor=HonorLevels.of(mine.map(RankingPayload.Entry::totalPoints).orElse(0));
    jdbc.update("INSERT INTO ranking_student_honor_progress(owner_username,student_id,highest_announced_tier) VALUES(?,?,1) ON DUPLICATE KEY UPDATE student_id=VALUES(student_id)",student.ownerUsername(),student.studentId());
    Integer previous=jdbc.queryForObject("SELECT highest_announced_tier FROM ranking_student_honor_progress WHERE owner_username=? AND student_id=? FOR UPDATE",Integer.class,student.ownerUsername(),student.studentId());
    boolean celebrate=honor.level()>previous;
    if(celebrate)jdbc.update("UPDATE ranking_student_honor_progress SET highest_announced_tier=?,updated_at=CURRENT_TIMESTAMP WHERE owner_username=? AND student_id=?",honor.level(),student.ownerUsername(),student.studentId());
    audit.owner(student.ownerUsername());audit.actor("STUDENT",student.studentId(),student.studentName());
    audit.event("rankings","UPDATE","student_honor_promotion_checked");audit.target("STUDENT_HONOR",student.studentId());
    if(celebrate)audit.change("STUDENT_HONOR",student.studentId(),Map.of("announcedTier",previous),Map.of("announcedTier",honor.level()));
    audit.detail(Map.of("celebrate",celebrate));audit.record("student_honor_promotion_checked",request);
    return new Celebration(celebrate,honor);
  }
  private int announced(String owner,String id) {
    var found=jdbc.query("SELECT highest_announced_tier FROM ranking_student_honor_progress WHERE owner_username=? AND student_id=?",(rs,i)->rs.getInt(1),owner,id);
    return found.isEmpty()?1:found.get(0);
  }
}
