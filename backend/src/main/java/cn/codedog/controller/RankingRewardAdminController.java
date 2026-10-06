package cn.codedog.controller;

import cn.codedog.model.RankingPayload;
import cn.codedog.model.RankingRewardPayload;
import cn.codedog.service.AuditService;
import cn.codedog.service.RankingBoardService;
import cn.codedog.service.RankingRewardService;
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

  public RankingRewardAdminController(RankingBoardService rankings, RankingRewardService rewards, AuditService audit) {
    this.rankings = rankings; this.rewards = rewards; this.audit = audit;
  }

  @GetMapping("/board")
  public RankingPayload.Board board(Principal principal) { return rankings.allBoardForOwner(principal.getName()); }

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

  public record AnnouncementRequest(String text) {}
}
