package cn.codedog.model;

import java.time.Instant;
import java.util.List;

public final class RankingRewardPayload {
  private RankingRewardPayload() {}

  public record Announcement(String text, Instant updatedAt) {}
  public record AnnouncementItem(long id, String text, String status, Instant publishAt,
                                 Instant unpublishAt, Instant createdAt, Instant updatedAt) {}
  public record AnnouncementRequest(String text, Instant publishAt, Instant unpublishAt) {}
  public record AnnouncementScheduleRequest(String text, Instant publishAt, Instant unpublishAt) {}
  public record AnnouncementStatusRequest(boolean online) {}
  public record Reward(long id, String name, int requiredPoints, boolean enabled,
                       boolean hasImage, String imageUrl, Instant createdAt, Instant updatedAt) {}
  public record Balance(int earnedPoints, int spentPoints, int availablePoints, int adjustmentPoints) {}
  public record RewardImage(byte[] data, String contentType) {}
  public record Redemption(long id, String studentId, String studentName, Long rewardId,
                           String rewardName, int pointsSpent, int balanceBefore, int balanceAfter, String status,
                           Instant redeemedAt, Instant fulfilledAt, String fulfilledBy) {}
  public record RedemptionRequest(String studentId, long rewardId) {}
  public record FulfillmentRequest(boolean fulfilled) {}
  public record RedemptionSummary(List<Redemption> redemptions) {}
}
