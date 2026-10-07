package cn.codedog.ranking;

import cn.codedog.model.RankingRewardPayload;
import cn.codedog.service.RankingRewardService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RankingRewardServiceTest {
  private JdbcTemplate jdbc;
  private RankingRewardService service;

  @BeforeEach
  void setUp() {
    DriverManagerDataSource dataSource = new DriverManagerDataSource(
      "jdbc:h2:mem:rewards-" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE", "sa", "");
    jdbc = new JdbcTemplate(dataSource);
    jdbc.execute("CREATE TABLE users(id BIGINT AUTO_INCREMENT PRIMARY KEY,username VARCHAR(50) NOT NULL UNIQUE,is_admin BOOLEAN NOT NULL)");
    jdbc.execute("""
      CREATE TABLE ranking_board_settings(
        owner_username VARCHAR(50) PRIMARY KEY,announcement VARCHAR(500) NOT NULL DEFAULT '',
        updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP)
      """);
    jdbc.execute("""
      CREATE TABLE ranking_announcements(
        id BIGINT AUTO_INCREMENT PRIMARY KEY,owner_username VARCHAR(50) NOT NULL,announcement_text VARCHAR(500) NOT NULL,
        enabled BOOLEAN NOT NULL DEFAULT FALSE,publish_at TIMESTAMP(6),unpublish_at TIMESTAMP(6),
        created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP)
      """);
    jdbc.execute("""
      CREATE TABLE ranking_rewards(
        id BIGINT AUTO_INCREMENT PRIMARY KEY,owner_username VARCHAR(50) NOT NULL,reward_name VARCHAR(100) NOT NULL,
        required_points INT NOT NULL,image_data BLOB,image_content_type VARCHAR(50),enabled BOOLEAN NOT NULL,
        created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP)
      """);
    jdbc.execute("""
      CREATE TABLE ranking_students(
        owner_username VARCHAR(50) NOT NULL,camp_id VARCHAR(100) NOT NULL,class_id VARCHAR(100) NOT NULL,
        student_id VARCHAR(100) NOT NULL,student_name VARCHAR(100) NOT NULL,
        updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
        PRIMARY KEY(owner_username,camp_id,class_id,student_id))
      """);
    jdbc.execute("""
      CREATE TABLE ranking_lesson_results(
        owner_username VARCHAR(50) NOT NULL,camp_id VARCHAR(100) NOT NULL,class_id VARCHAR(100) NOT NULL,
        lesson_id VARCHAR(100) NOT NULL,student_id VARCHAR(100) NOT NULL,total_points INT NOT NULL,
        PRIMARY KEY(owner_username,camp_id,class_id,lesson_id,student_id))
      """);
    jdbc.execute("""
      CREATE TABLE ranking_reward_redemptions(
        id BIGINT AUTO_INCREMENT PRIMARY KEY,owner_username VARCHAR(50) NOT NULL,student_id VARCHAR(100) NOT NULL,
        student_name VARCHAR(100) NOT NULL,reward_id BIGINT,reward_name VARCHAR(100) NOT NULL,points_spent INT NOT NULL,
        balance_before INT NOT NULL,balance_after INT NOT NULL,
        status VARCHAR(20) NOT NULL DEFAULT 'PENDING',redeemed_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
        fulfilled_at TIMESTAMP(6),fulfilled_by VARCHAR(50),
        FOREIGN KEY(reward_id) REFERENCES ranking_rewards(id) ON DELETE SET NULL)
      """);
    jdbc.update("INSERT INTO users(username,is_admin) VALUES('teacher-a',TRUE),('teacher-b',FALSE)");
    addStudent("teacher-a", "student-1", "张同学", 500);
    addStudent("teacher-b", "student-1", "李同学", 900);
    service = new RankingRewardService(jdbc);
  }

  @Test
  void savesAnnouncementsPerTeacher() {
    service.updateAnnouncement("teacher-a", "周五开放兑换");
    service.updateAnnouncement("teacher-b", "B老师公告");

    assertThat(service.announcement("teacher-a").text()).isEqualTo("周五开放兑换");
    assertThat(service.announcement("teacher-b").text()).isEqualTo("B老师公告");
  }

  @Test
  void schedulesAndUpdatesAnnouncementTimes() {
    Instant publishAt = Instant.now().plusSeconds(3600).truncatedTo(ChronoUnit.MICROS);
    Instant unpublishAt = publishAt.plusSeconds(3600);
    var scheduled = service.createAnnouncement("teacher-a", "周五公告", publishAt, unpublishAt);

    assertThat(scheduled.status()).isEqualTo("SCHEDULED");
    assertThat(scheduled.publishAt()).isEqualTo(publishAt);
    assertThat(scheduled.unpublishAt()).isEqualTo(unpublishAt);

    var online = service.updateAnnouncementSchedule("teacher-a", scheduled.id(),
      Instant.now().minusSeconds(5), Instant.now().plusSeconds(3600));
    assertThat(online.status()).isEqualTo("ONLINE");

    var offline = service.setAnnouncementOnline("teacher-a", scheduled.id(), false);
    assertThat(offline.status()).isEqualTo("OFFLINE");
  }

  @Test
  void rejectsInvalidAnnouncementTimeRange() {
    assertThatThrownBy(() -> service.createAnnouncement("teacher-a", "错误时间", Instant.now().plusSeconds(100), Instant.now()))
      .isInstanceOf(ResponseStatusException.class)
      .satisfies(error -> assertThat(((ResponseStatusException) error).getReason()).contains("下线时间必须晚于上线时间"));
  }

  @Test
  void managesRewardImagesAndKeepsRedemptionHistoryAfterDeletion() {
    MockMultipartFile image = new MockMultipartFile("image", "gift.png", "image/png", new byte[]{1, 2, 3, 4});
    var reward = service.createReward("teacher-a", "编程笔记本", 300, true, image);

    assertThat(reward.name()).isEqualTo("编程笔记本");
    assertThat(reward.hasImage()).isTrue();
    assertThat(service.rewardImage("teacher-a", reward.id()).data()).containsExactly(1, 2, 3, 4);
    assertThat(service.rewards("teacher-b")).isEmpty();

    var redemption = service.createRedemption("teacher-a", new RankingRewardPayload.RedemptionRequest("student-1", reward.id()));
    assertThat(redemption.status()).isEqualTo("PENDING");
    assertThat(redemption.pointsSpent()).isEqualTo(300);
    assertThat(redemption.balanceBefore()).isEqualTo(500);
    assertThat(redemption.balanceAfter()).isEqualTo(200);

    service.deleteReward("teacher-a", reward.id());
    var retained = service.redemptions("teacher-a").getFirst();
    assertThat(retained.rewardId()).isNull();
    assertThat(retained.rewardName()).isEqualTo("编程笔记本");
  }

  @Test
  void exposesOnlyEnabledRewardsWithPublicImages() {
    MockMultipartFile image = new MockMultipartFile("image", "gift.png", "image/png", new byte[]{5, 6, 7});
    var enabled = service.createReward("teacher-a", "机械键盘", 800, true, image);
    service.createReward("teacher-a", "暂停售卖", 100, false, null);
    service.createReward("teacher-b", "其他老师奖品", 50, true, null);

    var visible = service.publicRewards();

    assertThat(visible).hasSize(1);
    assertThat(visible.getFirst().name()).isEqualTo("机械键盘");
    assertThat(visible.getFirst().imageUrl()).startsWith("/api/public/rankings/rewards/");
    assertThat(service.publicRewardImage(enabled.id()).data()).containsExactly(5, 6, 7);
    assertThatThrownBy(() -> service.publicRewardImage(9999))
      .isInstanceOf(ResponseStatusException.class);
  }

  @Test
  void preventsOverspendingAndTracksFulfillment() {
    var small = service.createReward("teacher-a", "小奖品", 300, true, null);
    var large = service.createReward("teacher-a", "大奖品", 250, true, null);
    var redemption = service.createRedemption("teacher-a", new RankingRewardPayload.RedemptionRequest("student-1", small.id()));

    assertThat(redemption.balanceBefore()).isEqualTo(500);
    assertThat(redemption.balanceAfter()).isEqualTo(200);
    assertThatThrownBy(() -> service.createRedemption("teacher-a", new RankingRewardPayload.RedemptionRequest("student-1", large.id())))
      .isInstanceOf(ResponseStatusException.class)
      .satisfies(error -> {
        var status = (ResponseStatusException) error;
        assertThat(status.getStatusCode().value()).isEqualTo(422);
        assertThat(status.getReason()).contains("当前可用 200 分");
      });

    var fulfilled = service.setFulfilled("teacher-a", redemption.id(), true, "teacher-a");
    assertThat(fulfilled.status()).isEqualTo("FULFILLED");
    assertThat(fulfilled.fulfilledAt()).isNotNull();
    assertThat(fulfilled.fulfilledBy()).isEqualTo("teacher-a");

    var pending = service.setFulfilled("teacher-a", redemption.id(), false, "teacher-a");
    assertThat(pending.status()).isEqualTo("PENDING");
    assertThat(pending.fulfilledAt()).isNull();
  }

  @Test
  void rejectsDisabledRewardsAndCrossTeacherStudents() {
    var disabled = service.createReward("teacher-a", "停用奖品", 100, false, null);
    assertThatThrownBy(() -> service.createRedemption("teacher-a", new RankingRewardPayload.RedemptionRequest("student-1", disabled.id())))
      .isInstanceOf(ResponseStatusException.class)
      .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode().value()).isEqualTo(422));
    assertThatThrownBy(() -> service.createRedemption("teacher-b", new RankingRewardPayload.RedemptionRequest("student-1", disabled.id())))
      .isInstanceOf(ResponseStatusException.class)
      .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode().value()).isEqualTo(404));
  }

  @Test
  void returnsOnlyTheCurrentStudentsRedemptionsNewestFirst() {
    addStudent("teacher-a", "student-2", "王同学", 500);
    var firstReward = service.createReward("teacher-a", "第一件奖品", 50, true, null);
    var secondReward = service.createReward("teacher-a", "第二件奖品", 60, true, null);
    var otherTeacherReward = service.createReward("teacher-b", "其他老师奖品", 70, true, null);

    var first = service.createStudentRedemption("teacher-a", "student-1", firstReward.id());
    var second = service.createStudentRedemption("teacher-a", "student-1", secondReward.id());
    service.createStudentRedemption("teacher-a", "student-2", firstReward.id());
    service.createStudentRedemption("teacher-b", "student-1", otherTeacherReward.id());
    service.setFulfilled("teacher-a", second.id(), true, "teacher-a");

    var values = service.studentRedemptions("teacher-a", "student-1");

    assertThat(values).extracting(RankingRewardPayload.Redemption::id)
      .containsExactly(second.id(), first.id());
    assertThat(values).extracting(RankingRewardPayload.Redemption::studentName)
      .containsOnly("张同学");
    assertThat(values).extracting(RankingRewardPayload.Redemption::status)
      .containsExactly("FULFILLED", "PENDING");
    assertThat(values.getFirst().fulfilledAt()).isNotNull();
    assertThat(values.getFirst().fulfilledBy()).isNull();
    assertThat(values).extracting(RankingRewardPayload.Redemption::balanceBefore)
      .containsExactly(450, 500);
    assertThat(values).extracting(RankingRewardPayload.Redemption::balanceAfter)
      .containsExactly(390, 450);
  }

  private void addStudent(String owner, String id, String name, int points) {
    jdbc.update("INSERT INTO ranking_students(owner_username,camp_id,class_id,student_id,student_name) VALUES(?,'camp','class',?,?)", owner, id, name);
    jdbc.update("INSERT INTO ranking_lesson_results(owner_username,camp_id,class_id,lesson_id,student_id,total_points) VALUES(?,'camp','class','lesson',?,?)", owner, id, points);
  }
}
