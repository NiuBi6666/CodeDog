package cn.codedog.ranking;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
public class RankingService {
  private static final SecureRandom RANDOM = new SecureRandom();
  private static final int MAX_ROWS = 50_000;
  private final JdbcTemplate jdbc;
  public RankingService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

  @Transactional
  public RankingPayload.ImportSummary importData(RankingPayload payload, String sourceType, String sourceName, String ownerValue) {
    String owner = text(ownerValue, "数据所属用户", 50);
    String campId = text(payload == null ? null : payload.campId(), "营期 ID", 100);
    String campName = text(payload.campName(), "营期名称", 160);
    List<RankingPayload.ClassData> classes = safe(payload.classes());
    int received = countRows(classes);
    if (classes.isEmpty() || received == 0) throw invalid("没有可导入的学员结果");
    if (received > MAX_ROWS) throw invalid("单次导入不能超过 " + MAX_ROWS + " 行");
    long batchId = createBatch(owner, sourceType, sourceName, campId, received, owner);
    Instant scoreEventAt = Instant.now();
    upsertCamp(owner, campId, campName);
    int changed = 0, unchanged = 0, rejected = 0;
    List<RankingPayload.RowError> errors = new ArrayList<>();
    for (RankingPayload.ClassData clazz : classes) {
      String classId;
      try {
        classId = text(clazz.classId(), "班级 ID", 100);
        upsertClass(owner, campId, classId, text(clazz.className(), "班级名称", 160));
      } catch (RuntimeException error) {
        rejected += countRows(List.of(clazz)); addError(errors, clazz.classId(), "", "", message(error)); continue;
      }
      for (RankingPayload.LessonData lesson : safe(clazz.lessons())) {
        String lessonId;
        try {
          lessonId = text(lesson.lessonId(), "课节 ID", 100);
          upsertLesson(owner, campId, classId, lessonId, text(lesson.lessonName(), "课节名称", 200), lesson.lessonOrder(), lesson.endedAt());
        } catch (RuntimeException error) {
          rejected += safe(lesson.students()).size(); addError(errors, classId, lesson.lessonId(), "", message(error)); continue;
        }
        for (RankingPayload.StudentResult student : safe(lesson.students())) {
          try {
            String studentId = text(student.studentId(), "学员 ID", 100);
            String studentName = text(student.studentName(), "学员姓名", 100);
            RankingScore.Score score = RankingScore.calculate(student.completionRate(), student.inclass(), student.homework());
            upsertStudent(owner, campId, classId, studentId, studentName);
            String hash = sha256(studentName + "|" + score);
            ExistingResult existing = existingResult(owner, campId, classId, lessonId, studentId);
            if (existing != null && hash.equals(existing.hash())) unchanged++;
            else {
              upsertResult(owner, campId, classId, lessonId, studentId, score, hash, batchId);
              if (existing == null || existing.totalPoints() != score.totalPoints()) markScoreReached(owner, campId, classId, studentId, scoreEventAt);
              changed++;
            }
          } catch (RuntimeException error) {
            rejected++; addError(errors, classId, lessonId, student.studentId(), message(error));
          }
        }
      }
    }
    jdbc.update("UPDATE ranking_import_batches SET status=?, changed_rows=?, rejected_rows=?, completed_at=CURRENT_TIMESTAMP(6) WHERE id=? AND owner_username=?",
      rejected == received ? "FAILED" : rejected > 0 ? "PARTIAL" : "COMPLETED", changed, rejected, batchId, owner);
    refreshSnapshots(owner, campId);
    return new RankingPayload.ImportSummary(batchId, received, changed, unchanged, rejected, List.copyOf(errors));
  }

  public RankingPayload.Catalog catalog() { return catalog(null); }
  public RankingPayload.Catalog catalog(String teacherValue) {
    TeacherOwner teacher = resolveTeacher(teacherValue);
    Map<String, MutableCamp> values = new LinkedHashMap<>();
    jdbc.query("SELECT c.camp_id,c.camp_name,k.class_id,k.class_name FROM ranking_camps c LEFT JOIN ranking_classes k ON k.owner_username=c.owner_username AND k.camp_id=c.camp_id WHERE c.owner_username=? ORDER BY c.updated_at DESC,c.camp_name,k.class_name", rs -> {
      MutableCamp camp = values.computeIfAbsent(rs.getString(1), id -> new MutableCamp(id, get(rs, 2)));
      if (rs.getString(3) != null) camp.classes.add(new RankingPayload.ClassOption(rs.getString(3), rs.getString(4)));
    }, teacher.username());
    List<RankingPayload.CampOption> camps = values.values().stream().map(c -> new RankingPayload.CampOption(c.id, c.name, List.copyOf(c.classes))).toList();
    return new RankingPayload.Catalog(teacher.teacherId(), teacher.username(), camps, latestUpdate(teacher.username()));
  }

  public RankingPayload.Board board(String campValue, String classValue, String scopeValue) { return board(null, campValue, classValue, scopeValue); }
  public RankingPayload.Board board(String teacherValue, String campValue, String classValue, String scopeValue) {
    String owner = resolveTeacher(teacherValue).username();
    String campId = text(campValue, "营期 ID", 100), scope = "camp".equalsIgnoreCase(scopeValue) ? "camp" : "class";
    String classId = scope.equals("class") ? text(classValue, "班级 ID", 100) : "";
    String campName = lookup("SELECT camp_name FROM ranking_camps WHERE owner_username=? AND camp_id=?", owner, campId);
    if (campName == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "排行榜营期不存在");
    String className = scope.equals("class") ? lookup("SELECT class_name FROM ranking_classes WHERE owner_username=? AND camp_id=? AND class_id=?", owner, campId, classId) : "全部班级";
    if (className == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "排行榜班级不存在");
    List<AggregateRow> rows = aggregateRows(owner, campId, classId, scope);
    LocalDate baselineDate = previousSnapshotDate(owner, campId, classId, scope);
    Map<String,Integer> previousRanks = previousRanks(owner, campId, classId, scope, baselineDate);
    List<RankingPayload.Entry> entries = new ArrayList<>();
    for (AggregateRow row : rows) {
      int rank = entries.size()+1, level = level(row.totalPoints());
      Integer previousRank = previousRanks.get(row.studentId());
      int rankChange = previousRank == null ? 0 : previousRank-rank;
      entries.add(new RankingPayload.Entry(rank, row.studentId(), row.studentName(), row.classId(), row.className(),
        row.totalPoints(), row.completionPoints(), row.inclassPoints(), row.homeworkPoints(), row.lessonCount(),
        level, levelName(level), row.scoreReachedAt(), row.accuracyBasisPoints()/100.0, previousRank, rankChange,
        previousRank == null ? "NEW" : rankChange > 0 ? "UP" : rankChange < 0 ? "DOWN" : "SAME"));
    }
    return new RankingPayload.Board(campId, campName, classId, className, scope, entries.size(), latestUpdate(owner), baselineDate, List.copyOf(entries));
  }

  private List<AggregateRow> aggregateRows(String owner, String campId, String classId, String scope) {
    String filter = scope.equals("class") ? " AND s.class_id=?" : "";
    List<Object> args = new ArrayList<>(List.of(owner, campId)); if (scope.equals("class")) args.add(classId);
    String sql = """
      SELECT s.student_id,MAX(s.student_name) student_name,MIN(s.class_id) class_id,MIN(c.class_name) class_name,
      COALESCE(SUM(r.total_points),0) total_points,COALESCE(SUM(r.completion_points),0) completion_points,
      COALESCE(SUM(r.inclass_points),0) inclass_points,COALESCE(SUM(r.homework_points),0) homework_points,
      COUNT(r.lesson_id) lesson_count,MAX(s.score_reached_at) score_reached_at,
      CASE WHEN COUNT(r.lesson_id)=0 THEN 0
      ELSE ROUND(50.0*SUM(r.inclass_points+r.homework_points)/COUNT(r.lesson_id)) END accuracy_basis_points
      FROM ranking_students s JOIN ranking_classes c ON c.owner_username=s.owner_username AND c.camp_id=s.camp_id AND c.class_id=s.class_id
      LEFT JOIN ranking_lesson_results r ON r.owner_username=s.owner_username AND r.camp_id=s.camp_id AND r.class_id=s.class_id AND r.student_id=s.student_id
      WHERE s.owner_username=? AND s.camp_id=?
      """ + filter + " GROUP BY s.student_id ORDER BY total_points DESC,score_reached_at ASC,accuracy_basis_points DESC,homework_points DESC,inclass_points DESC,completion_points DESC,s.student_id";
    return jdbc.query(sql, (rs,n) -> new AggregateRow(rs.getString("student_id"),rs.getString("student_name"),
      rs.getString("class_id"),rs.getString("class_name"),rs.getInt("total_points"),rs.getInt("completion_points"),
      rs.getInt("inclass_points"),rs.getInt("homework_points"),rs.getInt("lesson_count"),
      rs.getTimestamp("score_reached_at").toInstant(),rs.getInt("accuracy_basis_points")),args.toArray());
  }

  private LocalDate previousSnapshotDate(String owner,String campId,String classId,String scope) {
    List<LocalDate> dates=jdbc.query("SELECT MAX(snapshot_date) FROM ranking_daily_snapshots WHERE owner_username=? AND camp_id=? AND scope_type=? AND class_id=? AND snapshot_date<?",
      (rs,n)->rs.getObject(1,LocalDate.class),owner,campId,scope,classId,LocalDate.now(ZoneId.of("Asia/Shanghai")));
    return dates.isEmpty()?null:dates.getFirst();
  }
  private Map<String,Integer> previousRanks(String owner,String campId,String classId,String scope,LocalDate date) {
    if(date==null)return Map.of(); Map<String,Integer> values=new HashMap<>();
    jdbc.query("SELECT student_id,rank_no FROM ranking_daily_snapshots WHERE snapshot_date=? AND owner_username=? AND camp_id=? AND scope_type=? AND class_id=?",
      (rs,n)->Map.entry(rs.getString(1),rs.getInt(2)),date,owner,campId,scope,classId)
      .forEach(entry->values.put(entry.getKey(),entry.getValue())); return values;
  }
  private void refreshSnapshots(String owner,String campId) {
    LocalDate date=LocalDate.now(ZoneId.of("Asia/Shanghai"));
    jdbc.update("DELETE FROM ranking_daily_snapshots WHERE snapshot_date=? AND owner_username=? AND camp_id=?",date,owner,campId);
    snapshot(date,owner,campId,"","camp",aggregateRows(owner,campId,"","camp"));
    List<String> classes=jdbc.query("SELECT class_id FROM ranking_classes WHERE owner_username=? AND camp_id=?",(rs,n)->rs.getString(1),owner,campId);
    for(String classId:classes)snapshot(date,owner,campId,classId,"class",aggregateRows(owner,campId,classId,"class"));
  }
  private void snapshot(LocalDate date,String owner,String campId,String classId,String scope,List<AggregateRow> rows) {
    for(int i=0;i<rows.size();i++)jdbc.update("INSERT INTO ranking_daily_snapshots(snapshot_date,owner_username,camp_id,scope_type,class_id,student_id,rank_no,total_points) VALUES(?,?,?,?,?,?,?,?)",
      date,owner,campId,scope,classId,rows.get(i).studentId(),i+1,rows.get(i).totalPoints());
  }

  @Transactional
  public RankingPayload.PairingCode createPairingCode(String owner) {
    jdbc.update("DELETE FROM ranking_pairing_codes WHERE expires_at<CURRENT_TIMESTAMP(6) OR used_at IS NOT NULL");
    String code = "%08d".formatted(RANDOM.nextInt(100_000_000)); Instant expires = Instant.now().plus(10, ChronoUnit.MINUTES);
    jdbc.update("INSERT INTO ranking_pairing_codes(code_hash,owner_username,expires_at) VALUES(?,?,?)", sha256(code), owner, Timestamp.from(expires));
    return new RankingPayload.PairingCode(code.substring(0,4)+"-"+code.substring(4), expires);
  }

  @Transactional
  public RankingPayload.Connection connect(String codeValue, String deviceValue) {
    String code = codeValue == null ? "" : codeValue.replaceAll("[^0-9]", ""), device = text(deviceValue, "设备名称", 100);
    if (code.length()!=8) throw invalid("连接码格式不正确");
    List<String> owners = jdbc.query("SELECT owner_username FROM ranking_pairing_codes WHERE code_hash=? AND used_at IS NULL AND expires_at>CURRENT_TIMESTAMP(6) FOR UPDATE", (rs,n)->rs.getString(1), sha256(code));
    if (owners.isEmpty()) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "连接码无效或已过期");
    jdbc.update("UPDATE ranking_pairing_codes SET used_at=CURRENT_TIMESTAMP(6) WHERE code_hash=?", sha256(code));
    return createDevice(owners.getFirst(), device, "");
  }

  @Transactional
  public RankingPayload.Connection bootstrap(String crmTeacherValue, String deviceValue) {
    String crmTeacherId = text(crmTeacherValue, "CRM 教师 ID", 100);
    if (!crmTeacherId.matches("^[A-Za-z0-9_-]+$")) throw invalid("CRM 教师 ID 格式不正确");
    String owner = lookup("SELECT owner_username FROM ranking_teacher_mappings WHERE crm_teacher_id=?", crmTeacherId);
    if (owner == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "当前 CRM 教师尚未绑定 CodeDog 账号，请联系管理员");
    Integer active = jdbc.queryForObject("SELECT COUNT(*) FROM ranking_extension_devices WHERE owner_username=? AND revoked_at IS NULL", Integer.class, owner);
    if (active != null && active >= 20) throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "该账号已连接过多扩展设备，请先在 CodeDog 撤销旧设备");
    return createDevice(owner, text(deviceValue, "设备名称", 100), crmTeacherId);
  }

  private RankingPayload.Connection createDevice(String owner,String device,String crmTeacherId) {
    String token = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes(32)); KeyHolder key = new GeneratedKeyHolder();
    jdbc.update(c -> { PreparedStatement s=c.prepareStatement("INSERT INTO ranking_extension_devices(token_hash,owner_username,device_name) VALUES(?,?,?)", new String[]{"id"}); s.setString(1,sha256(token)); s.setString(2,owner); s.setString(3,device); return s; }, key);
    String teacherId=lookup("SELECT teacher_public_id FROM users WHERE username=?",owner);
    return new RankingPayload.Connection(token, Objects.requireNonNull(key.getKey()).longValue(), owner, teacherId, crmTeacherId);
  }

  public String authenticateToken(String authorization) {
    if (authorization==null || !authorization.regionMatches(true,0,"Bearer ",0,7)) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"缺少扩展设备令牌");
    String hash=sha256(authorization.substring(7).trim());
    List<String> owners=jdbc.query("SELECT owner_username FROM ranking_extension_devices WHERE token_hash=? AND revoked_at IS NULL",(rs,n)->rs.getString(1),hash);
    if (owners.isEmpty()) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"扩展设备令牌无效或已撤销");
    jdbc.update("UPDATE ranking_extension_devices SET last_seen_at=CURRENT_TIMESTAMP(6) WHERE token_hash=?",hash); return owners.getFirst();
  }
  public List<RankingPayload.Device> devices(String owner) {
    return jdbc.query("SELECT id,device_name,owner_username,created_at,last_seen_at,revoked_at FROM ranking_extension_devices WHERE owner_username=? ORDER BY created_at DESC",(rs,n)->new RankingPayload.Device(rs.getLong(1),rs.getString(2),rs.getString(3),rs.getTimestamp(4).toInstant(),instant(rs.getTimestamp(5)),rs.getTimestamp(6)!=null),owner);
  }
  public void revoke(long id,String owner) { if(jdbc.update("UPDATE ranking_extension_devices SET revoked_at=CURRENT_TIMESTAMP(6) WHERE id=? AND owner_username=? AND revoked_at IS NULL",id,owner)==0) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"扩展设备不存在或已撤销"); }

  private TeacherOwner resolveTeacher(String teacherValue) {
    String teacherId=teacherValue==null?"":teacherValue.trim().toUpperCase(Locale.ROOT);
    List<TeacherOwner> values;
    if(teacherId.isEmpty()) values=jdbc.query("SELECT username,teacher_public_id FROM users ORDER BY is_admin DESC,id LIMIT 1",(rs,n)->new TeacherOwner(rs.getString(1),rs.getString(2)));
    else values=jdbc.query("SELECT username,teacher_public_id FROM users WHERE teacher_public_id=?",(rs,n)->new TeacherOwner(rs.getString(1),rs.getString(2)),teacherId);
    if(values.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"教师 ID 不存在");
    return values.getFirst();
  }
  private long createBatch(String owner,String type,String source,String camp,int received,String actor) {
    KeyHolder key=new GeneratedKeyHolder(); jdbc.update(c->{PreparedStatement s=c.prepareStatement("INSERT INTO ranking_import_batches(owner_username,source_type,source_name,camp_id,status,received_rows,actor) VALUES(?,?,?,?,'PROCESSING',?,?)",new String[]{"id"});s.setString(1,owner);s.setString(2,text(type,"来源",20));s.setString(3,text(source,"来源名称",160));s.setString(4,camp);s.setInt(5,received);s.setString(6,text(actor,"操作者",100));return s;},key);return Objects.requireNonNull(key.getKey()).longValue();
  }
  private void upsertCamp(String owner,String id,String name){jdbc.update("INSERT INTO ranking_camps(owner_username,camp_id,camp_name) VALUES(?,?,?) ON DUPLICATE KEY UPDATE camp_name=VALUES(camp_name),updated_at=CURRENT_TIMESTAMP(6)",owner,id,name);}
  private void upsertClass(String owner,String camp,String id,String name){jdbc.update("INSERT INTO ranking_classes(owner_username,camp_id,class_id,class_name) VALUES(?,?,?,?) ON DUPLICATE KEY UPDATE class_name=VALUES(class_name),updated_at=CURRENT_TIMESTAMP(6)",owner,camp,id,name);}
  private void upsertLesson(String owner,String camp,String clazz,String id,String name,Integer order,Instant ended){jdbc.update("INSERT INTO ranking_lessons(owner_username,camp_id,class_id,lesson_id,lesson_name,lesson_order,ended_at) VALUES(?,?,?,?,?,?,?) ON DUPLICATE KEY UPDATE lesson_name=VALUES(lesson_name),lesson_order=VALUES(lesson_order),ended_at=VALUES(ended_at),updated_at=CURRENT_TIMESTAMP(6)",owner,camp,clazz,id,name,order,ended==null?null:Timestamp.from(ended));}
  private void upsertStudent(String owner,String camp,String clazz,String id,String name){jdbc.update("INSERT INTO ranking_students(owner_username,camp_id,class_id,student_id,student_name) VALUES(?,?,?,?,?) ON DUPLICATE KEY UPDATE student_name=VALUES(student_name),updated_at=CURRENT_TIMESTAMP(6)",owner,camp,clazz,id,name);}
  private void markScoreReached(String owner,String camp,String clazz,String student,Instant reachedAt){jdbc.update("UPDATE ranking_students SET score_reached_at=? WHERE owner_username=? AND camp_id=? AND class_id=? AND student_id=?",Timestamp.from(reachedAt),owner,camp,clazz,student);}
  private void upsertResult(String owner,String camp,String clazz,String lesson,String student,RankingScore.Score s,String hash,long batch){jdbc.update("""
    INSERT INTO ranking_lesson_results(owner_username,camp_id,class_id,lesson_id,student_id,completion_rate,inclass_total,inclass_submitted,inclass_passed,homework_total,homework_submitted,homework_passed,completion_points,inclass_points,homework_points,total_points,rule_version,content_hash,import_batch_id)
    VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,1,?,?) ON DUPLICATE KEY UPDATE completion_rate=VALUES(completion_rate),inclass_total=VALUES(inclass_total),inclass_submitted=VALUES(inclass_submitted),inclass_passed=VALUES(inclass_passed),homework_total=VALUES(homework_total),homework_submitted=VALUES(homework_submitted),homework_passed=VALUES(homework_passed),completion_points=VALUES(completion_points),inclass_points=VALUES(inclass_points),homework_points=VALUES(homework_points),total_points=VALUES(total_points),content_hash=VALUES(content_hash),import_batch_id=VALUES(import_batch_id),updated_at=CURRENT_TIMESTAMP(6)
    """,owner,camp,clazz,lesson,student,s.completionRate(),s.inclass().total(),s.inclass().submitted(),s.inclass().passed(),s.homework().total(),s.homework().submitted(),s.homework().passed(),s.completionPoints(),s.inclassPoints(),s.homeworkPoints(),s.totalPoints(),hash,batch);}
  private ExistingResult existingResult(String owner,String camp,String clazz,String lesson,String student){List<ExistingResult> values=jdbc.query("SELECT content_hash,total_points FROM ranking_lesson_results WHERE owner_username=? AND camp_id=? AND class_id=? AND lesson_id=? AND student_id=?",(rs,n)->new ExistingResult(rs.getString(1),rs.getInt(2)),owner,camp,clazz,lesson,student);return values.isEmpty()?null:values.getFirst();}
  private String lookup(String sql,Object...args){try{return jdbc.queryForObject(sql,String.class,args);}catch(EmptyResultDataAccessException e){return null;}}
  private Instant latestUpdate(String owner){Timestamp t=jdbc.queryForObject("SELECT MAX(updated_at) FROM ranking_lesson_results WHERE owner_username=?",Timestamp.class,owner);return instant(t);}
  private Instant instant(Timestamp value){return value==null?null:value.toInstant();}
  private int countRows(List<RankingPayload.ClassData> c){return safe(c).stream().flatMap(x->safe(x.lessons()).stream()).mapToInt(x->safe(x.students()).size()).sum();}
  private <T> List<T> safe(List<T> value){return value==null?List.of():value;}
  private void addError(List<RankingPayload.RowError> e,String c,String l,String s,String m){if(e.size()<200)e.add(new RankingPayload.RowError(c,l,s,m));}
  private String message(RuntimeException e){return e.getMessage()==null?"数据无效":e.getMessage();}
  private String text(String v,String label,int max){String n=v==null?"":v.trim();if(n.isEmpty())throw invalid(label+"不能为空");if(n.length()>max)throw invalid(label+"不能超过 "+max+" 个字符");return n;}
  private ResponseStatusException invalid(String m){return new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,m);}
  private byte[] randomBytes(int n){byte[] b=new byte[n];RANDOM.nextBytes(b);return b;}
  private String sha256(String v){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(v.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
  private int level(int p){if(p>=5400)return 6;if(p>=4200)return 5;if(p>=2700)return 4;if(p>=1500)return 3;if(p>=600)return 2;return 1;}
  private String levelName(int l){return switch(l){case 6->"钻石";case 5->"蓝宝石";case 4->"黄金";case 3->"白银";case 2->"青铜";default->"石墨";};}
  private String get(java.sql.ResultSet rs,int column){try{return rs.getString(column);}catch(java.sql.SQLException e){throw new IllegalStateException(e);}}
  private record TeacherOwner(String username,String teacherId){}
  private record AggregateRow(String studentId,String studentName,String classId,String className,int totalPoints,
                              int completionPoints,int inclassPoints,int homeworkPoints,int lessonCount,
                              Instant scoreReachedAt,int accuracyBasisPoints){}
  private record ExistingResult(String hash,int totalPoints){}
  private static final class MutableCamp{final String id,name;final List<RankingPayload.ClassOption> classes=new ArrayList<>();MutableCamp(String id,String name){this.id=id;this.name=name;}}
}
