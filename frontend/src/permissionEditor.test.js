import { readFileSync } from "node:fs";
import { computed, ref } from "vue";
import { describe, expect, it, vi } from "vitest";

function editor(admin = true, assigned = ["users.permissions.manage", "dashboard.view"]) {
  const view = readFileSync(new URL("./views/UserPermissionsView.vue", import.meta.url), "utf8");
  const source = view.split("<script setup>")[1].split("</script>")[0]
    .replace(/^import .*;$/gm, "").replace("onMounted(load);", "");
  const api = vi.fn();
  const auth = { user: { username: "manager", admin, permissions: assigned } };
  const state = new Function("computed", "ref", "api", "jsonBody", "notify", "auth", "hasPermission", source + `
    return { selected, draft, catalog, users, saving, error, openPermissions, closePermissions, selectAllPermissions, savePermissions };
  `)(computed, ref, api, JSON.stringify, vi.fn(), auth, (code) => admin || assigned.includes(code));
  state.catalog.value = [{ key: "home", permissions: [{ code: "dashboard.view", type: "page" }, { code: "dashboard.stats", type: "data" }] }];
  state.users.value = [{ id: 7, username: "teacher", permissions: [] }];
  state.openPermissions(state.users.value[0]);
  return { ...state, api };
}

describe("permission editor", () => {
  it("selects all catalog permissions for a system administrator", () => {
    const state = editor();
    state.selectAllPermissions();
    expect([...state.draft.value]).toEqual(["dashboard.view", "dashboard.stats"]);
    state.selectAllPermissions();
    expect(state.draft.value.size).toBe(2);
  });

  it("selects only permissions the managing teacher may grant", () => {
    const state = editor(false);
    state.selectAllPermissions();
    expect([...state.draft.value]).toEqual(["dashboard.view"]);
  });

  it("updates the list and closes the dialog after a successful save", async () => {
    const state = editor();
    state.selectAllPermissions();
    const updated = { id: 7, username: "teacher", permissions: [...state.draft.value] };
    state.api.mockResolvedValue(updated);
    await state.savePermissions();
    expect(state.api).toHaveBeenCalledWith("/admin/users/7/permissions", { method: "PUT", body: JSON.stringify({ permissions: updated.permissions }) });
    expect(state.users.value[0]).toEqual(updated);
    expect(state.selected.value).toBeNull();
    expect(state.draft.value.size).toBe(0);
    expect(state.saving.value).toBe(false);
  });

  it("keeps the dialog and selection on failed save", async () => {
    const state = editor();
    state.selectAllPermissions();
    state.api.mockRejectedValue(new Error("无权限执行此操作"));
    await state.savePermissions();
    expect(state.selected.value.id).toBe(7);
    expect(state.draft.value.size).toBe(2);
    expect(state.error.value).toBe("无权限执行此操作");
    expect(state.saving.value).toBe(false);
  });

  it("prevents closing or bulk changes while saving", () => {
    const state = editor();
    state.saving.value = true;
    state.closePermissions();
    state.selectAllPermissions();
    expect(state.selected.value.id).toBe(7);
    expect(state.draft.value.size).toBe(0);
  });
});
