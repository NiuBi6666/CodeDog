package cn.codedog.controller;

import cn.codedog.model.RankingRewardPayload;
import cn.codedog.service.RankingRewardService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public/rankings")
public class RankingRewardPublicController {
  private final RankingRewardService rewards;
  public RankingRewardPublicController(RankingRewardService rewards) { this.rewards = rewards; }

  @GetMapping("/announcement")
  public RankingRewardPayload.Announcement announcement() { return rewards.publicAnnouncement(); }
}
