package cn.codedog.service;

import cn.codedog.model.RankingPayload;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class RankingBoardService {
  private static final ZoneId RANKING_ZONE = ZoneId.of("Asia/Shanghai");

  private final JdbcTemplate jdbc;

  public RankingBoardService(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public RankingPayload.Catalog catalog() {
    return catalog(null);
  }

  public RankingPayload.Catalog catalog(String teacherValue) {
    TeacherOwner teacher = resolveTeacher(teacherValue);
    Map<String, MutableCamp> values = new LinkedHashMap<>();
    jdbc.query("SELECT c.camp_id,c.camp_name,k.class_id,k.class_name FROM ranking_camps c "
        + "LEFT JOIN ranking_classes k ON k.owner_username=c.owner_username AND k.camp_id=c.camp_id "
        + "WHERE c.owner_username=? ORDER BY c.updated_at DESC,c.camp_name,k.class_name", rs -> {
          MutableCamp camp = values.computeIfAbsent(rs.getString(1), id -> new MutableCamp(id, get(rs, 2)));
          if (rs.getString(3) != null) {
            camp.classes.add(new RankingPayload.ClassOption(rs.getString(3), rs.getString(4)));
          }
        }, teacher.username());
    List<RankingPayload.CampOption> camps = values.values().stream()
        .map(camp -> new RankingPayload.CampOption(camp.id, camp.name, List.copyOf(camp.classes)))
        .toList();
    return new RankingPayload.Catalog(teacher.teacherId(), teacher.username(), camps, latestUpdate(teacher.username()));
  }

  public RankingPayload.Board board(String campValue, String classValue, String scopeValue) {
    return board(null, campValue, classValue, scopeValue);
  }

  public RankingPayload.Board board(String teacherValue, String campValue, String classValue, String scopeValue) {
    String owner = resolveTeacher(teacherValue).username();
    String campId = text(campValue, "营期 ID", 100);
    String scope = "camp".equalsIgnoreCase(scopeValue) ? "camp" : "class";
    String classId = scope.equals("class") ? text(classValue, "班级 ID", 100) : "";
    String campName = lookup("SELECT camp_name FROM ranking_camps WHERE owner_username=? AND camp_id=?", owner, campId);
    if (campName == null) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "排行榜营期不存在");
    }
    String className = scope.equals("class")
        ? lookup("SELECT class_name FROM ranking_classes WHERE owner_username=? AND camp_id=? AND class_id=?", owner, campId, classId)
        : "全部班级";
    if (className == null) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "排行榜班级不存在");
    }

    List<AggregateRow> rows = aggregateRows(owner, campId, classId, scope);
    LocalDate baselineDate = previousSnapshotDate(owner, campId, classId, scope);
    Map<String, Integer> previousRanks = previousRanks(owner, campId, classId, scope, baselineDate);
    List<RankingPayload.Entry> entries = new ArrayList<>();
    for (RankedRow rankedRow : rankRows(rows)) {
      AggregateRow row = rankedRow.row();
      int rank = rankedRow.rank();
      int level = level(row.availablePoints());
      Integer previousRank = previousRanks.get(row.studentId());
      int rankChange = previousRank == null ? 0 : previousRank - rank;
      entries.add(new RankingPayload.Entry(rank, row.studentId(), row.studentName(), row.classId(), row.className(),
          row.totalPoints(), row.completionPoints(), row.inclassPoints(), row.homeworkPoints(), row.lessonCount(),
          level, levelName(level), row.scoreReachedAt(), row.accuracyBasisPoints() / 100.0, previousRank, rankChange,
          previousRank == null ? "NEW" : rankChange > 0 ? "UP" : rankChange < 0 ? "DOWN" : "SAME",
          row.availablePoints(), row.adjustmentPoints(), row.spentPoints(), row.campId()));
    }
    return new RankingPayload.Board(campId, campName, classId, className, scope, entries.size(), latestUpdate(owner),
        baselineDate, List.copyOf(entries));
  }

  public RankingPayload.Board allBoard() {
    return allBoard(null);
  }

  public RankingPayload.Board allBoard(String teacherValue) {
    return allBoardForOwner(resolveTeacher(teacherValue).username());
  }

  public RankingPayload.Board allBoardForOwner(String ownerValue) {
    String owner = text(ownerValue, "数据所属用户", 50);
    List<RankingPayload.Entry> entries = new ArrayList<>();
    for (RankedRow rankedRow : rankRows(aggregateAllRows(owner))) {
      AggregateRow row = rankedRow.row();
      int level = level(row.availablePoints());
      entries.add(new RankingPayload.Entry(rankedRow.rank(), row.studentId(), row.studentName(), row.classId(), row.className(),
          row.totalPoints(), row.completionPoints(), row.inclassPoints(), row.homeworkPoints(), row.lessonCount(),
          level, levelName(level), row.scoreReachedAt(), row.accuracyBasisPoints() / 100.0, null, 0, "NEW",
          row.availablePoints(), row.adjustmentPoints(), row.spentPoints(), row.campId()));
    }
    return new RankingPayload.Board("", "全部学员", "", "全部学员", "all", entries.size(), latestUpdate(owner), null,
        List.copyOf(entries));
  }

  public RankingPayload.Board studentBoard(String ownerValue, String studentValue) {
    RankingPayload.Board full = allBoardForOwner(ownerValue);
    String studentId = text(studentValue, "学员 ID", 100);
    List<RankingPayload.Entry> visible = new ArrayList<>(full.rankings().stream().limit(10).toList());
    if (visible.stream().noneMatch(row -> row.studentId().equals(studentId))) {
      full.rankings().stream().filter(row -> row.studentId().equals(studentId)).findFirst().ifPresent(visible::add);
    }
    if (visible.isEmpty() || visible.stream().noneMatch(row -> row.studentId().equals(studentId))) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "学员不在当前排行榜中");
    }
    return new RankingPayload.Board(full.campId(), full.campName(), full.classId(), full.className(), full.scope(),
        full.studentCount(), full.updatedAt(), full.trendBaselineDate(), List.copyOf(visible));
  }

  public RankingPayload.OpportunitySummary opportunities(String studentValue) {
    return opportunitiesForOwner(resolveTeacher(null).username(), studentValue);
  }

  public RankingPayload.OpportunitySummary opportunitiesForOwner(String ownerValue, String studentValue) {
    String owner = text(ownerValue, "数据所属用户", 50);
    String studentId = text(studentValue, "学员 ID", 100);
    Integer studentCount = jdbc.queryForObject(
        "SELECT COUNT(*) FROM ranking_students WHERE owner_username=? AND student_id=?", Integer.class, owner, studentId);
    if (studentCount == null || studentCount == 0) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "学员不存在");
    }
    OpportunityStats stats = jdbc.queryForObject("""
        SELECT
        COALESCE(SUM(CASE WHEN homework_total>homework_submitted THEN homework_total-homework_submitted ELSE 0 END),0),
        COALESCE(SUM(CASE WHEN homework_submitted>homework_passed THEN homework_submitted-homework_passed ELSE 0 END),0),
        COALESCE(SUM(CASE WHEN inclass_total>inclass_submitted THEN inclass_total-inclass_submitted ELSE 0 END),0),
        COALESCE(SUM(CASE WHEN inclass_submitted>inclass_passed THEN inclass_submitted-inclass_passed ELSE 0 END),0),
        COALESCE(SUM(CASE WHEN completion_rate<1 THEN 1 ELSE 0 END),0)
        FROM ranking_lesson_results WHERE owner_username=? AND student_id=?
        """, (rs, n) -> new OpportunityStats(rs.getInt(1), rs.getInt(2), rs.getInt(3), rs.getInt(4), rs.getInt(5)),
        owner, studentId);
    List<RankingPayload.Opportunity> values = new ArrayList<>();
    if (stats.homeworkMissing() > 0) {
      values.add(new RankingPayload.Opportunity("HOMEWORK_MISSING", "完成课后作业",
          "还有 " + stats.homeworkMissing() + " 道课后作业未提交，赶快完成并争取全部答对。", stats.homeworkMissing()));
    }
    if (stats.inclassMissing() > 0) {
      values.add(new RankingPayload.Opportunity("INCLASS_MISSING", "补齐课上作业",
          "还有 " + stats.inclassMissing() + " 道课上作业未提交，补齐后再检查答案。", stats.inclassMissing()));
    }
    if (stats.incompleteLessons() > 0) {
      values.add(new RankingPayload.Opportunity("LESSON_INCOMPLETE", "完成未学完课程",
          "还有 " + stats.incompleteLessons() + " 节课程没有完成，学完课程即可获得更多完课积分。", stats.incompleteLessons()));
    }
    if (stats.homeworkReview() > 0) {
      values.add(new RankingPayload.Opportunity("HOMEWORK_REVIEW", "订正课后作业",
          "有 " + stats.homeworkReview() + " 道课后作业尚未通过，订正后可以提高正确率。", stats.homeworkReview()));
    }
    if (stats.inclassReview() > 0) {
      values.add(new RankingPayload.Opportunity("INCLASS_REVIEW", "复习课上错题",
          "有 " + stats.inclassReview() + " 道课上作业尚未通过，重新检查解题过程。", stats.inclassReview()));
    }
    boolean complete = values.isEmpty();
    if (complete) {
      values.add(new RankingPayload.Opportunity("COMPLETE", "本期任务已全部完成",
          "当前同步到的课程和作业都已完成，继续保持高正确率！", 0));
    }
    return new RankingPayload.OpportunitySummary(studentId, complete, List.copyOf(values));
  }

  void refreshSnapshots(String owner, String campId) {
    LocalDate date = LocalDate.now(RANKING_ZONE);
    jdbc.update("DELETE FROM ranking_daily_snapshots WHERE snapshot_date=? AND owner_username=? AND camp_id=?",
        date, owner, campId);
    snapshot(date, owner, campId, "", "camp", aggregateRows(owner, campId, "", "camp"));
    List<String> classes = jdbc.query(
        "SELECT class_id FROM ranking_classes WHERE owner_username=? AND camp_id=?",
        (rs, n) -> rs.getString(1), owner, campId);
    for (String classId : classes) {
      snapshot(date, owner, campId, classId, "class", aggregateRows(owner, campId, classId, "class"));
    }
  }

  private List<AggregateRow> aggregateAllRows(String owner) {
    String sql = """
        SELECT s.student_id,MAX(s.student_name) student_name,MIN(s.camp_id) camp_id,MIN(s.class_id) class_id,MIN(c.class_name) class_name,
        COALESCE(MAX(a.adjustment_points),0) adjustment_points,COALESCE(MAX(p.spent_points),0) spent_points,
        GREATEST(0,COALESCE(SUM(r.total_points),0)+COALESCE(MAX(a.adjustment_points),0)-COALESCE(MAX(p.spent_points),0)) available_points,
        COALESCE(SUM(r.total_points),0) total_points,COALESCE(SUM(r.completion_points),0) completion_points,
        COALESCE(SUM(r.inclass_points),0) inclass_points,COALESCE(SUM(r.homework_points),0) homework_points,
        COUNT(r.lesson_id) lesson_count,MAX(s.score_reached_at) score_reached_at,
        CASE WHEN COUNT(r.lesson_id)=0 THEN 0
        ELSE ROUND(50.0*SUM(r.inclass_points+r.homework_points)/COUNT(r.lesson_id)) END accuracy_basis_points
        FROM ranking_students s
        JOIN ranking_classes c ON c.owner_username=s.owner_username AND c.camp_id=s.camp_id AND c.class_id=s.class_id
        LEFT JOIN ranking_lesson_results r ON r.owner_username=s.owner_username AND r.camp_id=s.camp_id
          AND r.class_id=s.class_id AND r.student_id=s.student_id
        LEFT JOIN (SELECT owner_username,student_id,SUM(points) adjustment_points FROM ranking_point_adjustments
          GROUP BY owner_username,student_id) a ON a.owner_username=s.owner_username AND a.student_id=s.student_id
        LEFT JOIN (SELECT owner_username,student_id,SUM(points_spent) spent_points FROM ranking_reward_redemptions
          GROUP BY owner_username,student_id) p ON p.owner_username=s.owner_username AND p.student_id=s.student_id
        WHERE s.owner_username=?
        GROUP BY s.student_id
        ORDER BY available_points DESC,score_reached_at ASC,accuracy_basis_points DESC,homework_points DESC,
          inclass_points DESC,completion_points DESC,s.student_id
        """;
    return jdbc.query(sql, (rs, n) -> aggregateRow(rs), owner);
  }

  private List<AggregateRow> aggregateRows(String owner, String campId, String classId, String scope) {
    String filter = scope.equals("class") ? " AND s.class_id=?" : "";
    List<Object> args = new ArrayList<>(List.of(owner, campId));
    if (scope.equals("class")) {
      args.add(classId);
    }
    String sql = """
        SELECT s.student_id,MAX(s.student_name) student_name,MIN(s.camp_id) camp_id,MIN(s.class_id) class_id,MIN(c.class_name) class_name,
        COALESCE(MAX(a.adjustment_points),0) adjustment_points,COALESCE(MAX(p.spent_points),0) spent_points,
        GREATEST(0,COALESCE(SUM(r.total_points),0)+COALESCE(MAX(a.adjustment_points),0)-COALESCE(MAX(p.spent_points),0)) available_points,
        COALESCE(SUM(r.total_points),0) total_points,COALESCE(SUM(r.completion_points),0) completion_points,
        COALESCE(SUM(r.inclass_points),0) inclass_points,COALESCE(SUM(r.homework_points),0) homework_points,
        COUNT(r.lesson_id) lesson_count,MAX(s.score_reached_at) score_reached_at,
        CASE WHEN COUNT(r.lesson_id)=0 THEN 0
        ELSE ROUND(50.0*SUM(r.inclass_points+r.homework_points)/COUNT(r.lesson_id)) END accuracy_basis_points
        FROM ranking_students s
        JOIN ranking_classes c ON c.owner_username=s.owner_username AND c.camp_id=s.camp_id AND c.class_id=s.class_id
        LEFT JOIN ranking_lesson_results r ON r.owner_username=s.owner_username AND r.camp_id=s.camp_id
          AND r.class_id=s.class_id AND r.student_id=s.student_id
        LEFT JOIN (SELECT owner_username,camp_id,class_id,student_id,SUM(points) adjustment_points FROM ranking_point_adjustments
          GROUP BY owner_username,camp_id,class_id,student_id) a ON a.owner_username=s.owner_username AND a.camp_id=s.camp_id
          AND a.class_id=s.class_id AND a.student_id=s.student_id
        LEFT JOIN (SELECT owner_username,student_id,SUM(points_spent) spent_points FROM ranking_reward_redemptions
          GROUP BY owner_username,student_id) p ON p.owner_username=s.owner_username AND p.student_id=s.student_id
        WHERE s.owner_username=? AND s.camp_id=?
        """ + filter + " GROUP BY s.student_id ORDER BY available_points DESC,score_reached_at ASC,accuracy_basis_points DESC,"
        + "homework_points DESC,inclass_points DESC,completion_points DESC,s.student_id";
    return jdbc.query(sql, (rs, n) -> aggregateRow(rs), args.toArray());
  }

  private AggregateRow aggregateRow(java.sql.ResultSet rs) throws java.sql.SQLException {
    return new AggregateRow(rs.getString("student_id"), rs.getString("student_name"), rs.getString("camp_id"), rs.getString("class_id"),
        rs.getString("class_name"), rs.getInt("total_points"), rs.getInt("adjustment_points"), rs.getInt("spent_points"),
        rs.getInt("available_points"), rs.getInt("completion_points"),
        rs.getInt("inclass_points"), rs.getInt("homework_points"), rs.getInt("lesson_count"),
        rs.getTimestamp("score_reached_at").toInstant(), rs.getInt("accuracy_basis_points"));
  }

  private LocalDate previousSnapshotDate(String owner, String campId, String classId, String scope) {
    List<LocalDate> dates = jdbc.query(
        "SELECT MAX(snapshot_date) FROM ranking_daily_snapshots WHERE owner_username=? AND camp_id=? "
            + "AND scope_type=? AND class_id=? AND snapshot_date<?",
        (rs, n) -> rs.getObject(1, LocalDate.class), owner, campId, scope, classId, LocalDate.now(RANKING_ZONE));
    return dates.isEmpty() ? null : dates.getFirst();
  }

  private Map<String, Integer> previousRanks(String owner, String campId, String classId, String scope, LocalDate date) {
    if (date == null) {
      return Map.of();
    }
    Map<String, Integer> values = new HashMap<>();
    List<SnapshotScore> rows = jdbc.query(
        "SELECT student_id,total_points FROM ranking_daily_snapshots WHERE snapshot_date=? AND owner_username=? "
            + "AND camp_id=? AND scope_type=? AND class_id=? ORDER BY total_points DESC,rank_no,student_id",
        (rs, n) -> new SnapshotScore(rs.getString(1), rs.getInt(2)), date, owner, campId, scope, classId);
    int rank = 0;
    int previousPoints = 0;
    for (int index = 0; index < rows.size(); index++) {
      SnapshotScore row = rows.get(index);
      if (index == 0 || row.totalPoints() != previousPoints) {
        rank = index + 1;
      }
      values.put(row.studentId(), rank);
      previousPoints = row.totalPoints();
    }
    return values;
  }

  private void snapshot(LocalDate date, String owner, String campId, String classId, String scope,
                        List<AggregateRow> rows) {
    for (RankedRow rankedRow : rankRows(rows)) {
      jdbc.update("INSERT INTO ranking_daily_snapshots(snapshot_date,owner_username,camp_id,scope_type,class_id,"
              + "student_id,rank_no,total_points) VALUES(?,?,?,?,?,?,?,?)",
          date, owner, campId, scope, classId, rankedRow.row().studentId(), rankedRow.rank(),
          rankedRow.row().availablePoints());
    }
  }

  private List<RankedRow> rankRows(List<AggregateRow> rows) {
    List<RankedRow> rankedRows = new ArrayList<>(rows.size());
    int rank = 0;
    int previousPoints = 0;
    for (int index = 0; index < rows.size(); index++) {
      AggregateRow row = rows.get(index);
      if (index == 0 || row.availablePoints() != previousPoints) {
        rank = index + 1;
      }
      rankedRows.add(new RankedRow(rank, row));
      previousPoints = row.availablePoints();
    }
    return rankedRows;
  }

  @Transactional
  public RankingPayload.PointAdjustment adjustStudentPoints(String owner, String studentValue, String campValue,
                                                            String classValue, Integer targetValue, String reason,
                                                            String actor) {
    String studentId = text(studentValue, "学员 ID", 100);
    String campId = text(campValue, "营期 ID", 100);
    String classId = text(classValue, "班级 ID", 100);
    String note = text(reason, "调整原因", 255);
    if (targetValue == null || targetValue < 0 || targetValue > 1_000_000) {
      throw invalid("目标积分必须是 0 至 1000000 的整数");
    }
    List<String> names = jdbc.query("SELECT student_name FROM ranking_students WHERE owner_username=? AND camp_id=? AND class_id=? AND student_id=? FOR UPDATE",
        (rs, n) -> rs.getString(1), owner, campId, classId, studentId);
    if (names.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "学员不在指定营期班级中");
    AggregateRow before = aggregateAllRows(owner).stream().filter(row -> row.studentId().equals(studentId)).findFirst()
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "学员不存在"));
    int delta = targetValue - before.availablePoints();
    jdbc.update("INSERT INTO ranking_point_adjustments(owner_username,camp_id,class_id,student_id,points,reason,actor) VALUES(?,?,?,?,?,?,?)",
        owner, campId, classId, studentId, delta, note, actor);
    return new RankingPayload.PointAdjustment(studentId, names.getFirst(), campId, classId, before.totalPoints(),
        before.adjustmentPoints() + delta, before.spentPoints(), before.availablePoints(), targetValue, delta, note);
  }

  private TeacherOwner resolveTeacher(String teacherValue) {
    String teacherId = teacherValue == null ? "" : teacherValue.trim().toUpperCase(Locale.ROOT);
    List<TeacherOwner> values;
    if (teacherId.isEmpty()) {
      values = jdbc.query("SELECT username,teacher_public_id FROM users ORDER BY is_admin DESC,id LIMIT 1",
          (rs, n) -> new TeacherOwner(rs.getString(1), rs.getString(2)));
    } else {
      values = jdbc.query("SELECT username,teacher_public_id FROM users WHERE teacher_public_id=?",
          (rs, n) -> new TeacherOwner(rs.getString(1), rs.getString(2)), teacherId);
    }
    if (values.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "教师 ID 不存在");
    }
    return values.getFirst();
  }

  private String lookup(String sql, Object... args) {
    try {
      return jdbc.queryForObject(sql, String.class, args);
    } catch (EmptyResultDataAccessException ignored) {
      return null;
    }
  }

  private Instant latestUpdate(String owner) {
    Timestamp value = jdbc.queryForObject(
        "SELECT MAX(updated_at) FROM ranking_lesson_results WHERE owner_username=?", Timestamp.class, owner);
    return value == null ? null : value.toInstant();
  }

  private String text(String value, String label, int max) {
    String normalized = value == null ? "" : value.trim();
    if (normalized.isEmpty()) {
      throw invalid(label + "不能为空");
    }
    if (normalized.length() > max) {
      throw invalid(label + "不能超过 " + max + " 个字符");
    }
    return normalized;
  }

  private ResponseStatusException invalid(String message) {
    return new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, message);
  }

  private int level(int points) {
    if (points >= 5400) return 6;
    if (points >= 4200) return 5;
    if (points >= 2700) return 4;
    if (points >= 1500) return 3;
    if (points >= 600) return 2;
    return 1;
  }

  private String levelName(int level) {
    return switch (level) {
      case 6 -> "钻石";
      case 5 -> "蓝宝石";
      case 4 -> "黄金";
      case 3 -> "白银";
      case 2 -> "青铜";
      default -> "石墨";
    };
  }

  private String get(java.sql.ResultSet rs, int column) {
    try {
      return rs.getString(column);
    } catch (java.sql.SQLException error) {
      throw new IllegalStateException(error);
    }
  }

  private record TeacherOwner(String username, String teacherId) {}
  private record AggregateRow(String studentId, String studentName, String campId, String classId, String className, int totalPoints,
                              int adjustmentPoints, int spentPoints, int availablePoints, int completionPoints, int inclassPoints, int homeworkPoints, int lessonCount,
                              Instant scoreReachedAt, int accuracyBasisPoints) {}
  private record RankedRow(int rank, AggregateRow row) {}
  private record SnapshotScore(String studentId, int totalPoints) {}
  private record OpportunityStats(int homeworkMissing, int homeworkReview, int inclassMissing, int inclassReview,
                                  int incompleteLessons) {}

  private static final class MutableCamp {
    private final String id;
    private final String name;
    private final List<RankingPayload.ClassOption> classes = new ArrayList<>();

    private MutableCamp(String id, String name) {
      this.id = id;
      this.name = name;
    }
  }
}
