import { beforeEach, describe, expect, it, vi } from "vitest";
describe("shared themed dialogs", () => {
  beforeEach(() => vi.resetModules());
  it("confirmation waits for acceptance, without invoking an operation itself", async () => {
    const { showConfirm, activeDialog, settleDialog } = await import("./uiDialogs.js");
    const result = showConfirm("删除奖品？", { danger: true, confirmText: "确认删除" });
    expect(activeDialog.value).toMatchObject({ confirm: true, danger: true, confirmText: "确认删除" });
    settleDialog(false);
    expect(await result).toBe(false);
    expect(activeDialog.value).toBeNull();
  });
  it("queues alerts without losing or duplicating the next dialog", async () => {
    const { showAlert, activeDialog, settleDialog } = await import("./uiDialogs.js");
    const first=showAlert("当前积分不足以兑换此商品");
    const second=showAlert("第二条提示");
    expect(activeDialog.value.message).toBe("当前积分不足以兑换此商品");
    settleDialog(true);
    expect(await first).toBe(true);
    expect(activeDialog.value.message).toBe("第二条提示");
    settleDialog(true);
    expect(await second).toBe(true);
    expect(activeDialog.value).toBeNull();
  });
});
