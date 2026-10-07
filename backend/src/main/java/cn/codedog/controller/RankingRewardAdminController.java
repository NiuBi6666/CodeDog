package cn.codedog.controller;

import cn.codedog.model.RankingPayload;
import cn.codedog.model.RankingRewardPayload;
import cn.codedog.service.AuditService;
import cn.codedog.service.RankingBoardService;
import cn.codedog.service.RankingRewardService;
import cn.codedog.service.StudentRankingAuthService;
import cn.codedog.service.PermissionService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/rankings/admin")
public class RankingRewardAdminController {
  private final RankingBoardService rankings;
  private final RankingRewardService rewards;
  private final AuditService audit;
  private final StudentRankingAuthService studentAuth;
  private final PermissionService permissions;

  public RankingRewardAdminController(RankingBoardService rankings, RankingRewardService rewards, AuditService audit,
      StudentRankingAuthService studentAuth, PermissionService permissions) {
    this.rankings = rankings; this.rewards = rewards; this.audit = audit;
    this.studentAuth = studentAuth; this.permissions = permissions;
  }

  @GetMapping("/board")
  public RankingPayload.Board board(Principal principal) { return rankings.allBoardForOwner(principal.getName()); }

  @GetMapping("/students/{studentId}/password")
  public ResponseEntity<StudentRankingAuthService.RecoveredPassword> studentPassword(@PathVariable String studentId,
      Principal principal, HttpServletRequest request) {
    requireSystemAdmin(principal);
    var value = studentAuth.recoveredPassword(principal.getName(), studentId);
    audit.record("student_password_viewed:" + studentId, request);
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(value);
  }

  @PutMapping("/students/{studentId}/password")
  public ResponseEntity<StudentRankingAuthService.RecoveredPassword> resetStudentPassword(@PathVariable String studentId,
      @RequestBody StudentPasswordRequest body, Principal principal, HttpServletRequest request) {
    requireSystemAdmin(principal);
    var value = studentAuth.resetPassword(principal.getName(), studentId, body == null ? null : body.password());
    audit.record("student_password_reset:" + studentId, request);
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(value);
  }

  @GetMapping("/announcement")
  public RankingRewardPayload.Announcement announcement(Principal principal) { return rewards.announcement(principal.getName()); }

  @GetMapping("/announcements")
  public List<RankingRewardPayload.AnnouncementItem> announcements(Principal principal) {
    return rewards.announcements(principal.getName());
  }

  @PostMapping("/announcements")
  @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
  public RankingRewardPayload.AnnouncementItem createAnnouncement(
      @RequestBody RankingRewardPayload.AnnouncementRequest body, Principal principal, HttpServletRequest request) {
    var value = rewards.createAnnouncement(principal.getName(), body == null ? "" : body.text(), body == null ? null : body.publishAt(), body == null ? null : body.unpublishAt());
    audit.record("ranking_announcement_created:" + value.id(), request);
    return value;
  }

  @PatchMapping("/announcements/{id}")
  public RankingRewardPayload.AnnouncementItem announcementSchedule(
      @PathVariable long id, @RequestBody RankingRewardPayload.AnnouncementScheduleRequest body,
      Principal principal, HttpServletRequest request) {
    var value = rewards.updateAnnouncementSchedule(principal.getName(), id,
      body == null ? null : body.text(), body == null ? null : body.publishAt(), body == null ? null : body.unpublishAt());
    audit.record("ranking_announcement_schedule:" + id, request);
    return value;
  }

  @PatchMapping("/announcements/{id}/status")
  public RankingRewardPayload.AnnouncementItem announcementStatus(
      @PathVariable long id, @RequestBody RankingRewardPayload.AnnouncementStatusRequest body,
      Principal principal, HttpServletRequest request) {
    var value = rewards.setAnnouncementOnline(principal.getName(), id, body != null && body.online());
    audit.record("ranking_announcement_status:" + id + ":" + value.status(), request);
    return value;
  }

  @PutMapping("/announcement")
  public RankingRewardPayload.Announcement updateAnnouncement(@RequestBody AnnouncementRequest body, Principal principal, HttpServletRequest request) {
    var value = rewards.updateAnnouncement(principal.getName(), body == null ? "" : body.text());
    audit.record("ranking_announcement_updated", request); return value;
  }

  @GetMapping("/rewards")
  public List<RankingRewardPayload.Reward> rewards(Principal principal) { return rewards.rewards(principal.getName()); }

  @PostMapping(value = "/rewards", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
  public RankingRewardPayload.Reward createReward(@RequestParam String name, @RequestParam Integer requiredPoints,
      @RequestParam(defaultValue = "true") Boolean enabled, @RequestParam(required = false) MultipartFile image,
      Principal principal, HttpServletRequest request) {
    var value = rewards.createReward(principal.getName(), name, requiredPoints, enabled, image);
    audit.record("ranking_reward_created:" + value.id(), request); return value;
  }

  @PutMapping(value = "/rewards/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public RankingRewardPayload.Reward updateReward(@PathVariable long id, @RequestParam String name,
      @RequestParam Integer requiredPoints, @RequestParam(defaultValue = "true") Boolean enabled,
      @RequestParam(required = false) MultipartFile image, Principal principal, HttpServletRequest request) {
    var value = rewards.updateReward(principal.getName(), id, name, requiredPoints, enabled, image);
    audit.record("ranking_reward_updated:" + id, request); return value;
  }

  @DeleteMapping("/rewards/{id}")
  @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
  public void deleteReward(@PathVariable long id, Principal principal, HttpServletRequest request) {
    rewards.deleteReward(principal.getName(), id); audit.record("ranking_reward_deleted:" + id, request);
  }

  @GetMapping("/rewards/{id}/image")
  public ResponseEntity<byte[]> rewardImage(@PathVariable long id, Principal principal) {
    var image = rewards.rewardImage(principal.getName(), id);
    return ResponseEntity.ok().cacheControl(CacheControl.noCache()).contentType(MediaType.parseMediaType(image.contentType())).body(image.data());
  }

  @GetMapping("/redemptions")
  public List<RankingRewardPayload.Redemption> redemptions(Principal principal) { return rewards.redemptions(principal.getName()); }

  @PostMapping("/redemptions")
  @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
  public RankingRewardPayload.Redemption createRedemption(@RequestBody RankingRewardPayload.RedemptionRequest body,
      Principal principal, HttpServletRequest request) {
    var value = rewards.createRedemption(principal.getName(), body);
    audit.record("ranking_redemption_created:" + value.id(), request); return value;
  }

  @PatchMapping("/redemptions/{id}/fulfillment")
  public RankingRewardPayload.Redemption fulfillment(@PathVariable long id,
      @RequestBody RankingRewardPayload.FulfillmentRequest body, Principal principal, HttpServletRequest request) {
    var value = rewards.setFulfilled(principal.getName(), id, body != null && body.fulfilled(), principal.getName());
    audit.record("ranking_redemption_fulfillment:" + id + ":" + value.status(), request); return value;
  }

  private void requireSystemAdmin(Principal principal) {
    if (principal == null || !permissions.isAdmin(principal.getName()))
      throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN,
        "仅系统管理员可以查看或重置学生密码");
  }

  public record AnnouncementRequest(String text) {}
  public record StudentPasswordRequest(String password) {}
}
