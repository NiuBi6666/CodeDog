import { describe, expect, it, vi } from "vitest";
import { insufficientRewardPoints, rewardRedemptionError } from "./rewardRedemption.js";

describe("reward confirmation feedback", () => {
  it("requires enough points, allowing the exact price", () => {
    expect(insufficientRewardPoints({ availablePoints: 3 }, { requiredPoints: 66 })).toBe(true);
    expect(insufficientRewardPoints({ availablePoints: 66 }, { requiredPoints: 66 })).toBe(false);
    expect(insufficientRewardPoints({ availablePoints: 100 }, { requiredPoints: 66 })).toBe(false);
  });
  it("uses the requested prompt when the server rejects a stale balance", () => {
    expect(rewardRedemptionError({ status: 422, message: "可用积分不足，当前可用 3 分" })).toBe("当前积分不足以兑换此商品");
  });
  it("preserves sold-out, authentication and network errors", () => {
    expect(rewardRedemptionError({ status: 422, message: "该奖品已兑完，请选择其他奖品" })).toBe("该奖品已兑完，请选择其他奖品");
    expect(rewardRedemptionError({ status: 401, message: "学生登录已失效，请重新登录" })).toBe("学生登录已失效，请重新登录");
    expect(rewardRedemptionError({})).toBe("兑换失败，请刷新后重试");
  });
});

describe("student confirmation action", () => {
  async function setup() {
    const { readFileSync } = await import("node:fs");
    const view = readFileSync(new URL("./views/PublicRankingView.vue", import.meta.url), "utf8");
    const source = view.slice(view.indexOf("async function redeemReward()"), view.indexOf("function toggleRowDetails"));
    const state = {
      selectedReward: { value: { id: 9, stock: 1, requiredPoints: 66 } },
      redeemSaving: { value: false }, redeemError: { value: "" },
      balance: { value: { availablePoints: 100 } },
      redemptions: { value: [] }, redeemNotice: { value: "" }, board: { value: {} },
      api: vi.fn(), showAlert: vi.fn().mockResolvedValue(true),
      insufficientRewardPoints, rewardRedemptionError,
      refreshRewardStock: vi.fn().mockResolvedValue(),
      closeRewardRedeem: vi.fn(), handleSessionExpired: vi.fn()
    };
    const action = new Function(...Object.keys(state), source + "; return redeemReward;")(...Object.values(state));
    return { state, action };
  }

  it("refreshes stale displayed points, alerts on insufficient balance and sends no redemption", async () => {
    const { state, action } = await setup();
    state.api.mockResolvedValue({ availablePoints: 3 });
    await action();
    expect(state.api).toHaveBeenCalledTimes(1);
    expect(state.api).toHaveBeenCalledWith("/public/rankings/student-balance");
    expect(state.showAlert).toHaveBeenCalledWith("当前积分不足以兑换此商品");
    expect(state.redeemSaving.value).toBe(false);
  });

  it("alerts if another tab spends the balance between refresh and redemption", async () => {
    const { state, action } = await setup();
    state.api.mockResolvedValueOnce({ availablePoints: 100 })
      .mockRejectedValueOnce({ status: 422, message: "可用积分不足，当前可用 3 分" })
      .mockResolvedValueOnce({ availablePoints: 3 });
    await action();
    expect(state.showAlert).toHaveBeenCalledWith("当前积分不足以兑换此商品");
    expect(state.balance.value.availablePoints).toBe(3);
    expect(state.redeemSaving.value).toBe(false);
    expect(state.closeRewardRedeem).not.toHaveBeenCalled();
  });

  it("ignores a repeated click while redemption is pending", async () => {
    const { state, action } = await setup();
    state.redeemSaving.value = true;
    await action();
    expect(state.api).not.toHaveBeenCalled();
    expect(state.showAlert).not.toHaveBeenCalled();
  });
});
