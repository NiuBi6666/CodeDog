package cn.codedog.service;
import cn.codedog.model.RankingPayload;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import java.sql.Timestamp;
import java.time.Instant;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
@Service public class RankingImportWorkflow {
 private final JdbcTemplate jdbc;private final ObjectMapper json;private final RankingImportService imports;private final RankingPointsService points;
 public RankingImportWorkflow(JdbcTemplate j,ObjectMapper m,RankingImportService i,RankingPointsService p){jdbc=j;json=m;imports=i;points=p;}
 private String encode(Object o){try{return json.writeValueAsString(o);}catch(Exception e){throw new IllegalStateException(e);}}
 private String fingerprint(String owner){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(encode(jdbc.queryForList("SELECT camp_id,class_id,lesson_id,student_id,content_hash,import_batch_id FROM ranking_lesson_results WHERE owner_username=? ORDER BY camp_id,class_id,lesson_id,student_id",owner)).getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
 @Transactional public Map<String,Object> preview(String owner,RankingPayload payload){
  List<Map<String,Object>> rows=new ArrayList<>();Set<String> keys=new HashSet<>();
  for(var c:payload.classes())for(var l:c.lessons())for(var s:l.students()){
   String error="";Integer score=null;
   try{if(s.studentId()==null||s.studentId().isBlank()||s.studentName()==null||s.studentName().isBlank())throw new IllegalArgumentException("缺少学生ID或姓名");if(!keys.add(c.classId()+"|"+l.lessonId()+"|"+s.studentId()))throw new IllegalArgumentException("文件中存在重复课程结果");score=RankingScore.calculatePerfectTasks(s.completionRate(),s.inclass(),s.homework()).totalPoints();}catch(RuntimeException e){error=e.getMessage()==null?"数据不合法":e.getMessage();}
   var old=jdbc.queryForList("SELECT total_points FROM ranking_lesson_results WHERE owner_username=? AND camp_id=? AND class_id=? AND lesson_id=? AND student_id=?",owner,payload.campId(),c.classId(),l.lessonId(),s.studentId());
   rows.add(Map.of("studentId",s.studentId()==null?"":s.studentId(),"name",s.studentName()==null?"":s.studentName(),"lesson",l.lessonName(),"before",old.isEmpty()?0:old.getFirst().get("total_points"),"after",score==null?0:score,"error",error));
  }
  if(rows.isEmpty()||rows.size()>50000)throw RankingPointsService.conflict("导入需包含1至50000条结果");
  String id=UUID.randomUUID().toString();jdbc.update("INSERT INTO ranking_import_previews(id,owner_username,payload_json,fingerprint,expires_at) VALUES(?,?,?,?,?)",id,owner,encode(payload),fingerprint(owner),Timestamp.from(Instant.now().plusSeconds(1800)));
  return Map.of("previewId",id,"rows",rows,"expiresInSeconds",1800,"hasErrors",rows.stream().anyMatch(r->!r.get("error").equals("")));
 }
 @Transactional public RankingPayload.ImportSummary confirm(String owner,String id){
  jdbc.queryForList("SELECT username FROM users WHERE username=? FOR UPDATE",owner);
  var rows=jdbc.queryForList("SELECT * FROM ranking_import_previews WHERE id=? AND owner_username=? FOR UPDATE",id,owner);
  if(rows.isEmpty())throw RankingPointsService.conflict("导入预览不存在");var r=rows.getFirst();
  if(r.get("confirmed_batch_id")!=null)throw RankingPointsService.conflict("该预览已经导入，请查看批次记录");
  if(((Timestamp)r.get("expires_at")).toInstant().isBefore(Instant.now()))throw RankingPointsService.conflict("预览已过期，请重新上传");
  if(!fingerprint(owner).equals(r.get("fingerprint")))throw RankingPointsService.conflict("数据已被其他操作更新，请重新预览");
  RankingPayload payload;try{payload=json.readValue((String)r.get("payload_json"),RankingPayload.class);}catch(Exception e){throw new IllegalStateException(e);}
  for(var c:payload.classes())for(var l:c.lessons())for(var s:l.students()){if(s.studentId()==null||s.studentId().isBlank()||s.studentName()==null||s.studentName().isBlank())throw RankingPointsService.conflict("预览包含无效数据，请重新上传");RankingScore.calculatePerfectTasks(s.completionRate(),s.inclass(),s.homework());}
  var result=imports.importPerfectTaskData(payload,"XLSX","确认预览导入",owner);
  if(result.rejectedRows()>0)throw RankingPointsService.conflict("导入校验未全部通过，整批未写入，请重新预览检查数据");
  jdbc.update("UPDATE ranking_import_previews SET confirmed_batch_id=? WHERE id=?",result.batchId(),id);return result;
 }
 public List<Map<String,Object>> batches(String owner){return jdbc.queryForList("SELECT id,source_type,source_name,status,received_rows,changed_rows,rejected_rows,created_at,reversed_at FROM ranking_import_batches WHERE owner_username=? ORDER BY id DESC LIMIT 100",owner);}
 @Transactional public Map<String,Object> reverse(String owner,long id,String reason){
  if(reason==null||reason.isBlank()||reason.length()>255)throw RankingPointsService.conflict("请填写1至255字撤销原因");
  jdbc.queryForList("SELECT username FROM users WHERE username=? FOR UPDATE",owner);
  var batches=jdbc.queryForList("SELECT reversed_at FROM ranking_import_batches WHERE owner_username=? AND id=? FOR UPDATE",owner,id);
  if(batches.isEmpty()||batches.getFirst().get("reversed_at")!=null)throw RankingPointsService.conflict("批次不存在或已撤销");
  var changes=jdbc.queryForList("SELECT * FROM ranking_import_changes WHERE owner_username=? AND batch_id=? ORDER BY student_id,id",owner,id);
  if(changes.isEmpty())throw RankingPointsService.conflict("历史批次无可靠快照，不能自动撤销");
  for(var change:changes){String s=(String)change.get("student_id");points.lock(owner,s);
   var current=jdbc.queryForList("SELECT content_hash,import_batch_id FROM ranking_lesson_results WHERE owner_username=? AND camp_id=? AND class_id=? AND lesson_id=? AND student_id=? FOR UPDATE",owner,change.get("camp_id"),change.get("class_id"),change.get("lesson_id"),s);
   if(current.isEmpty()||((Number)current.getFirst().get("import_batch_id")).longValue()!=id||!current.getFirst().get("content_hash").equals(change.get("after_hash")))throw RankingPointsService.conflict("学员 "+s+" 的记录已被后续批次更新，整批未撤销");
   long before=points.raw(owner,s);
   Object[] key={owner,change.get("camp_id"),change.get("class_id"),change.get("lesson_id"),s};
   jdbc.update("DELETE FROM ranking_lesson_results WHERE owner_username=? AND camp_id=? AND class_id=? AND lesson_id=? AND student_id=?",key);
   if(change.get("before_json")!=null){try{
    Map<String,Object> old=json.readValue((String)change.get("before_json"),new com.fasterxml.jackson.core.type.TypeReference<Map<String,Object>>(){});
    old.remove("updated_at");String columns=String.join(",",old.keySet());String marks=String.join(",",Collections.nCopies(old.size(),"?"));
    jdbc.update("INSERT INTO ranking_lesson_results("+columns+") VALUES("+marks+")",old.values().toArray());
   }catch(Exception e){throw new IllegalStateException("恢复课程快照失败",e);}}
   long after=points.raw(owner,s);if(after<0)throw RankingPointsService.conflict("学员 "+s+" 撤销后积分不足，整批未撤销，请先处理兑换");
   points.record(owner,s,"IMPORT_REVERSAL","reverse:"+change.get("id"),before,after,reason,owner);
  }
  jdbc.update("UPDATE ranking_import_batches SET status='REVERSED',reversed_at=CURRENT_TIMESTAMP(6) WHERE id=? AND owner_username=?",id,owner);
  for(String camp:changes.stream().map(c->(String)c.get("camp_id")).distinct().toList())new RankingBoardService(jdbc).refreshSnapshots(owner,camp);
  return Map.of("batchId",id,"reversedRows",changes.size());
 }
}
