import { readFileSync } from "node:fs";
import { computed, reactive, ref } from "vue";
import { describe, expect, it, vi } from "vitest";

function editor(admin = true, assigned = ["users.permissions.manage", "dashboard.view"]) {
  const view = readFileSync(new URL("./views/UserPermissionsView.vue", import.meta.url), "utf8");
  const source = view.split("<script setup>")[1].split("</script>")[0]
    .replace(/^import .*;$/gm, "").replace("onMounted(load);", "");
  const api = vi.fn();
  const auth = { user: { username: "manager", admin, permissions: assigned } };
  const state = new Function("computed", "reactive", "ref", "api", "jsonBody", "notify", "auth", "hasPermission", "watch", "onBeforeUnmount", "nextTick", source + `
    return { selected, draft, catalog, users, saving, error, openPermissions, closePermissions, selectAllPermissions, savePermissions, allPermissionsSelected, toggle, toggleGroup, createForm, createOpen, createSaving, createError, openCreate, closeCreate, createUser, keyword };
  `)(computed, reactive, ref, api, JSON.stringify, vi.fn(), auth, (code) => admin || assigned.includes(code), vi.fn(), vi.fn(), async () => {});
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
    expect(state.allPermissionsSelected.value).toBe(true);
    state.selectAllPermissions();
    expect(state.draft.value.size).toBe(0);
    expect(state.allPermissionsSelected.value).toBe(false);
  });

  it("selects only permissions the managing teacher may grant", () => {
    const state = editor(false);
    state.selectAllPermissions();
    expect([...state.draft.value]).toEqual(["dashboard.view"]);
  });

  it("tracks individual and group changes when deciding between select and clear", () => {
    const state = editor();
    const group = state.catalog.value[0];
    state.toggle(group, group.permissions[1]);
    expect(state.allPermissionsSelected.value).toBe(true);
    state.toggle(group, group.permissions[1]);
    expect(state.allPermissionsSelected.value).toBe(false);
    state.selectAllPermissions();
    expect(state.draft.value.size).toBe(2);
    state.toggleGroup(group);
    expect(state.allPermissionsSelected.value).toBe(false);
    expect(state.draft.value.size).toBe(0);
  });

  it("clears only editable permissions and preserves locked grants", () => {
    const state = editor(false);
    state.draft.value = new Set(["dashboard.stats"]);
    state.selectAllPermissions();
    expect(state.allPermissionsSelected.value).toBe(true);
    state.selectAllPermissions();
    expect([...state.draft.value]).toEqual(["dashboard.stats"]);
    expect(state.allPermissionsSelected.value).toBe(false);
  });

  it("does not treat an empty catalog as fully selected", () => {
    const state = editor();
    state.catalog.value = [];
    expect(state.allPermissionsSelected.value).toBe(false);
    state.selectAllPermissions();
    expect(state.draft.value.size).toBe(0);
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

describe("managed user creation",()=>{
  it("does not open without creation permission",()=>{
    const s=editor(false);s.openCreate();expect(s.createOpen.value).toBe(false);
  });
  it("rejects confirmation mismatch without making a request",async()=>{
    const s=editor();s.openCreate();s.createForm.password="password123";s.createForm.confirmation="different";
    await s.createUser();expect(s.api).not.toHaveBeenCalled();expect(s.createError.value).toContain("不一致");expect(s.createOpen.value).toBe(true);
  });
  it("creates, clears credentials, closes and refreshes the list",async()=>{
    const s=editor();s.openCreate();Object.assign(s.createForm,{username:"newteacher",password:"password123",confirmation:"password123"});
    s.keyword.value="old-search";s.api.mockResolvedValueOnce({username:"newteacher"}).mockResolvedValueOnce([{username:"newteacher"}]).mockResolvedValueOnce([]);
    await s.createUser();expect(s.api).toHaveBeenCalledWith("/admin/users",{method:"POST",body:JSON.stringify({username:"newteacher",password:"password123",confirmation:"password123"})});
    expect(s.createOpen.value).toBe(false);expect(s.createForm.password).toBe("");expect(s.keyword.value).toBe("");expect(s.users.value[0].username).toBe("newteacher");
  });
  it("keeps the dialog and useful error on duplicate username",async()=>{
    const s=editor();s.openCreate();s.createForm.password=s.createForm.confirmation="password123";s.api.mockRejectedValue(new Error("用户名已存在"));
    await s.createUser();expect(s.createOpen.value).toBe(true);expect(s.createError.value).toBe("用户名已存在");expect(s.createSaving.value).toBe(false);
  });
  it("prevents duplicate clicks and closing while creating",async()=>{
    const s=editor();s.openCreate();s.createForm.password=s.createForm.confirmation="password123";
    let resolve;s.api.mockImplementationOnce(()=>new Promise(r=>resolve=r)).mockResolvedValue([]);
    const pending=s.createUser();await s.createUser();s.closeCreate();expect(s.api).toHaveBeenCalledTimes(1);expect(s.createOpen.value).toBe(true);
    resolve({username:"newteacher"});await pending;
  });
});
