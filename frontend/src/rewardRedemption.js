export function insufficientRewardPoints(balance, reward) {
  return Number(balance.availablePoints) < Number(reward.requiredPoints);
}

export function rewardRedemptionError(failure) {
  if (failure?.status === 422 && /可用积分不足|当前积分不足/.test(failure.message || "")) {
    return "当前积分不足以兑换此商品";
  }
  return failure?.message || "兑换失败，请刷新后重试";
}
