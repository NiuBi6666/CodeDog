package cn.codedog.service;
import org.springframework.jdbc.core.JdbcTemplate;
public final class RankingOwnershipGuard {
 private RankingOwnershipGuard() {}
 public static void requireCurrent(JdbcTemplate j,String owner,String student,boolean establish){
  if(establish)j.update("INSERT INTO ranking_current_ownership(student_id,owner_username,state) VALUES(?,?,'ACTIVE') ON DUPLICATE KEY UPDATE student_id=student_id",student,owner);
  var rows=j.queryForList("SELECT owner_username,state FROM ranking_current_ownership WHERE student_id=?",student);
  if(!rows.isEmpty()&&(!"ACTIVE".equals(rows.getFirst().get("state"))||!owner.equals(rows.getFirst().get("owner_username"))))throw RankingPointsService.conflict("学员已交接或归属冲突，不能由当前老师写入，请联系最高管理员");
 }
}
