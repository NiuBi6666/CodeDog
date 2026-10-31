package cn.codedog.service;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
@Service
public class RankingGraduationService {
  private final JdbcTemplate jdbc;
  public RankingGraduationService(JdbcTemplate jdbc) { this.jdbc=jdbc; }
  public record Graduate(String studentId,String studentName,Instant graduatedAt,String updatedBy) {}
  public record Change(String studentId,String studentName,boolean graduatedBefore,boolean graduated,Instant graduatedAt,boolean changed) {}
  public List<Graduate> graduates(String owner) {
    return jdbc.query("""
      SELECT g.student_id,MAX(s.student_name) student_name,g.graduated_at,g.updated_by
      FROM ranking_student_graduations g
      JOIN ranking_students s ON s.owner_username=g.owner_username AND s.student_id=g.student_id
      WHERE g.owner_username=? AND g.graduated_at IS NOT NULL
      AND NOT EXISTS (SELECT 1 FROM ranking_current_ownership o WHERE o.student_id=g.student_id
        AND (o.state<>'ACTIVE' OR o.owner_username<>g.owner_username))
      GROUP BY g.student_id,g.graduated_at,g.updated_by ORDER BY g.graduated_at DESC,g.student_id
      """,(rs,n)->new Graduate(rs.getString(1),rs.getString(2),rs.getTimestamp(3).toInstant(),rs.getString(4)),owner);
  }
  @Transactional
  public Change setGraduated(String owner,String studentId,boolean graduated,String actor) {
    if(studentId==null||studentId.isBlank()||studentId.length()>100)
      throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,"学员 ID 无效");
    // Lock every class row in stable order to serialize concurrent retries.
    var names=jdbc.query("""
      SELECT student_name FROM ranking_students WHERE owner_username=? AND student_id=?
      ORDER BY camp_id,class_id FOR UPDATE
      """,(rs,n)->rs.getString(1),owner,studentId);
    if(names.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"学员不存在或不属于当前老师");
    RankingOwnershipGuard.requireCurrent(jdbc,owner,studentId,false);
    var existing=jdbc.query("SELECT graduated_at FROM ranking_student_graduations WHERE owner_username=? AND student_id=?",
      (rs,n)->rs.getTimestamp(1),owner,studentId);
    boolean before=!existing.isEmpty()&&existing.getFirst()!=null;
    if(before!=graduated)jdbc.update("""
      INSERT INTO ranking_student_graduations(owner_username,student_id,graduated_at,updated_by)
      VALUES(?,?,?,?) ON DUPLICATE KEY UPDATE graduated_at=VALUES(graduated_at),
      updated_by=VALUES(updated_by),updated_at=CURRENT_TIMESTAMP(6)
      """,owner,studentId,graduated?java.sql.Timestamp.from(Instant.now()):null,actor);
    var at=jdbc.query("SELECT graduated_at FROM ranking_student_graduations WHERE owner_username=? AND student_id=?",
      (rs,n)->rs.getTimestamp(1),owner,studentId);
    return new Change(studentId,names.getFirst(),before,graduated,at.isEmpty()||at.getFirst()==null?null:at.getFirst().toInstant(),before!=graduated);
  }
}
