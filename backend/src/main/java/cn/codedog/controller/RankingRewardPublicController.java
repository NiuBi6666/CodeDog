package cn.codedog.controller;

import cn.codedog.model.RankingRewardPayload;
import cn.codedog.service.RankingRewardService;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/public/rankings")
public class RankingRewardPublicController {
  private final RankingRewardService rewards;
  public RankingRewardPublicController(RankingRewardService rewards) { this.rewards = rewards; }

  @GetMapping("/announcement")
  public RankingRewardPayload.Announcement announcement() { return rewards.publicAnnouncement(); }

  @GetMapping("/rewards")
  public List<RankingRewardPayload.Reward> rewards() { return rewards.publicRewards(); }

  @GetMapping("/rewards/{id}/image")
  public ResponseEntity<byte[]> rewardImage(@PathVariable long id) {
    var image = rewards.publicRewardImage(id);
    return ResponseEntity.ok()
      .cacheControl(CacheControl.noCache())
      .contentType(MediaType.parseMediaType(image.contentType()))
      .body(image.data());
  }
}
