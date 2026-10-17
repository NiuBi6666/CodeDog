package cn.codedog.service;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.util.*;
@Service
public class RankingPointsService {
 @org.springframework.beans.factory.annotation.Value("${codedog.maintenance-enabled:true}") private boolean maintenanceEnabled=true;
 private final JdbcTemplate jdbc;
 public RankingPointsService(JdbcTemplate jdbc){this.jdbc=jdbc;}
 public long raw(String owner,String student){
  return jdbc.queryForObject("SELECT COALESCE((SELECT SUM(total_points) FROM ranking_lesson_results WHERE owner_username=? AND student_id=?),0)+COALESCE((SELECT SUM(points) FROM ranking_point_adjustments WHERE owner_username=? AND student_id=?),0)-COALESCE((SELECT SUM(points_spent) FROM ranking_reward_redemptions WHERE owner_username=? AND student_id=?),0)",Long.class,owner,student,owner,student,owner,student);
 }
 public int available(String owner,String student){return (int)Math.min(999999999L,Math.max(0,raw(owner,student)));}
 public void lock(String owner,String student){jdbc.queryForList("SELECT username FROM users WHERE username=? FOR UPDATE",owner);jdbc.query("SELECT student_id FROM ranking_students WHERE owner_username=? AND student_id=? ORDER BY camp_id,class_id FOR UPDATE",(rs,n)->rs.getString(1),owner,student);}
 public void record(String owner,String student,String type,String key,long before,long after,String reason,String actor){
  jdbc.update("INSERT INTO ranking_point_ledger(owner_username,student_id,event_type,business_key,delta,balance_before,balance_after,reason,actor) VALUES(?,?,?,?,?,?,?,?,?)",owner,student,type,key,after-before,before,after,reason,actor);
 }
 public Map<String,Object> ledger(String owner,String student,int page){
  int safe=Math.max(0,page);
  var items=jdbc.queryForList("SELECT id,event_type,delta,balance_before,balance_after,reason,actor,created_at FROM ranking_point_ledger WHERE owner_username=? AND student_id=? ORDER BY id DESC LIMIT 30 OFFSET ?",owner,student,safe*30L);
  return Map.of("items",items,"page",safe,"total",jdbc.queryForObject("SELECT COUNT(*) FROM ranking_point_ledger WHERE owner_username=? AND student_id=?",Long.class,owner,student),"availablePoints",available(owner,student));
 }
 @Transactional public Map<String,Object> reconcile(String owner){
  var students=jdbc.queryForList("SELECT DISTINCT student_id FROM ranking_students WHERE owner_username=? ORDER BY student_id",String.class,owner);
  int errors=0;
  for(String student:students){lock(owner,student);long source=raw(owner,student);long ledger=jdbc.queryForObject("SELECT COALESCE(SUM(delta),0) FROM ranking_point_ledger WHERE owner_username=? AND student_id=?",Long.class,owner,student);
   boolean ok=source==ledger&&source>=0&&source<=999999999L;if(!ok)errors++;
   jdbc.update("INSERT INTO ranking_reconciliations(owner_username,student_id,source_balance,ledger_balance,result) VALUES(?,?,?,?,?)",owner,student,source,ledger,ok?"OK":"MISMATCH");
  }return Map.of("checked",students.size(),"anomalies",errors);
 }

 @Transactional public Map<String,Object> repair(String owner,String student,long expectedSource,long expectedLedger,String reason){
  if(reason==null||reason.isBlank()||reason.length()>255)throw conflict("请填写1至255字修复原因");
  lock(owner,student);long source=raw(owner,student);long ledger=jdbc.queryForObject("SELECT COALESCE(SUM(delta),0) FROM ranking_point_ledger WHERE owner_username=? AND student_id=?",Long.class,owner,student);
  if(source!=expectedSource||ledger!=expectedLedger)throw conflict("数据已变化，请重新对账后处理");
  if(source<0||source>999999999L)throw conflict("业务余额超出范围，请先核实并处理来源记录");
  if(source==ledger)throw conflict("当前积分已一致，无需修复");
  record(owner,student,"LEDGER_REPAIR",UUID.randomUUID().toString(),ledger,source,"台账修复（不改变可用积分）："+reason,owner);
  jdbc.update("INSERT INTO ranking_reconciliations(owner_username,student_id,source_balance,ledger_balance,result) VALUES(?,?,?,?,'OK')",owner,student,source,source);
  return Map.of("studentId",student,"before",ledger,"after",source,"reason",reason);
 }
 public List<Map<String,Object>> reports(String owner){return jdbc.queryForList("SELECT * FROM ranking_reconciliations WHERE owner_username=? ORDER BY id DESC LIMIT 200",owner);}
 @Scheduled(cron="0 15 2 * * *",zone="Asia/Shanghai")

 @Transactional public void nightly(){
  if(!maintenanceEnabled)return;
  for(String owner:jdbc.queryForList("SELECT DISTINCT owner_username FROM ranking_students",String.class)){
   var result=reconcile(owner);
   jdbc.update("INSERT INTO audit_log(action,owner_username,actor_type,actor_id,actor_name,request_id,http_method,request_path,status_code,duration_ms,module,event_type,event_code,result,detail_json,ip_address,created_at) VALUES(?,?,'SYSTEM','SYSTEM','系统任务',?,'SYSTEM','scheduled://points-reconciliation',200,0,'rankings','execute','ranking_reconciliation_scheduled','success',?,'SYSTEM',CURRENT_TIMESTAMP(6))","ranking_reconciliation_scheduled",owner,UUID.randomUUID().toString(),"{\"checked\":"+result.get("checked")+",\"anomalies\":"+result.get("anomalies")+"}");
  }
 }
 public static ResponseStatusException conflict(String message){return new ResponseStatusException(HttpStatus.CONFLICT,message);}
}
