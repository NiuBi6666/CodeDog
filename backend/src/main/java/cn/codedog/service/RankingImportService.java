package cn.codedog.service;

import cn.codedog.model.RankingPayload;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class RankingImportService {
  private static final int MAX_ROWS = 50_000;
  private static final int MAX_CONTACT_ROWS = 5_000;

  private final JdbcTemplate jdbc;
  private final RankingBoardService boards;

  public RankingImportService(JdbcTemplate jdbc, RankingBoardService boards) {
    this.jdbc = jdbc;
    this.boards = boards;
  }

  @Transactional
  public RankingPayload.ImportSummary importData(RankingPayload payload, String sourceType, String sourceName,
                                                  String ownerValue) {
    String owner = text(ownerValue, "数据所属用户", 50);
    String campId = text(payload == null ? null : payload.campId(), "营期 ID", 100);
    String campName = text(payload.campName(), "营期名称", 160);
    List<RankingPayload.ClassData> classes = safe(payload.classes());
    int received = countRows(classes);
    if (classes.isEmpty() || received == 0) {
      throw invalid("没有可导入的学员结果");
    }
    if (received > MAX_ROWS) {
      throw invalid("单次导入不能超过 " + MAX_ROWS + " 行");
    }

    long batchId = createBatch(owner, sourceType, sourceName, campId, received, owner);
    Instant scoreEventAt = Instant.now();
    upsertCamp(owner, campId, campName);
    int changed = 0;
    int unchanged = 0;
    int rejected = 0;
    List<RankingPayload.RowError> errors = new ArrayList<>();
    for (RankingPayload.ClassData clazz : classes) {
      String classId;
      try {
        classId = text(clazz.classId(), "班级 ID", 100);
        upsertClass(owner, campId, classId, text(clazz.className(), "班级名称", 160));
      } catch (RuntimeException error) {
        rejected += countRows(List.of(clazz));
        addError(errors, clazz.classId(), "", "", message(error));
        continue;
      }
      for (RankingPayload.LessonData lesson : safe(clazz.lessons())) {
        String lessonId;
        try {
          lessonId = text(lesson.lessonId(), "课节 ID", 100);
          upsertLesson(owner, campId, classId, lessonId, text(lesson.lessonName(), "课节名称", 200),
              lesson.lessonOrder(), lesson.endedAt());
        } catch (RuntimeException error) {
          rejected += safe(lesson.students()).size();
          addError(errors, classId, lesson.lessonId(), "", message(error));
          continue;
        }
        for (RankingPayload.StudentResult student : safe(lesson.students())) {
          try {
            String studentId = text(student.studentId(), "学员 ID", 100);
            String studentName = text(student.studentName(), "学员姓名", 100);
            RankingScore.Score score = RankingScore.calculate(student.completionRate(), student.inclass(), student.homework());
            upsertStudent(owner, campId, classId, studentId, studentName);
            String hash = sha256(studentName + "|" + score);
            ExistingResult existing = existingResult(owner, campId, classId, lessonId, studentId);
            if (existing != null && hash.equals(existing.hash())) {
              unchanged++;
            } else {
              upsertResult(owner, campId, classId, lessonId, studentId, score, hash, batchId);
              if (existing == null || existing.totalPoints() != score.totalPoints()) {
                markScoreReached(owner, campId, classId, studentId, scoreEventAt);
              }
              changed++;
            }
          } catch (RuntimeException error) {
            rejected++;
            addError(errors, classId, lessonId, student.studentId(), message(error));
          }
        }
      }
    }
    jdbc.update("UPDATE ranking_import_batches SET status=?, changed_rows=?, rejected_rows=?, "
            + "completed_at=CURRENT_TIMESTAMP(6) WHERE id=? AND owner_username=?",
        rejected == received ? "FAILED" : rejected > 0 ? "PARTIAL" : "COMPLETED",
        changed, rejected, batchId, owner);
    boards.refreshSnapshots(owner, campId);
    return new RankingPayload.ImportSummary(batchId, received, changed, unchanged, rejected, List.copyOf(errors));
  }

  @Transactional
  public RankingPayload.ExternalContactSyncSummary syncExternalContacts(RankingPayload.ExternalContactSync payload,
                                                                         String ownerValue) {
    String owner = text(ownerValue, "数据所属用户", 50);
    List<RankingPayload.ExternalContactInput> contacts = safe(payload == null ? null : payload.contacts());
    if (contacts.isEmpty()) {
      throw invalid("没有可保存的企微联系人");
    }
    if (contacts.size() > MAX_CONTACT_ROWS) {
      throw invalid("单次同步不能超过 " + MAX_CONTACT_ROWS + " 条企微联系人");
    }

    Map<String, Set<String>> requestValues = new HashMap<>();
    for (RankingPayload.ExternalContactInput contact : contacts) {
      if (contact == null) continue;
      String crmUserId = contact.crmUserId() == null ? "" : contact.crmUserId().trim();
      String externalUserId = contact.externalUserId() == null ? "" : contact.externalUserId().trim();
      if (!crmUserId.isEmpty() && !externalUserId.isEmpty()) {
        requestValues.computeIfAbsent(crmUserId, ignored -> new HashSet<>()).add(externalUserId);
      }
    }
    Set<String> requestConflicts = new HashSet<>();
    requestValues.forEach((crmUserId, values) -> {
      if (values.size() > 1) requestConflicts.add(crmUserId);
    });

    Set<String> reportedRequestConflicts = new HashSet<>();
    int inserted = 0;
    int unchanged = 0;
    int conflicts = 0;
    List<RankingPayload.ExternalContactError> errors = new ArrayList<>();
    for (RankingPayload.ExternalContactInput contact : contacts) {
      String crmUserId = contact == null ? "" : contact.crmUserId();
      try {
        crmUserId = text(crmUserId, "CRM 用户 ID", 100);
        if (!crmUserId.matches("^[A-Za-z0-9_-]+$")) {
          throw invalid("CRM 用户 ID 格式不正确");
        }
        String externalUserId = text(contact.externalUserId(), "external_userid", 128);
        if (!externalUserId.matches("^wm[A-Za-z0-9_-]+$")) {
          throw invalid("external_userid 格式不正确");
        }
        jdbc.update("INSERT INTO crm_external_contact_observations(owner_username,crm_user_id,external_userid) "
                + "VALUES(?,?,?) ON DUPLICATE KEY UPDATE last_seen_at=CURRENT_TIMESTAMP(6),seen_count=seen_count+1",
            owner, crmUserId, externalUserId);
        if (requestConflicts.contains(crmUserId)) {
          if (reportedRequestConflicts.add(crmUserId)) {
            conflicts++;
            addContactError(errors, crmUserId, "同一批次包含多个不同的 external_userid，整组未保存");
          }
          continue;
        }
        String existing = lookup(
            "SELECT external_userid FROM crm_external_contacts WHERE owner_username=? AND crm_user_id=?", owner, crmUserId);
        if (existing == null) {
          jdbc.update("INSERT INTO crm_external_contacts(owner_username,crm_user_id,external_userid) VALUES(?,?,?)",
              owner, crmUserId, externalUserId);
          inserted++;
        } else if (existing.equals(externalUserId)) {
          jdbc.update("UPDATE crm_external_contacts SET updated_at=CURRENT_TIMESTAMP(6) "
              + "WHERE owner_username=? AND crm_user_id=?", owner, crmUserId);
          unchanged++;
        } else {
          conflicts++;
          addContactError(errors, crmUserId, "数据库中已存在不同的 external_userid，未覆盖原值");
        }
      } catch (RuntimeException error) {
        conflicts++;
        addContactError(errors, crmUserId, message(error));
      }
    }
    return new RankingPayload.ExternalContactSyncSummary(
        contacts.size(), inserted, unchanged, conflicts, List.copyOf(errors));
  }

  private long createBatch(String owner, String type, String source, String camp, int received, String actor) {
    KeyHolder key = new GeneratedKeyHolder();
    jdbc.update(connection -> {
      PreparedStatement statement = connection.prepareStatement(
          "INSERT INTO ranking_import_batches(owner_username,source_type,source_name,camp_id,status,received_rows,actor) "
              + "VALUES(?,?,?,?,'PROCESSING',?,?)", new String[]{"id"});
      statement.setString(1, owner);
      statement.setString(2, text(type, "来源", 20));
      statement.setString(3, text(source, "来源名称", 160));
      statement.setString(4, camp);
      statement.setInt(5, received);
      statement.setString(6, text(actor, "操作者", 100));
      return statement;
    }, key);
    return Objects.requireNonNull(key.getKey()).longValue();
  }

  private void upsertCamp(String owner, String id, String name) {
    jdbc.update("INSERT INTO ranking_camps(owner_username,camp_id,camp_name) VALUES(?,?,?) "
        + "ON DUPLICATE KEY UPDATE camp_name=VALUES(camp_name),updated_at=CURRENT_TIMESTAMP(6)", owner, id, name);
  }

  private void upsertClass(String owner, String camp, String id, String name) {
    jdbc.update("INSERT INTO ranking_classes(owner_username,camp_id,class_id,class_name) VALUES(?,?,?,?) "
        + "ON DUPLICATE KEY UPDATE class_name=VALUES(class_name),updated_at=CURRENT_TIMESTAMP(6)",
        owner, camp, id, name);
  }

  private void upsertLesson(String owner, String camp, String clazz, String id, String name, Integer order,
                            Instant ended) {
    jdbc.update("INSERT INTO ranking_lessons(owner_username,camp_id,class_id,lesson_id,lesson_name,lesson_order,ended_at) "
            + "VALUES(?,?,?,?,?,?,?) ON DUPLICATE KEY UPDATE lesson_name=VALUES(lesson_name),"
            + "lesson_order=VALUES(lesson_order),ended_at=VALUES(ended_at),updated_at=CURRENT_TIMESTAMP(6)",
        owner, camp, clazz, id, name, order, ended == null ? null : Timestamp.from(ended));
  }

  private void upsertStudent(String owner, String camp, String clazz, String id, String name) {
    jdbc.update("INSERT INTO ranking_students(owner_username,camp_id,class_id,student_id,student_name) VALUES(?,?,?,?,?) "
        + "ON DUPLICATE KEY UPDATE student_name=VALUES(student_name),updated_at=CURRENT_TIMESTAMP(6)",
        owner, camp, clazz, id, name);
  }

  private void markScoreReached(String owner, String camp, String clazz, String student, Instant reachedAt) {
    jdbc.update("UPDATE ranking_students SET score_reached_at=? "
            + "WHERE owner_username=? AND camp_id=? AND class_id=? AND student_id=?",
        Timestamp.from(reachedAt), owner, camp, clazz, student);
  }

  private void upsertResult(String owner, String camp, String clazz, String lesson, String student,
                            RankingScore.Score score, String hash, long batch) {
    jdbc.update("""
        INSERT INTO ranking_lesson_results(owner_username,camp_id,class_id,lesson_id,student_id,completion_rate,
          inclass_total,inclass_submitted,inclass_passed,homework_total,homework_submitted,homework_passed,
          completion_points,inclass_points,homework_points,total_points,rule_version,content_hash,import_batch_id)
        VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,1,?,?) ON DUPLICATE KEY UPDATE
          completion_rate=VALUES(completion_rate),inclass_total=VALUES(inclass_total),
          inclass_submitted=VALUES(inclass_submitted),inclass_passed=VALUES(inclass_passed),
          homework_total=VALUES(homework_total),homework_submitted=VALUES(homework_submitted),
          homework_passed=VALUES(homework_passed),completion_points=VALUES(completion_points),
          inclass_points=VALUES(inclass_points),homework_points=VALUES(homework_points),
          total_points=VALUES(total_points),content_hash=VALUES(content_hash),
          import_batch_id=VALUES(import_batch_id),updated_at=CURRENT_TIMESTAMP(6)
        """, owner, camp, clazz, lesson, student, score.completionRate(), score.inclass().total(),
        score.inclass().submitted(), score.inclass().passed(), score.homework().total(), score.homework().submitted(),
        score.homework().passed(), score.completionPoints(), score.inclassPoints(), score.homeworkPoints(),
        score.totalPoints(), hash, batch);
  }

  private ExistingResult existingResult(String owner, String camp, String clazz, String lesson, String student) {
    List<ExistingResult> values = jdbc.query(
        "SELECT content_hash,total_points FROM ranking_lesson_results "
            + "WHERE owner_username=? AND camp_id=? AND class_id=? AND lesson_id=? AND student_id=?",
        (rs, n) -> new ExistingResult(rs.getString(1), rs.getInt(2)), owner, camp, clazz, lesson, student);
    return values.isEmpty() ? null : values.getFirst();
  }

  private String lookup(String sql, Object... args) {
    try {
      return jdbc.queryForObject(sql, String.class, args);
    } catch (EmptyResultDataAccessException ignored) {
      return null;
    }
  }

  private int countRows(List<RankingPayload.ClassData> classes) {
    return safe(classes).stream().flatMap(clazz -> safe(clazz.lessons()).stream())
        .mapToInt(lesson -> safe(lesson.students()).size()).sum();
  }

  private <T> List<T> safe(List<T> value) {
    return value == null ? List.of() : value;
  }

  private void addError(List<RankingPayload.RowError> errors, String classId, String lessonId, String studentId,
                        String message) {
    if (errors.size() < 200) errors.add(new RankingPayload.RowError(classId, lessonId, studentId, message));
  }

  private void addContactError(List<RankingPayload.ExternalContactError> errors, String userId, String message) {
    if (errors.size() < 200) errors.add(new RankingPayload.ExternalContactError(userId, message));
  }

  private String message(RuntimeException error) {
    return error.getMessage() == null ? "数据无效" : error.getMessage();
  }

  private String text(String value, String label, int max) {
    String normalized = value == null ? "" : value.trim();
    if (normalized.isEmpty()) throw invalid(label + "不能为空");
    if (normalized.length() > max) throw invalid(label + "不能超过 " + max + " 个字符");
    return normalized;
  }

  private ResponseStatusException invalid(String message) {
    return new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, message);
  }

  private String sha256(String value) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
          .digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception error) {
      throw new IllegalStateException(error);
    }
  }

  private record ExistingResult(String hash, int totalPoints) {}
}
