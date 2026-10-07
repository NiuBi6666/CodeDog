package cn.codedog.controller;

import cn.codedog.model.RankingRewardPayload;
import cn.codedog.service.AuditService;
import cn.codedog.service.RankingAnnouncementBroadcaster;
import cn.codedog.service.RankingRewardService;
import cn.codedog.service.StudentRankingAuthService;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RankingRewardPublicControllerTest {
  @Mock RankingRewardService rewards;
  @Mock StudentRankingAuthService studentAuth;
  @Mock RankingAnnouncementBroadcaster announcements;
  @Mock AuditService audit;
  @Mock HttpServletRequest request;
  private RankingRewardPublicController controller;
  private StudentRankingAuthService.StudentSession student;

  @BeforeEach
  void setUp() {
    controller = new RankingRewardPublicController(rewards, studentAuth, announcements, audit);
    student = new StudentRankingAuthService.StudentSession(
      "teacher-a", "student-1", "张同学", "13800000000", false);
  }

  @Test
  void listsOnlyTheStudentFromTheAuthenticatedSessionAndAuditsTheRead() {
    var redemption = redemption(17, 9, 120);
    when(studentAuth.current(request)).thenReturn(student);
    when(rewards.studentRedemptions("teacher-a", "student-1")).thenReturn(List.of(redemption));

    var values = controller.studentRedemptions(request);

    assertThat(values).containsExactly(redemption);
    verify(rewards).studentRedemptions("teacher-a", "student-1");
    verify(audit).record(
      "student_redemptions_viewed:owner=teacher-a:student_id=student-1:count=1", request);
  }

  @Test
  void rejectsRedemptionHistoryWhenTheStudentSessionIsMissing() {
    when(studentAuth.current(request)).thenThrow(
      new ResponseStatusException(HttpStatus.UNAUTHORIZED, "学生登录已失效，请重新登录"));

    assertThatThrownBy(() -> controller.studentRedemptions(request))
      .isInstanceOf(ResponseStatusException.class)
      .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode())
        .isEqualTo(HttpStatus.UNAUTHORIZED));
    verify(audit).record(
      "student_redemptions_view_failed:status=401", request);
  }

  @Test
  void auditsAStudentRedemptionWithItsStableIdentifiersAndPoints() {
    var redemption = redemption(23, 11, 180);
    when(studentAuth.current(request)).thenReturn(student);
    when(rewards.createStudentRedemption("teacher-a", "student-1", 11)).thenReturn(redemption);

    assertThat(controller.redeem(11, request)).isEqualTo(redemption);

    verify(audit).record(
      "student_reward_redeemed:owner=teacher-a:student_id=student-1:redemption_id=23:reward_id=11:points=180",
      request);
  }

  @Test
  void auditsARejectedRedemptionWithoutSensitiveSessionData() {
    when(studentAuth.current(request)).thenReturn(student);
    when(rewards.createStudentRedemption("teacher-a", "student-1", 11)).thenThrow(
      new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "可用积分不足"));

    assertThatThrownBy(() -> controller.redeem(11, request))
      .isInstanceOf(ResponseStatusException.class)
      .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode())
        .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY));
    verify(audit).record(
      "student_reward_redeem_failed:owner=teacher-a:student_id=student-1:reward_id=11:status=422",
      request);
  }

  private RankingRewardPayload.Redemption redemption(long id, long rewardId, int points) {
    return new RankingRewardPayload.Redemption(
      id, "student-1", "张同学", rewardId, "测试奖品", points,
      "PENDING", Instant.parse("2026-10-07T04:34:00Z"), null, null);
  }
}
