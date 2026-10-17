package cn.codedog.service;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
@Service public class RankingAftercareService {
 private final JdbcTemplate jdbc;private final RankingPointsService points;private final StudentRankingAuthService auth;
 public RankingAftercareService(JdbcTemplate j,RankingPointsService p,StudentRankingAuthService a){jdbc=j;points=p;auth=a;}
 private String reason(String text){if(text==null||text.isBlank()||text.length()>500)throw RankingPointsService.conflict("请填写1至500字处理原因");return text.trim();}
 @Transactional public void requestCancellation(String owner,String student,long id,String note){
  points.lock(owner,student);var rows=jdbc.queryForList("SELECT status FROM ranking_reward_redemptions WHERE owner_username=? AND student_id=? AND id=? FOR UPDATE",owner,student,id);
  if(rows.isEmpty())throw RankingPointsService.conflict("兑换记录不存在");String state=(String)rows.getFirst().get("status");if(state.equals("CANCEL_REQUESTED"))return;
  if(!state.equals("PENDING"))throw RankingPointsService.conflict("只有未发放的订单可以申请取消");
  jdbc.update("DELETE FROM ranking_redemption_cancellations WHERE redemption_id=? AND owner_username=? AND decided_at IS NOT NULL",id,owner);
  jdbc.update("INSERT INTO ranking_redemption_cancellations(redemption_id,owner_username,student_id,reason) VALUES(?,?,?,?)",id,owner,student,reason(note));
  jdbc.update("UPDATE ranking_reward_redemptions SET status='CANCEL_REQUESTED' WHERE id=? AND owner_username=?",id,owner);
 }
 public List<Map<String,Object>> cancellations(String owner){return jdbc.queryForList("SELECT c.*,d.reward_name,d.points_spent,d.status,d.student_name FROM ranking_redemption_cancellations c JOIN ranking_reward_redemptions d ON d.id=c.redemption_id WHERE c.owner_username=? ORDER BY c.created_at DESC",owner);}
 @Transactional public Map<String,Object> decide(String owner,long id,boolean approved,String note,String actor){
  String explanation=reason(note);jdbc.queryForList("SELECT username FROM users WHERE username=? FOR UPDATE",owner);
  var rows=jdbc.queryForList("SELECT * FROM ranking_reward_redemptions WHERE owner_username=? AND id=? FOR UPDATE",owner,id);
  if(rows.isEmpty())throw RankingPointsService.conflict("兑换记录不存在");var order=rows.getFirst();
  if(!order.get("status").equals("CANCEL_REQUESTED"))throw RankingPointsService.conflict("申请已处理或订单状态已变化");
  String student=(String)order.get("student_id");points.lock(owner,student);
  long balanceBefore=points.raw(owner,student);
  var stocks=order.get("reward_id")==null?java.util.List.<java.util.Map<String,Object>>of():jdbc.queryForList("SELECT stock FROM ranking_rewards WHERE id=? AND owner_username=? FOR UPDATE",order.get("reward_id"),owner);
  int stockBefore=stocks.isEmpty()?0:((Number)stocks.getFirst().get("stock")).intValue();
  if(approved){
   long before=balanceBefore;int delta=((Number)order.get("points_spent")).intValue();if(before+delta>999999999L)throw RankingPointsService.conflict("退款后超出积分上限，请先核实当前余额");
   var scope=jdbc.queryForList("SELECT camp_id,class_id FROM ranking_students WHERE owner_username=? AND student_id=? ORDER BY camp_id,class_id LIMIT 1",owner,student);
   if(scope.isEmpty())throw RankingPointsService.conflict("学员归属缺失，请联系管理员");
   jdbc.update("INSERT INTO ranking_point_adjustments(owner_username,camp_id,class_id,student_id,points,balance_before,balance_after,reason,actor) VALUES(?,?,?,?,?,?,?,?,?)",owner,scope.getFirst().get("camp_id"),scope.getFirst().get("class_id"),student,delta,before,before+delta,"兑换退款#"+id,actor);
   points.record(owner,student,"REFUND","refund:"+id,before,before+delta,"兑换退款#"+id+"："+explanation,actor);
   if(order.get("reward_id")!=null)jdbc.update("UPDATE ranking_rewards SET stock=stock+1,updated_at=CURRENT_TIMESTAMP(6) WHERE id=? AND owner_username=?",order.get("reward_id"),owner);
  }
  jdbc.update("UPDATE ranking_reward_redemptions SET status=? WHERE id=? AND owner_username=?",approved?"CANCELLED":"PENDING",id,owner);
  jdbc.update("UPDATE ranking_redemption_cancellations SET decision_note=?,decided_by=?,decided_at=CURRENT_TIMESTAMP(6) WHERE redemption_id=? AND owner_username=?",explanation,actor,id,owner);
  return Map.of("redemptionId",id,"status",approved?"CANCELLED":"PENDING","refunded",approved,"studentId",student,"refundedPoints",approved?order.get("points_spent"):0,"balanceBefore",balanceBefore,"balanceAfter",points.raw(owner,student),"stockBefore",stockBefore,"stockAfter",stockBefore+(approved&&!stocks.isEmpty()?1:0),"reason",explanation);
 }
 @Transactional public void requestRecovery(String phone,String student,String note){
  if(phone==null||!phone.matches("^1\\d{10}$")||student==null||student.isBlank())return;
  var rows=jdbc.queryForList("SELECT owner_username,student_id,student_name FROM ranking_student_accounts WHERE phone=? AND student_id=? AND enabled=TRUE",phone,student);
  for(var account:rows){String owner=(String)account.get("owner_username");jdbc.queryForList("SELECT username FROM users WHERE username=? FOR UPDATE",owner);
   Integer count=jdbc.queryForObject("SELECT COUNT(*) FROM ranking_recovery_requests WHERE owner_username=? AND student_id=? AND status IN ('PENDING_VERIFY','VERIFIED')",Integer.class,owner,student);
   if(count==0)jdbc.update("INSERT INTO ranking_recovery_requests(owner_username,student_id,student_name,reason) VALUES(?,?,?,?)",owner,student,account.get("student_name"),note==null||note.isBlank()?"申请找回学生账号":note.substring(0,Math.min(500,note.length())));
  }
 }
 public List<Map<String,Object>> recoveries(String owner,boolean admin){return admin?jdbc.queryForList("SELECT * FROM ranking_recovery_requests ORDER BY id DESC LIMIT 200"):jdbc.queryForList("SELECT * FROM ranking_recovery_requests WHERE owner_username=? ORDER BY id DESC LIMIT 200",owner);}
 @Transactional public Map<String,Object> verify(String owner,long id,boolean accepted,String note){
  String explanation=reason(note);int n=jdbc.update("UPDATE ranking_recovery_requests SET status=?,verification_note=?,verified_by=?,updated_at=CURRENT_TIMESTAMP(6) WHERE id=? AND owner_username=? AND status='PENDING_VERIFY'",accepted?"VERIFIED":"REJECTED",explanation,owner,id,owner);
  if(n!=1)throw RankingPointsService.conflict("申请不存在或已处理");return Map.of("id",id,"status",accepted?"VERIFIED":"REJECTED");
 }
 @Transactional public Map<String,Object> resolve(long id,String password,String actor){
  var rows=jdbc.queryForList("SELECT * FROM ranking_recovery_requests WHERE id=? AND status='VERIFIED' FOR UPDATE",id);if(rows.isEmpty())throw RankingPointsService.conflict("申请未通过教师核实或已处理");var row=rows.getFirst();
  auth.resetPassword((String)row.get("owner_username"),(String)row.get("student_id"),password);
  jdbc.update("UPDATE ranking_recovery_requests SET status='RESOLVED',resolved_by=?,updated_at=CURRENT_TIMESTAMP(6) WHERE id=?",actor,id);
  return Map.of("id",id,"status","RESOLVED","owner",row.get("owner_username"),"studentId",row.get("student_id"));
 }
}
