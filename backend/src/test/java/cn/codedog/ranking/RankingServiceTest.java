package cn.codedog.ranking;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.web.server.ResponseStatusException;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RankingServiceTest {
  private JdbcTemplate jdbc;
  private RankingService service;

  @BeforeEach
  void setUp() {
    DriverManagerDataSource dataSource = new DriverManagerDataSource(
      "jdbc:h2:mem:ranking-" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE", "sa", "");
    jdbc = new JdbcTemplate(dataSource);
    jdbc.execute("""
      CREATE TABLE users (
        id BIGINT AUTO_INCREMENT PRIMARY KEY,
        username VARCHAR(50) NOT NULL UNIQUE,
        teacher_public_id VARCHAR(11) NOT NULL UNIQUE,
        is_admin BOOLEAN NOT NULL
      )
      """);
    jdbc.execute("""
      CREATE TABLE ranking_teacher_mappings (
        crm_teacher_id VARCHAR(100) PRIMARY KEY,
        owner_username VARCHAR(50) NOT NULL UNIQUE
      )
      """);
    jdbc.execute("""
      CREATE TABLE ranking_extension_devices (
        id BIGINT AUTO_INCREMENT PRIMARY KEY,
        token_hash CHAR(64) NOT NULL UNIQUE,
        owner_username VARCHAR(50) NOT NULL,
        device_name VARCHAR(100) NOT NULL,
        created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
        last_seen_at TIMESTAMP(6),
        revoked_at TIMESTAMP(6)
      )
      """);
    jdbc.execute("""
      CREATE TABLE ranking_camps (
        owner_username VARCHAR(50) NOT NULL,
        camp_id VARCHAR(100) NOT NULL,
        camp_name VARCHAR(160) NOT NULL,
        updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
        PRIMARY KEY (owner_username, camp_id)
      )
      """);
    jdbc.execute("""
      CREATE TABLE ranking_classes (
        owner_username VARCHAR(50) NOT NULL,
        camp_id VARCHAR(100) NOT NULL,
        class_id VARCHAR(100) NOT NULL,
        class_name VARCHAR(160) NOT NULL,
        PRIMARY KEY (owner_username, camp_id, class_id)
      )
      """);
    jdbc.execute("""
      CREATE TABLE ranking_students (
        owner_username VARCHAR(50) NOT NULL,
        camp_id VARCHAR(100) NOT NULL,
        class_id VARCHAR(100) NOT NULL,
        student_id VARCHAR(100) NOT NULL,
        student_name VARCHAR(100) NOT NULL,
        score_reached_at TIMESTAMP(6) NOT NULL,
        PRIMARY KEY (owner_username, camp_id, class_id, student_id)
      )
      """);
    jdbc.execute("""
      CREATE TABLE ranking_lesson_results (
        owner_username VARCHAR(50) NOT NULL,
        camp_id VARCHAR(100) NOT NULL,
        class_id VARCHAR(100) NOT NULL,
        lesson_id VARCHAR(100) NOT NULL,
        student_id VARCHAR(100) NOT NULL,
        completion_points INT NOT NULL,
        inclass_points INT NOT NULL,
        homework_points INT NOT NULL,
        total_points INT NOT NULL,
        updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
        PRIMARY KEY (owner_username, camp_id, class_id, lesson_id, student_id)
      )
      """);
    jdbc.execute("""
      CREATE TABLE ranking_daily_snapshots (
        snapshot_date DATE NOT NULL,
        owner_username VARCHAR(50) NOT NULL,
        camp_id VARCHAR(100) NOT NULL,
        scope_type VARCHAR(10) NOT NULL,
        class_id VARCHAR(100) NOT NULL,
        student_id VARCHAR(100) NOT NULL,
        rank_no INT NOT NULL,
        total_points INT NOT NULL,
        PRIMARY KEY (snapshot_date, owner_username, camp_id, scope_type, class_id, student_id)
      )
      """);
    jdbc.update("INSERT INTO users(username,teacher_public_id,is_admin) VALUES('admin','CD-ADMIN001',TRUE)");
    jdbc.update("INSERT INTO users(username,teacher_public_id,is_admin) VALUES('teacher-b','CD-TEACHER2',FALSE)");
    jdbc.update("INSERT INTO ranking_teacher_mappings(crm_teacher_id,owner_username) VALUES('29413','admin')");
    jdbc.update("INSERT INTO ranking_teacher_mappings(crm_teacher_id,owner_username) VALUES('555','teacher-b')");
    addScope("admin", "测试营", "测试班");
    addScope("teacher-b", "B老师营", "B老师班");
    service = new RankingService(jdbc);
  }

  @Test
  void ordersTiesDeterministicallyButAssignsCompetitionRanks() {
    addStudent("admin", "early-low", "先到", "2026-01-01T10:00:00Z", 100, 50, 50);
    addStudent("admin", "late-high", "后到", "2026-01-01T11:00:00Z", 0, 100, 100);
    addStudent("admin", "a-low", "低正确率", "2026-01-01T12:00:00Z", 100, 25, 25);
    addStudent("admin", "z-high", "高正确率", "2026-01-01T12:00:00Z", 0, 75, 75);

    RankingPayload.Board board = service.board("CD-ADMIN001", "camp", "class", "class");

    assertThat(board.rankings()).extracting(RankingPayload.Entry::studentId)
      .containsExactly("early-low", "late-high", "z-high", "a-low");
    assertThat(board.rankings()).extracting(RankingPayload.Entry::rank)
      .containsExactly(1, 1, 3, 3);
    assertThat(board.rankings().get(0).accuracyRate()).isEqualTo(50.0);
    assertThat(board.rankings().get(1).accuracyRate()).isEqualTo(100.0);
  }

  @Test
  void skipsFollowingRanksAfterThreeStudentsTieForFirst() {
    addStudent("admin", "one", "甲", "2026-01-01T10:00:00Z", 100, 0, 0);
    addStudent("admin", "two", "乙", "2026-01-01T11:00:00Z", 0, 50, 50);
    addStudent("admin", "three", "丙", "2026-01-01T12:00:00Z", 40, 30, 30);
    addStudent("admin", "four", "丁", "2026-01-01T13:00:00Z", 30, 30, 30);

    RankingPayload.Board board = service.board("CD-ADMIN001", "camp", "class", "class");

    assertThat(board.rankings()).extracting(RankingPayload.Entry::totalPoints)
      .containsExactly(100, 100, 100, 90);
    assertThat(board.rankings()).extracting(RankingPayload.Entry::rank)
      .containsExactly(1, 1, 1, 4);
  }

  @Test
  void reportsUpDownSameAndNewAgainstMostRecentEarlierSnapshot() {
    addStudent("admin", "up", "上升", "2026-01-01T10:00:00Z", 200, 100, 100);
    addStudent("admin", "down", "下降", "2026-01-01T10:00:00Z", 100, 100, 100);
    addStudent("admin", "same", "持平", "2026-01-01T10:00:00Z", 0, 100, 100);
    addStudent("admin", "new", "新增", "2026-01-01T10:00:00Z", 0, 50, 50);
    LocalDate yesterday = LocalDate.now(ZoneId.of("Asia/Shanghai")).minusDays(1);
    addSnapshot("admin", yesterday, "up", 2, 300);
    addSnapshot("admin", yesterday, "down", 1, 400);
    addSnapshot("admin", yesterday, "same", 3, 200);

    RankingPayload.Board board = service.board("CD-ADMIN001", "camp", "class", "class");

    assertThat(board.trendBaselineDate()).isEqualTo(yesterday);
    assertTrend(board, "up", 2, 1, "UP");
    assertTrend(board, "down", 1, -1, "DOWN");
    assertTrend(board, "same", 3, 0, "SAME");
    assertTrend(board, "new", null, 0, "NEW");
  }

  @Test
  void isolatesIdenticalRankingKeysByMappedTeacher() {
    addStudent("admin", "same-student", "A老师学员", "2026-01-01T10:00:00Z", 100, 100, 100);
    addStudent("teacher-b", "same-student", "B老师学员", "2026-01-01T10:00:00Z", 10, 20, 30);

    RankingPayload.Board boardA = service.board("CD-ADMIN001", "camp", "class", "class");
    RankingPayload.Board boardB = service.board("CD-TEACHER2", "camp", "class", "class");

    assertThat(boardA.rankings()).singleElement().satisfies(row -> {
      assertThat(row.studentName()).isEqualTo("A老师学员");
      assertThat(row.totalPoints()).isEqualTo(300);
    });
    assertThat(boardB.rankings()).singleElement().satisfies(row -> {
      assertThat(row.studentName()).isEqualTo("B老师学员");
      assertThat(row.totalPoints()).isEqualTo(60);
    });
  }

  @Test
  void bootstrapsMappedCrmTeacherAndAuthenticatesIssuedToken() {
    RankingPayload.Connection connection = service.bootstrap("29413", "Chrome 测试设备");

    assertThat(connection.username()).isEqualTo("admin");
    assertThat(connection.teacherId()).isEqualTo("CD-ADMIN001");
    assertThat(connection.crmTeacherId()).isEqualTo("29413");
    assertThat(service.authenticateToken("Bearer " + connection.token())).isEqualTo("admin");
  }

  @Test
  void restoresMappedSessionFromLegacyDeviceToken() {
    RankingPayload.Connection connection = service.bootstrap("29413", "Chrome 旧设备");

    RankingPayload.ExtensionSession session = service.session("Bearer " + connection.token());

    assertThat(session.deviceId()).isEqualTo(connection.deviceId());
    assertThat(session.username()).isEqualTo("admin");
    assertThat(session.teacherId()).isEqualTo("CD-ADMIN001");
    assertThat(session.crmTeacherId()).isEqualTo("29413");
  }

  @Test
  void rejectsRevokedDeviceSession() {
    RankingPayload.Connection connection = service.bootstrap("29413", "Chrome 已撤销设备");
    jdbc.update("UPDATE ranking_extension_devices SET revoked_at=CURRENT_TIMESTAMP WHERE id=?", connection.deviceId());

    assertThatThrownBy(() -> service.session("Bearer " + connection.token()))
      .isInstanceOf(ResponseStatusException.class)
      .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode().value()).isEqualTo(401));
  }

  @Test
  void rejectsUnknownCrmTeacher() {
    assertThatThrownBy(() -> service.bootstrap("unknown", "Chrome 测试设备"))
      .isInstanceOf(ResponseStatusException.class)
      .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode().value()).isEqualTo(404));
  }

  private void addScope(String owner, String campName, String className) {
    jdbc.update("INSERT INTO ranking_camps(owner_username,camp_id,camp_name) VALUES(?,'camp',?)", owner, campName);
    jdbc.update("INSERT INTO ranking_classes(owner_username,camp_id,class_id,class_name) VALUES(?,'camp','class',?)", owner, className);
  }

  private void addStudent(String owner, String id, String name, String reachedAt, int completion, int inclass, int homework) {
    jdbc.update("INSERT INTO ranking_students(owner_username,camp_id,class_id,student_id,student_name,score_reached_at) VALUES(?,'camp','class',?,?,?)",
      owner, id, name, Timestamp.from(Instant.parse(reachedAt)));
    jdbc.update("INSERT INTO ranking_lesson_results(owner_username,camp_id,class_id,lesson_id,student_id,completion_points,inclass_points,homework_points,total_points) VALUES(?,'camp','class','lesson',?,?,?,?,?)",
      owner, id, completion, inclass, homework, completion + inclass + homework);
  }

  private void addSnapshot(String owner, LocalDate date, String studentId, int rank, int points) {
    jdbc.update("INSERT INTO ranking_daily_snapshots(snapshot_date,owner_username,camp_id,scope_type,class_id,student_id,rank_no,total_points) VALUES(?,?,'camp','class','class',?,?,?)",
      date, owner, studentId, rank, points);
  }

  private void assertTrend(RankingPayload.Board board, String studentId, Integer previousRank, int change, String trend) {
    RankingPayload.Entry entry = board.rankings().stream().filter(row -> row.studentId().equals(studentId)).findFirst().orElseThrow();
    assertThat(entry.previousRank()).isEqualTo(previousRank);
    assertThat(entry.rankChange()).isEqualTo(change);
    assertThat(entry.trend()).isEqualTo(trend);
  }
}
