package cn.codedog.service;
import java.util.*;import java.sql.Timestamp;import java.time.Instant;
import org.springframework.stereotype.Service;import org.springframework.jdbc.core.JdbcTemplate;import org.springframework.transaction.annotation.Transactional;import com.fasterxml.jackson.databind.ObjectMapper;
@Service public class RankingOwnershipService {
 private final JdbcTemplate j;private final RankingPointsService points;private final PasswordRecoveryCipher cipher;private final ObjectMapper json;
 public RankingOwnershipService(JdbcTemplate j,RankingPointsService p,PasswordRecoveryCipher c,ObjectMapper m){this.j=j;points=p;cipher=c;json=m;}
 public static void requireCurrent(JdbcTemplate j,String owner,String student,boolean establish){
  if(establish)j.update("INSERT INTO ranking_current_ownership(student_id,owner_username,state) VALUES(?,?,'ACTIVE') ON DUPLICATE KEY UPDATE student_id=student_id",student,owner);
  var rows=j.queryForList("SELECT owner_username,state FROM ranking_current_ownership WHERE student_id=?",student);
  if(!rows.isEmpty()&&(!"ACTIVE".equals(rows.getFirst().get("state"))||!owner.equals(rows.getFirst().get("owner_username"))))throw RankingPointsService.conflict("学员已交接或归属冲突，不能由当前老师写入，请联系最高管理员");
 }
 public List<Map<String,Object>> ownerships(){return j.queryForList("SELECT o.*,MAX(s.student_name) student_name FROM ranking_current_ownership o LEFT JOIN ranking_students s ON s.student_id=o.student_id GROUP BY o.student_id,o.owner_username,o.state,o.version_no,o.updated_at ORDER BY o.student_id");}
 public List<Map<String,Object>> teachers(){return j.queryForList("SELECT username,display_name,enabled,is_admin FROM users ORDER BY username");}
 private String encode(Object v){try{return json.writeValueAsString(v);}catch(Exception e){throw new IllegalStateException(e);}}
 private void target(String from,String to){if(from==null||to==null||from.equals(to)||j.queryForObject("SELECT COUNT(*) FROM users WHERE username=? AND enabled=TRUE",Integer.class,to)!=1)throw RankingPointsService.conflict("请选择不同且可用的接收老师");}
 private Map<String,Object> snapshot(String from,String student){var rows=j.queryForList("SELECT * FROM ranking_current_ownership WHERE student_id=?",student);if(rows.isEmpty())throw RankingPointsService.conflict("学员没有当前归属");requireCurrent(j,from,student,false);return Map.of("studentId",student,"version",rows.getFirst().get("version_no"),"balance",points.raw(from,student));}
 @Transactional public Map<String,Object> preview(String from,String to,List<String> ids,boolean offboard,String reason,String actor){
  target(from,to);if(reason==null||reason.isBlank()||reason.length()>255)throw RankingPointsService.conflict("请填写1至255字交接原因");
  if(offboard&&j.queryForObject("SELECT COUNT(*) FROM users WHERE username=? AND is_admin=TRUE",Integer.class,from)>0)throw RankingPointsService.conflict("最高管理员不能离职交接");
  var students=offboard?j.queryForList("SELECT student_id FROM ranking_current_ownership WHERE owner_username=? AND state='ACTIVE' ORDER BY student_id",String.class,from):new ArrayList<>(new TreeSet<>(ids==null?List.of():ids));
  if(students.isEmpty()&&!offboard)throw RankingPointsService.conflict("请选择学员");
  if(j.queryForObject("SELECT COUNT(*) FROM ranking_current_ownership WHERE state='CONFLICT' AND student_id IN (SELECT student_id FROM ranking_students WHERE owner_username=?)",Integer.class,from)>0&&offboard)throw RankingPointsService.conflict("请先处理原老师名下的归属冲突");
  var rows=students.stream().map(s->snapshot(from,s)).toList();String id=UUID.randomUUID().toString();var payload=Map.of("from",from,"to",to,"rows",rows,"offboard",offboard,"reason",reason);
  j.update("INSERT INTO ranking_transfer_previews(id,payload_json,created_by,expires_at) VALUES(?,?,?,?)",id,encode(payload),actor,Timestamp.from(Instant.now().plusSeconds(1800)));
  return Map.of("previewId",id,"from",from,"to",to,"rows",rows,"offboard",offboard,"reason",reason);
 }
 @Transactional public Map<String,Object> confirm(String id,String actor){
  var list=j.queryForList("SELECT * FROM ranking_transfer_previews WHERE id=? AND created_by=? FOR UPDATE",id,actor);if(list.isEmpty())throw RankingPointsService.conflict("预览不存在");var row=list.getFirst();if(row.get("confirmed_at")!=null||((Timestamp)row.get("expires_at")).toInstant().isBefore(Instant.now()))throw RankingPointsService.conflict("预览已确认或已过期");
  Map<String,Object> p;try{p=json.readValue((String)row.get("payload_json"),Map.class);}catch(Exception e){throw new IllegalStateException(e);}String from=(String)p.get("from"),to=(String)p.get("to");
  for(String owner:new TreeSet<>(List.of(from,to)))j.queryForList("SELECT username FROM users WHERE username=? FOR UPDATE",owner);target(from,to);
  var items=(List<Map<String,Object>>)p.get("rows");boolean offboard=Boolean.TRUE.equals(p.get("offboard"));
  if(offboard&&j.queryForObject("SELECT COUNT(*) FROM ranking_current_ownership WHERE owner_username=? AND state='ACTIVE'",Integer.class,from)!=items.size())throw RankingPointsService.conflict("交接名单已变化，请重新预览");
  for(var item:items){String student=(String)item.get("studentId");j.queryForList("SELECT student_id FROM ranking_current_ownership WHERE student_id=? FOR UPDATE",student);var now=snapshot(from,student);if(((Number)now.get("version")).longValue()!=((Number)item.get("version")).longValue()||((Number)now.get("balance")).longValue()!=((Number)item.get("balance")).longValue())throw RankingPointsService.conflict("学员归属或余额已变化，请重新预览");transfer(from,to,student,(String)p.get("reason"),actor);}
  if(offboard){j.update("UPDATE users SET enabled=FALSE WHERE username=? AND is_admin=FALSE",from);j.update("UPDATE ranking_extension_devices SET revoked_at=CURRENT_TIMESTAMP(6) WHERE owner_username=? AND revoked_at IS NULL",from);}
  j.update("UPDATE ranking_transfer_previews SET confirmed_at=CURRENT_TIMESTAMP(6) WHERE id=?",id);return Map.of("previewId",id,"from",from,"to",to,"count",items.size(),"offboard",offboard,"rows",items);
 }
 private void adjustment(String owner,String student,long delta,String key,String type,String reason,String actor){points.lock(owner,student);long before=points.raw(owner,student);long after=before+delta;if(after<0||after>999999999)throw RankingPointsService.conflict("交接后余额超出范围");var scopes=j.queryForList("SELECT camp_id,class_id FROM ranking_students WHERE owner_username=? AND student_id=? ORDER BY camp_id,class_id LIMIT 1",owner,student);if(scopes.isEmpty())throw RankingPointsService.conflict("缺少学员名单");var s=scopes.getFirst();j.update("INSERT INTO ranking_point_adjustments(owner_username,camp_id,class_id,student_id,points,balance_before,balance_after,reason,actor) VALUES(?,?,?,?,?,?,?,?,?)",owner,s.get("camp_id"),s.get("class_id"),student,delta,before,after,reason,actor);points.record(owner,student,type,key,before,after,reason,actor);}
 private void transfer(String from,String to,String student,String reason,String actor){
  var dest=j.queryForList("SELECT id FROM ranking_student_accounts WHERE owner_username=? AND student_id=?",to,student);if(!dest.isEmpty())throw RankingPointsService.conflict("接收老师已存在该学员账号，请先处理冲突");
  String name=j.queryForObject("SELECT MAX(student_name) FROM ranking_students WHERE owner_username=? AND student_id=?",String.class,from,student);long balance=points.raw(from,student);if(points.raw(to,student)!=0)throw RankingPointsService.conflict("接收老师名下存在历史余额，请先核实");
  j.update("INSERT INTO ranking_camps(owner_username,camp_id,camp_name) VALUES(?,'__transfers__','交接学员') ON DUPLICATE KEY UPDATE camp_id=camp_id",to);
  j.update("INSERT INTO ranking_classes(owner_username,camp_id,class_id,class_name) VALUES(?,'__transfers__','__transfers__','交接学员') ON DUPLICATE KEY UPDATE class_id=class_id",to);
  j.update("INSERT INTO ranking_students(owner_username,camp_id,class_id,student_id,student_name) VALUES(?,'__transfers__','__transfers__',?,?) ON DUPLICATE KEY UPDATE student_name=VALUES(student_name)",to,student,name);
  String id=UUID.randomUUID().toString();adjustment(from,student,-balance,id,"TRANSFER_OUT",reason,actor);adjustment(to,student,balance,id,"TRANSFER_IN",reason,actor);
  var accounts=j.queryForList("SELECT id,password_ciphertext FROM ranking_student_accounts WHERE owner_username=? AND student_id=? FOR UPDATE",from,student);
  if(!accounts.isEmpty()){var a=accounts.getFirst();String ciphertext=(String)a.get("password_ciphertext");if(ciphertext!=null&&!ciphertext.isBlank())ciphertext=cipher.encrypt(cipher.decrypt(ciphertext,"student:"+from+":"+student),"student:"+to+":"+student);j.update("UPDATE ranking_student_accounts SET owner_username=?,password_ciphertext=?,session_version=session_version+1 WHERE id=?",to,ciphertext,a.get("id"));}
  j.update("UPDATE ranking_recovery_requests SET owner_username=?,status='PENDING_VERIFY',verified_by=NULL,verification_note=NULL,updated_at=CURRENT_TIMESTAMP(6) WHERE owner_username=? AND student_id=? AND status IN ('PENDING_VERIFY','VERIFIED')",to,from,student);
  j.update("UPDATE ranking_current_ownership SET owner_username=?,version_no=version_no+1,updated_at=CURRENT_TIMESTAMP(6) WHERE student_id=?",to,student);
  j.update("INSERT INTO ranking_transfers(id,student_id,from_owner,to_owner,points,reason,actor) VALUES(?,?,?,?,?,?,?)",id,student,from,to,balance,reason,actor);
 }
 @Transactional public Map<String,Object> resolve(String student,String owner,String reason,String actor){
  if(reason==null||reason.isBlank())throw RankingPointsService.conflict("请填写归属核实依据");if(j.queryForObject("SELECT COUNT(*) FROM users WHERE username=? AND enabled=TRUE",Integer.class,owner)!=1)throw RankingPointsService.conflict("请选择有效老师");var rows=j.queryForList("SELECT * FROM ranking_current_ownership WHERE student_id=? FOR UPDATE",student);if(rows.isEmpty()||!rows.getFirst().get("state").equals("CONFLICT"))throw RankingPointsService.conflict("没有待处理冲突");if(j.queryForObject("SELECT COUNT(*) FROM ranking_students WHERE owner_username=? AND student_id=?",Integer.class,owner,student)==0)throw RankingPointsService.conflict("请选择现有关系中的老师");
  j.update("UPDATE ranking_current_ownership SET owner_username=?,state='ACTIVE',version_no=version_no+1,updated_at=CURRENT_TIMESTAMP(6) WHERE student_id=?",owner,student);j.update("UPDATE ranking_student_accounts SET session_version=session_version+1 WHERE student_id=?",student);return Map.of("studentId",student,"owner",owner,"reason",reason,"balancesMerged",false);
 }
}
