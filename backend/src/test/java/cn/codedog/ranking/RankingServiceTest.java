package cn.codedog.ranking;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RankingServiceTest {
  private JdbcTemplate jdbc;
  private RankingService service;

  @BeforeEach
  void setUp() {
    DriverManagerDataSource dataSource = new DriverManagerDataSource(
      "jdbc:h2:mem:ranking-" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE", "sa", "");
    jdbc = new JdbcTemplate(dataSource);
    jdbc.execute("""
      CREATE TABLE ranking_camps (
        camp_id VARCHAR(100) PRIMARY KEY,
        camp_name VARCHAR(160) NOT NULL,
        updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP
      )
      """);
    jdbc.execute("""
      CREATE TABLE ranking_classes (
        camp_id VARCHAR(100) NOT NULL,
        class_id VARCHAR(100) NOT NULL,
        class_name VARCHAR(160) NOT NULL,
        PRIMARY KEY (camp_id, class_id)
      )
      """);
    jdbc.execute("""
      CREATE TABLE ranking_students (
        camp_id VARCHAR(100) NOT NULL,
        class_id VARCHAR(100) NOT NULL,
        student_id VARCHAR(100) NOT NULL,
        student_name VARCHAR(100) NOT NULL,
        score_reached_at TIMESTAMP(6) NOT NULL,
        PRIMARY KEY (camp_id, class_id, student_id)
      )
      """);
    jdbc.execute("""
      CREATE TABLE ranking_lesson_results (
        camp_id VARCHAR(100) NOT NULL,
        class_id VARCHAR(100) NOT NULL,
        lesson_id VARCHAR(100) NOT NULL,
        student_id VARCHAR(100) NOT NULL,
        completion_points INT NOT NULL,
        inclass_points INT NOT NULL,
        homework_points INT NOT NULL,
        total_points INT NOT NULL,
        updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
        PRIMARY KEY (camp_id, class_id, lesson_id, student_id)
      )
      """);
    jdbc.execute("""
      CREATE TABLE ranking_daily_snapshots (
        snapshot_date DATE NOT NULL,
        camp_id VARCHAR(100) NOT NULL,
        scope_type VARCHAR(10) NOT NULL,
        class_id VARCHAR(100) NOT NULL,
        student_id VARCHAR(100) NOT NULL,
        rank_no INT NOT NULL,
        total_points INT NOT NULL,
        PRIMARY KEY (snapshot_date, camp_id, scope_type, class_id, student_id)
      )
      """);
    jdbc.update("INSERT INTO ranking_camps(camp_id,camp_name) VALUES('camp','测试营')");
    jdbc.update("INSERT INTO ranking_classes(camp_id,class_id,class_name) VALUES('camp','class','测试班')");
    service = new RankingService(jdbc);
  }

  @Test
  void resolvesTiesByReachedTimeThenCombinedAssignmentAccuracy() {
    addStudent("early-low", "先到", "2026-01-01T10:00:00Z", 100, 50, 50);
    addStudent("late-high", "后到", "2026-01-01T11:00:00Z", 0, 100, 100);
    addStudent("a-low", "低正确率", "2026-01-01T12:00:00Z", 100, 25, 25);
    addStudent("z-high", "高正确率", "2026-01-01T12:00:00Z", 0, 75, 75);

    RankingPayload.Board board = service.board("camp", "class", "class");

    assertThat(board.rankings()).extracting(RankingPayload.Entry::studentId)
      .containsExactly("early-low", "late-high", "z-high", "a-low");
    assertThat(board.rankings()).extracting(RankingPayload.Entry::rank)
      .containsExactly(1, 2, 3, 4);
    assertThat(board.rankings().get(0).accuracyRate()).isEqualTo(50.0);
    assertThat(board.rankings().get(1).accuracyRate()).isEqualTo(100.0);
  }

  @Test
  void reportsUpDownSameAndNewAgainstMostRecentEarlierSnapshot() {
    addStudent("up", "上升", "2026-01-01T10:00:00Z", 200, 100, 100);
    addStudent("down", "下降", "2026-01-01T10:00:00Z", 100, 100, 100);
    addStudent("same", "持平", "2026-01-01T10:00:00Z", 0, 100, 100);
    addStudent("new", "新增", "2026-01-01T10:00:00Z", 0, 50, 50);
    LocalDate yesterday = LocalDate.now(ZoneId.of("Asia/Shanghai")).minusDays(1);
    addSnapshot(yesterday, "up", 2, 300);
    addSnapshot(yesterday, "down", 1, 300);
    addSnapshot(yesterday, "same", 3, 200);

    RankingPayload.Board board = service.board("camp", "class", "class");

    assertThat(board.trendBaselineDate()).isEqualTo(yesterday);
    assertTrend(board, "up", 2, 1, "UP");
    assertTrend(board, "down", 1, -1, "DOWN");
    assertTrend(board, "same", 3, 0, "SAME");
    assertTrend(board, "new", null, 0, "NEW");
  }

  private void addStudent(String id, String name, String reachedAt, int completion, int inclass, int homework) {
    jdbc.update("INSERT INTO ranking_students(camp_id,class_id,student_id,student_name,score_reached_at) VALUES('camp','class',?,?,?)",
      id, name, Timestamp.from(Instant.parse(reachedAt)));
    jdbc.update("INSERT INTO ranking_lesson_results(camp_id,class_id,lesson_id,student_id,completion_points,inclass_points,homework_points,total_points) VALUES('camp','class','lesson',?,?,?,?,?)",
      id, completion, inclass, homework, completion + inclass + homework);
  }

  private void addSnapshot(LocalDate date, String studentId, int rank, int points) {
    jdbc.update("INSERT INTO ranking_daily_snapshots(snapshot_date,camp_id,scope_type,class_id,student_id,rank_no,total_points) VALUES(?,'camp','class','class',?,?,?)",
      date, studentId, rank, points);
  }

  private void assertTrend(RankingPayload.Board board, String studentId, Integer previousRank, int change, String trend) {
    RankingPayload.Entry entry = board.rankings().stream().filter(row -> row.studentId().equals(studentId)).findFirst().orElseThrow();
    assertThat(entry.previousRank()).isEqualTo(previousRank);
    assertThat(entry.rankChange()).isEqualTo(change);
    assertThat(entry.trend()).isEqualTo(trend);
  }
}
