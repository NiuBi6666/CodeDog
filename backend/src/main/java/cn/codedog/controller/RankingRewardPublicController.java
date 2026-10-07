package cn.codedog.controller;

import cn.codedog.model.RankingRewardPayload;
import cn.codedog.service.AuditService;
import cn.codedog.service.RankingAnnouncementBroadcaster;
import cn.codedog.service.StudentRankingAuthService;
import jakarta.servlet.http.HttpServletRequest;
import cn.codedog.service.RankingRewardService;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@RestController
@RequestMapping("/api/public/rankings")
public class RankingRewardPublicController {
  private final RankingRewardService rewards;
  private final StudentRankingAuthService studentAuth;
  private final RankingAnnouncementBroadcaster announcements;
  private final AuditService audit;
  public RankingRewardPublicController(RankingRewardService rewards, StudentRankingAuthService studentAuth,
                                       RankingAnnouncementBroadcaster announcements, AuditService audit) {
    this.rewards = rewards; this.studentAuth = studentAuth; this.announcements = announcements; this.audit = audit;
  }

  @GetMapping("/announcement")
  public RankingRewardPayload.Announcement announcement() { return rewards.publicAnnouncement(); }

  @GetMapping(value = "/announcement/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public ResponseEntity<SseEmitter> announcementEvents(HttpServletRequest request) {
    var student = studentAuth.current(request);
    SseEmitter emitter = announcements.subscribe(student.ownerUsername(), rewards.publicAnnouncement(student.ownerUsername()));
    return ResponseEntity.ok().cacheControl(CacheControl.noCache()).header("X-Accel-Buffering", "no").body(emitter);
  }

  @GetMapping("/rewards")
  public List<RankingRewardPayload.Reward> rewards() { return rewards.publicRewards(); }

  @GetMapping("/student-balance")
  public RankingRewardPayload.Balance balance(HttpServletRequest request) {
    var student = studentAuth.current(request);
    return rewards.balance(student.ownerUsername(), student.studentId());
  }

  @GetMapping("/student-redemptions")
  public List<RankingRewardPayload.Redemption> studentRedemptions(HttpServletRequest request) {
    try {
      var student = studentAuth.current(request);
      var values = rewards.studentRedemptions(student.ownerUsername(), student.studentId());
      audit.record("student_redemptions_viewed:owner=" + student.ownerUsername()
        + ":student_id=" + student.studentId() + ":count=" + values.size(), request);
      return values;
    } catch (org.springframework.web.server.ResponseStatusException error) {
      audit.record("student_redemptions_view_failed:status=" + error.getStatusCode().value(), request);
      throw error;
    }
  }

  @PostMapping("/rewards/{id}/redeem")
  public RankingRewardPayload.Redemption redeem(@PathVariable long id, HttpServletRequest request) {
    StudentRankingAuthService.StudentSession student;
    try {
      student = studentAuth.current(request);
    } catch (org.springframework.web.server.ResponseStatusException error) {
      audit.record("student_reward_redeem_failed:reward_id=" + id
        + ":status=" + error.getStatusCode().value(), request);
      throw error;
    }
    try {
      var value = rewards.createStudentRedemption(student.ownerUsername(), student.studentId(), id);
      audit.record("student_reward_redeemed:owner=" + student.ownerUsername()
        + ":student_id=" + student.studentId() + ":redemption_id=" + value.id()
        + ":reward_id=" + id + ":points=" + value.pointsSpent(), request);
      return value;
    } catch (org.springframework.web.server.ResponseStatusException error) {
      audit.record("student_reward_redeem_failed:owner=" + student.ownerUsername()
        + ":student_id=" + student.studentId() + ":reward_id=" + id
        + ":status=" + error.getStatusCode().value(), request);
      throw error;
    }
  }

  @GetMapping("/rewards/{id}/image")
  public ResponseEntity<byte[]> rewardImage(@PathVariable long id) {
    var image = rewards.publicRewardImage(id);
    return ResponseEntity.ok()
      .cacheControl(CacheControl.noCache())
      .contentType(MediaType.parseMediaType(image.contentType()))
      .body(image.data());
  }
}
