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
      System.getProperty("reward.test.url", "jdbc:h2:mem:rewards-" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE"), System.getProperty("reward.test.user", "sa"), System.getProperty("reward.test.password", ""));
    jdbc = new JdbcTemplate(dataSource);
    cn.codedog.OperationsTestSchema.create(jdbc);
    if (System.getProperty("reward.test.url") != null) {
      // Only the explicitly supplied isolated test database is reset, never the application datasource.
      if (!System.getProperty("reward.test.url").contains("/reward_stock_test")) throw new IllegalStateException("Isolated test database required");
      jdbc.execute("SET FOREIGN_KEY_CHECKS=0");
      for (String table : new String[]{"ranking_reward_redemptions", "ranking_rewards", "ranking_point_adjustments", "ranking_lesson_results", "ranking_students", "ranking_announcements", "ranking_board_settings", "users"}) jdbc.execute("DROP TABLE IF EXISTS " + table);
      jdbc.execute("SET FOREIGN_KEY_CHECKS=1");
    }
    jdbc.execute("CREATE TABLE users(id BIGINT AUTO_INCREMENT PRIMARY KEY,username VARCHAR(50) NOT NULL UNIQUE,enabled BOOLEAN DEFAULT TRUE,is_admin BOOLEAN NOT NULL)");
    jdbc.execute("""
      CREATE TABLE ranking_board_settings(
        owner_username VARCHAR(50) PRIMARY KEY,announcement VARCHAR(500) NOT NULL DEFAULT '',
        updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6))
      """);
    jdbc.execute("""
      CREATE TABLE ranking_announcements(
        id BIGINT AUTO_INCREMENT PRIMARY KEY,owner_username VARCHAR(50) NOT NULL,announcement_text VARCHAR(500) NOT NULL,
        enabled BOOLEAN NOT NULL DEFAULT FALSE,publish_at TIMESTAMP(6),unpublish_at TIMESTAMP(6),
        created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6))
      """);
    jdbc.execute("""
      CREATE TABLE ranking_rewards(
        id BIGINT AUTO_INCREMENT PRIMARY KEY,owner_username VARCHAR(50) NOT NULL,reward_name VARCHAR(100) NOT NULL,
        stock INT NOT NULL DEFAULT 0 CHECK (stock >= 0),deleted_at TIMESTAMP(6),required_points INT NOT NULL,image_data BLOB,image_content_type VARCHAR(50),enabled BOOLEAN NOT NULL,
        created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6))
      """);
    jdbc.execute("""
      CREATE TABLE ranking_students(
        owner_username VARCHAR(50) NOT NULL,camp_id VARCHAR(100) NOT NULL,class_id VARCHAR(100) NOT NULL,
        student_id VARCHAR(100) NOT NULL,student_name VARCHAR(100) NOT NULL,
        updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
        PRIMARY KEY(owner_username,camp_id,class_id,student_id))
      """);
    jdbc.execute("""
      CREATE TABLE ranking_lesson_results(
        owner_username VARCHAR(50) NOT NULL,camp_id VARCHAR(100) NOT NULL,class_id VARCHAR(100) NOT NULL,
        lesson_id VARCHAR(100) NOT NULL,student_id VARCHAR(100) NOT NULL,total_points INT NOT NULL,
        PRIMARY KEY(owner_username,camp_id,class_id,lesson_id,student_id))
      """);
    jdbc.execute("""
      CREATE TABLE ranking_point_adjustments(
        id BIGINT AUTO_INCREMENT PRIMARY KEY,
        owner_username VARCHAR(50) NOT NULL,
        camp_id VARCHAR(100) NOT NULL,
        class_id VARCHAR(100) NOT NULL,
        student_id VARCHAR(100) NOT NULL,
        points INT NOT NULL,balance_before INT,balance_after INT,
        reason VARCHAR(255) NOT NULL,
        actor VARCHAR(100) NOT NULL
      )
      """);
    jdbc.execute("""
      CREATE TABLE ranking_reward_redemptions(
        id BIGINT AUTO_INCREMENT PRIMARY KEY,owner_username VARCHAR(50) NOT NULL,student_id VARCHAR(100) NOT NULL,
        student_name VARCHAR(100) NOT NULL,reward_id BIGINT,reward_name VARCHAR(100) NOT NULL,points_spent INT NOT NULL,
        balance_before INT NOT NULL,balance_after INT NOT NULL,
        request_key VARCHAR(80),status VARCHAR(20) NOT NULL DEFAULT 'PENDING',redeemed_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
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
  void acceptsMaximumRewardPointsAndRejectsValuesAboveIt() {
    var reward = service.createReward("teacher-a", "最高积分奖品", 999_999_999, true, null, 10);

    assertThat(reward.requiredPoints()).isEqualTo(999_999_999);
    assertThatThrownBy(() -> service.createReward("teacher-a", "超出上限奖品", 1_000_000_000, true, null, 10))
      .isInstanceOf(ResponseStatusException.class)
      .satisfies(error -> {
        var status = (ResponseStatusException) error;
        assertThat(status.getStatusCode().value()).isEqualTo(422);
        assertThat(status.getReason()).contains("1 至 999999999");
      });
  }

  @Test
  void managesRewardImagesAndKeepsRedemptionHistoryAfterDeletion() {
    MockMultipartFile image = new MockMultipartFile("image", "gift.png", "image/png", new byte[]{1, 2, 3, 4});
    var reward = service.createReward("teacher-a", "编程笔记本", 300, true, image, 10);

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
    assertThat(retained.rewardId()).isEqualTo(reward.id());
    assertThat(service.rewards("teacher-a")).isEmpty();
    assertThat(retained.rewardName()).isEqualTo("编程笔记本");
  }

  @Test
  void exposesOnlyEnabledRewardsWithPublicImages() {
    MockMultipartFile image = new MockMultipartFile("image", "gift.png", "image/png", new byte[]{5, 6, 7});
    var enabled = service.createReward("teacher-a", "机械键盘", 800, true, image, 10);
    service.createReward("teacher-a", "暂停售卖", 100, false, null, 10);
    service.createReward("teacher-b", "其他老师奖品", 50, true, null, 10);

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
    var small = service.createReward("teacher-a", "小奖品", 300, true, null, 10);
    var large = service.createReward("teacher-a", "大奖品", 250, true, null, 10);
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
    var disabled = service.createReward("teacher-a", "停用奖品", 100, false, null, 10);
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
    var firstReward = service.createReward("teacher-a", "第一件奖品", 50, true, null, 10);
    var secondReward = service.createReward("teacher-a", "第二件奖品", 60, true, null, 10);
    var otherTeacherReward = service.createReward("teacher-b", "其他老师奖品", 70, true, null, 10);

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

  private org.springframework.transaction.support.TransactionTemplate transactions() {
    return new org.springframework.transaction.support.TransactionTemplate(
      new org.springframework.jdbc.datasource.DataSourceTransactionManager(jdbc.getDataSource()));
  }

  @Test
  void validatesStockAndPreservesItWhenAnOldClientOnlyEditsDetails() {
    var reward = service.createReward("teacher-a", "库存", 100, true, null, 3);
    assertThat(reward.stock()).isEqualTo(3);
    assertThatThrownBy(() -> service.createReward("teacher-a", "负库存", 100, true, null, -1)).isInstanceOf(ResponseStatusException.class);
    assertThatThrownBy(() -> service.createReward("teacher-a", "超限库存", 100, true, null, 1_000_000_000)).isInstanceOf(ResponseStatusException.class);
    transactions().executeWithoutResult(status -> service.updateReward("teacher-a", reward.id(), "修改名称", 100, true, null, null, null));
    assertThat(service.rewards("teacher-a").getFirst().stock()).isEqualTo(3);
    transactions().executeWithoutResult(status -> service.createStudentRedemption("teacher-a", "student-1", reward.id()));
    assertThatThrownBy(() -> transactions().executeWithoutResult(status -> service.updateReward("teacher-a", reward.id(), "旧表单", 100, true, null, 10, 3)))
      .isInstanceOf(ResponseStatusException.class)
      .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode().value()).isEqualTo(409));
    assertThat(service.rewards("teacher-a").getFirst().stock()).isEqualTo(2);
    assertThat(service.rewards("teacher-a").getFirst().name()).isEqualTo("修改名称");
    transactions().executeWithoutResult(status -> service.updateReward("teacher-a", reward.id(), "补货", 100, true, null, 8, 2));
    assertThat(service.rewards("teacher-a").getFirst().stock()).isEqualTo(8);
  }

  @Test
  void soldOutAndInsufficientPointsNeverSpendStockOrPoints() {
    var sold = service.createReward("teacher-a", "售罄", 100, true, null, 0);
    assertThat(service.publicRewards().getFirst().stock()).isZero();
    assertThatThrownBy(() -> transactions().executeWithoutResult(status -> service.createStudentRedemption("teacher-a", "student-1", sold.id())))
      .isInstanceOf(ResponseStatusException.class).hasMessageContaining("已兑完");
    var expensive = service.createReward("teacher-a", "买不起", 600, true, null, 2);
    assertThatThrownBy(() -> transactions().executeWithoutResult(status -> service.createStudentRedemption("teacher-a", "student-1", expensive.id())))
      .isInstanceOf(ResponseStatusException.class).hasMessageContaining("积分不足");
    assertThat(service.balance("teacher-a", "student-1").availablePoints()).isEqualTo(500);
    assertThat(service.redemptions("teacher-a")).isEmpty();
    assertThat(service.rewards("teacher-a").stream().filter(x -> x.id()==expensive.id()).findFirst().orElseThrow().stock()).isEqualTo(2);
  }

  @Test
  void failedRedemptionInsertRollsBackInventory() {
    var reward = service.createReward("teacher-a", "回滚测试", 100, true, null, 1);
    jdbc.execute("ALTER TABLE ranking_reward_redemptions ADD CONSTRAINT force_test_failure CHECK (points_spent < 1)");
    assertThatThrownBy(() -> transactions().executeWithoutResult(status -> service.createStudentRedemption("teacher-a", "student-1", reward.id())))
      .isInstanceOf(org.springframework.dao.DataAccessException.class);
    assertThat(service.rewards("teacher-a").getFirst().stock()).isEqualTo(1);
    assertThat(service.balance("teacher-a", "student-1").availablePoints()).isEqualTo(500);
    assertThat(service.redemptions("teacher-a")).isEmpty();
  }

  @Test
  void concurrentStudentsCannotOversellTheLastItem() throws Exception {
    var reward = service.createReward("teacher-a", "最后一件", 100, true, null, 1);
    for (int i=0;i<16;i++) addStudent("teacher-a", "race-"+i, "并发学员", 500);
    int success = race(16, i -> "race-"+i, reward.id());
    assertThat(success).isEqualTo(1);
    assertThat(service.rewards("teacher-a").getFirst().stock()).isZero();
    assertThat(service.redemptions("teacher-a")).hasSize(1);
    assertThat(jdbc.queryForObject("SELECT SUM(points_spent) FROM ranking_reward_redemptions", Integer.class)).isEqualTo(100);
    assertThat(jdbc.queryForObject("SELECT SUM(balance_after) FROM ranking_reward_redemptions", Integer.class)).isEqualTo(400);
  }

  @Test
  void simultaneousTabsCannotSpendTheSameBalanceTwice() throws Exception {
    var reward = service.createReward("teacher-a", "多页兑换", 300, true, null, 20);
    assertThat(race(12, i -> "student-1", reward.id())).isEqualTo(1);
    assertThat(service.rewards("teacher-a").getFirst().stock()).isEqualTo(19);
    assertThat(service.balance("teacher-a", "student-1").availablePoints()).isEqualTo(200);
    var redemption = service.redemptions("teacher-a").getFirst();
    service.setFulfilled("teacher-a", redemption.id(), true, "teacher-a");
    service.setFulfilled("teacher-a", redemption.id(), false, "teacher-a");
    assertThat(service.rewards("teacher-a").getFirst().stock()).isEqualTo(19);
  }

  private int race(int count, java.util.function.IntFunction<String> student, long rewardId) throws Exception {
    var pool = java.util.concurrent.Executors.newFixedThreadPool(count);
    var start = new java.util.concurrent.CountDownLatch(1);
    var futures = new java.util.ArrayList<java.util.concurrent.Future<Boolean>>();
    try {
      for (int i=0;i<count;i++) {
        final String id=student.apply(i);
        futures.add(pool.submit(() -> {
          start.await();
          try {
            transactions().executeWithoutResult(status -> service.createStudentRedemption("teacher-a", id, rewardId));
            return true;
          } catch (ResponseStatusException error) {
            assertThat(error.getStatusCode().value()).isEqualTo(422);
            return false;
          }
        }));
      }
      start.countDown();
      int successes=0;
      for (var future:futures) if (future.get(45, java.util.concurrent.TimeUnit.SECONDS)) successes++;
      return successes;
    } finally { pool.shutdownNow(); }
  }

  private void addStudent(String owner, String id, String name, int points) {
    jdbc.update("INSERT INTO ranking_students(owner_username,camp_id,class_id,student_id,student_name) VALUES(?,'camp','class',?,?)", owner, id, name);
    jdbc.update("INSERT INTO ranking_lesson_results(owner_username,camp_id,class_id,lesson_id,student_id,total_points) VALUES(?,'camp','class','lesson',?,?)", owner, id, points);
  }

  @Test void studentEndpointsUseSessionOwnerForRewardsAnnouncementAndImages() {
    var auth=org.mockito.Mockito.mock(cn.codedog.service.StudentRankingAuthService.class);
    var request=new org.springframework.mock.web.MockHttpServletRequest();
    org.mockito.Mockito.when(auth.current(request)).thenReturn(
      new cn.codedog.service.StudentRankingAuthService.StudentSession("teacher-b","student-1","李同学","19900000001",false));
    var controller=new cn.codedog.controller.RankingRewardPublicController(service,auth,null,null);
    var image=new MockMultipartFile("image","gift.png","image/png",new byte[]{1,2,3,4});
    var a=service.createReward("teacher-a","A奖品",10,true,image,1);
    var b=service.createReward("teacher-b","B奖品",20,true,image,1);
    service.updateAnnouncement("teacher-a","A公告");service.updateAnnouncement("teacher-b","B公告");
    assertThat(controller.rewards(request)).extracting(RankingRewardPayload.Reward::name).containsExactly("B奖品");
    assertThat(controller.announcement(request).text()).isEqualTo("B公告");
    assertThat(controller.rewardImage(b.id(),request).getBody()).containsExactly(1,2,3,4);
    assertThatThrownBy(() -> controller.rewardImage(a.id(),request)).isInstanceOf(ResponseStatusException.class)
      .satisfies(error -> assertThat(((ResponseStatusException)error).getStatusCode().value()).isEqualTo(404));
  }

 @Test void cancellationRefundsOnceAndKeepsDeletedRewardOffline(){
  var reward=service.createReward("teacher-a","取消测试",100,true,null,1);
  var order=transactions().execute(status->service.createStudentRedemption("teacher-a","student-1",reward.id(),"request-cancel-123456"));
  assertThat(transactions().execute(status->service.createStudentRedemption("teacher-a","student-1",reward.id(),"request-cancel-123456")).id()).isEqualTo(order.id());
  var aftercare=new cn.codedog.service.RankingAftercareService(jdbc,new cn.codedog.service.RankingPointsService(jdbc),null);
  transactions().executeWithoutResult(status->aftercare.requestCancellation("teacher-a","student-1",order.id(),"误兑换"));
  assertThatThrownBy(()->service.setFulfilled("teacher-a",order.id(),true,"teacher-a")).isInstanceOf(ResponseStatusException.class);
  service.deleteReward("teacher-a",reward.id());
  transactions().executeWithoutResult(status->aftercare.decide("teacher-a",order.id(),true,"身份核实","teacher-a"));
  assertThat(service.balance("teacher-a","student-1").availablePoints()).isEqualTo(500);
  assertThat(jdbc.queryForObject("SELECT stock FROM ranking_rewards WHERE id=?",Integer.class,reward.id())).isEqualTo(1);
  assertThat(service.publicRewards("teacher-a")).isEmpty();
  assertThatThrownBy(()->transactions().executeWithoutResult(status->aftercare.decide("teacher-a",order.id(),true,"重复","teacher-a"))).isInstanceOf(ResponseStatusException.class);
 }
 @Test void cancellationRejectsCrossTenantAndFulfilledOrders(){
  var reward=service.createReward("teacher-a","取消限制",100,true,null,1);
  var order=service.createStudentRedemption("teacher-a","student-1",reward.id());
  var aftercare=new cn.codedog.service.RankingAftercareService(jdbc,new cn.codedog.service.RankingPointsService(jdbc),null);
  assertThatThrownBy(()->aftercare.requestCancellation("teacher-b","student-1",order.id(),"越权")).isInstanceOf(ResponseStatusException.class);
  service.setFulfilled("teacher-a",order.id(),true,"teacher-a");
  assertThatThrownBy(()->aftercare.requestCancellation("teacher-a","student-1",order.id(),"已发放")).isInstanceOf(ResponseStatusException.class);
 }

 @Test void retiredRewardRulesDoNotRestrictRedemptionButStockStillDoes(){
  var reward=service.createReward("teacher-a","基础兑换",1,true,null,2);
  jdbc.update("INSERT INTO ranking_reward_rules(reward_id,owner_username,low_stock_threshold,per_student_limit,starts_at,instructions) VALUES(?,'teacher-a',1,1,?,'已下线的历史规则')",reward.id(),java.sql.Timestamp.from(Instant.now().plusSeconds(3600)));
  transactions().execute(status->service.createStudentRedemption("teacher-a","student-1",reward.id()));
  transactions().execute(status->service.createStudentRedemption("teacher-a","student-1",reward.id()));
  assertThatThrownBy(()->transactions().execute(status->service.createStudentRedemption("teacher-a","student-1",reward.id()))).isInstanceOf(ResponseStatusException.class);
 }
}
