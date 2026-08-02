import { beforeEach, describe, expect, it } from "vitest";
import { auth, hasPermission } from "./auth";

describe("permission checks", () => {
  beforeEach(() => {
    auth.user = null;
    auth.checked = false;
  });

  it("denies anonymous users", () => {
    expect(hasPermission("dashboard.view")).toBe(false);
  });

  it("grants only assigned permissions to normal users", () => {
    auth.user = { username: "member", admin: false, permissions: ["dashboard.view"] };
    expect(hasPermission("dashboard.view")).toBe(true);
    expect(hasPermission("documents.edit")).toBe(false);
  });

  it("grants every permission to administrators", () => {
    auth.user = { username: "admin", admin: true, permissions: [] };
    expect(hasPermission("documents.edit")).toBe(true);
  });
});
