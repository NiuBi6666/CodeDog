import { readFileSync } from "node:fs";
import { describe, expect, it } from "vitest";

describe("ranking password controls", () => {
  it("renders password controls only for system administrators", () => {
    const view = readFileSync(new URL("./views/RankingManagementView.vue", import.meta.url), "utf8");
    expect(view).toContain('v-if="auth.user?.admin">密码');
    expect(view).toContain('v-if="auth.user?.admin"><button');
    expect(view).toContain("仅系统管理员可查看");
  });

  it("shows the configured administrator display name when available", () => {
    const layout = readFileSync(new URL("./components/AdminLayout.vue", import.meta.url), "utf8");
    expect(layout).toContain("auth.user?.displayName || auth.user?.username");
  });
});
