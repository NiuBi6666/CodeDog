<script setup>
import { computed, onMounted, onBeforeUnmount, nextTick, reactive, ref, watch } from "vue";
import { Link2, Save, Search, ShieldCheck, Unlink, UserPlus, X } from "@lucide/vue";
import AdminLayout from "../components/AdminLayout.vue";
import { api, jsonBody, notify } from "../api";
import { auth, hasPermission } from "../auth";
import { formatDateTime } from "../utils";

const userSearchInput = ref(null);
const createOpen = ref(false);
const createSaving = ref(false);
const createError = ref("");
const createForm = reactive({ username: "", password: "", confirmation: "" });
const canCreateUsers = computed(() => hasPermission("users.create"));
function openCreate() {
  if (!canCreateUsers.value) return;
  Object.assign(createForm, { username: "", password: "", confirmation: "" });
  createError.value = ""; createOpen.value = true;
}
function closeCreate() {
  if (createSaving.value) return;
  createOpen.value = false;
  Object.assign(createForm, { username: "", password: "", confirmation: "" });
  createError.value = "";
}
async function createUser() {
  if (createSaving.value || !canCreateUsers.value) return;
  createError.value = "";
  if (createForm.password !== createForm.confirmation) { createError.value = "两次输入的密码不一致"; return; }
  createSaving.value = true;
  try {
    const user = await api("/admin/users", { method: "POST", body: jsonBody(createForm) });
    createSaving.value = false;
    closeCreate();
    keyword.value = "";
    notify(`用户 ${user.username} 已创建`);
    await load();
  } catch (failure) { createError.value = failure.message; }
  finally { createSaving.value = false; }
}

const users = ref([]);
const catalog = ref([]);
const error = ref("");
const loading = ref(false);
const saving = ref(false);
const keyword = ref("");
const selected = ref(null);
const draft = ref(new Set());
const mappingSelected = ref(null);
const mappingDraft = ref("");
const mappingSaving = ref(false);
const canManagePermissions = computed(() => hasPermission("users.permissions.manage"));
const canManageCrm = computed(() => hasPermission("users.crm.manage"));

const filteredUsers = computed(() => {
  const value = keyword.value.trim().toLowerCase();
  return value ? users.value.filter((user) => [user.username, user.teacherId, user.crmTeacherId]
    .some((item) => String(item || "").toLowerCase().includes(value))) : users.value;
});

async function load() {
  loading.value = true;
  error.value = "";
  try {
    const [userList, permissionGroups] = await Promise.all([
      api("/admin/users"),
      api("/admin/permissions")
    ]);
    users.value = userList;
    catalog.value = permissionGroups;
  } catch (failure) {
    error.value = failure.message;
  } finally {
    loading.value = false;
  }
}

function openPermissions(user) {
  if (user.admin || !canManagePermissions.value || (!auth.user?.admin && user.username === auth.user?.username)) return;
  error.value = "";
  selected.value = user;
  draft.value = new Set(user.permissions);
}

function closePermissions() {
  if (saving.value) return;
  selected.value = null;
  draft.value = new Set();
}

function openMapping(user) {
  if (!canManageCrm.value || (user.admin && !auth.user?.admin)) return;
  mappingSelected.value = user;
  mappingDraft.value = user.crmTeacherId || "";
}

function closeMapping() {
  if (mappingSaving.value) return;
  mappingSelected.value = null;
  mappingDraft.value = "";
}

function toggle(group, permission) {
  if (!canChangePermission(permission)) return;
  const next = new Set(draft.value);
  const page = group.permissions.find((item) => item.type === "page");
  if (next.has(permission.code)) {
    next.delete(permission.code);
    if (permission.type === "page") group.permissions.forEach((item) => next.delete(item.code));
  } else {
    next.add(permission.code);
    if (permission.type !== "page" && page) next.add(page.code);
  }
  draft.value = next;
}

function groupSelected(group) {
  return group.permissions.every((permission) => draft.value.has(permission.code));
}
function canChangePermission(permission) {
  return Boolean(auth.user?.admin || (selected.value?.username !== auth.user?.username && hasPermission(permission.code)));
}
function editablePermissions(group) {
  return group.permissions.filter(canChangePermission);
}

function toggleGroup(group) {
  const next = new Set(draft.value);
  const editable = editablePermissions(group);
  const enable = !editable.every((permission) => next.has(permission.code));
  editable.forEach((permission) => enable ? next.add(permission.code) : next.delete(permission.code));
  draft.value = next;
}

const allEditablePermissions = computed(() => catalog.value.flatMap(editablePermissions));
const allPermissionsSelected = computed(() => allEditablePermissions.value.length > 0 &&
  allEditablePermissions.value.every((permission) => draft.value.has(permission.code)));

function selectAllPermissions() {
  if (saving.value || !canManagePermissions.value) return;
  const next = new Set(draft.value);
  const enable = !allPermissionsSelected.value;
  allEditablePermissions.value.forEach((permission) => enable ? next.add(permission.code) : next.delete(permission.code));
  draft.value = next;
}

function replaceUser(updated) {
  users.value = users.value.map((user) => user.id === updated.id ? updated : user);
}

async function savePermissions() {
  if (!selected.value) return;
  saving.value = true;
  error.value = "";
  try {
    const updated = await api(`/admin/users/${selected.value.id}/permissions`, {
      method: "PUT",
      body: jsonBody({ permissions: [...draft.value] })
    });
    replaceUser(updated);
    notify("用户权限已更新");
    saving.value = false;
    closePermissions();
  } catch (failure) {
    error.value = failure.message;
  } finally {
    saving.value = false;
  }
}

async function saveMapping(clear = false) {
  if (!mappingSelected.value) return;
  mappingSaving.value = true;
  error.value = "";
  try {
    const value = clear ? null : mappingDraft.value.trim() || null;
    const updated = await api(`/admin/users/${mappingSelected.value.id}/crm-teacher`, {
      method: "PUT",
      body: jsonBody({ crmTeacherId: value })
    });
    replaceUser(updated);
    notify(value ? "CRM 教师已绑定" : "CRM 教师绑定已清除");
    mappingSaving.value = false;
    closeMapping();
  } catch (failure) {
    error.value = failure.message;
  } finally {
    mappingSaving.value = false;
  }
}

let previousFocus;
let previousOverflow;
function modalKeydown(event) {
  if (!selected.value && !mappingSelected.value && !createOpen.value) return;
  if (event.key === "Escape") {
    event.preventDefault();
    if (createOpen.value) closeCreate(); else if (mappingSelected.value) closeMapping(); else closePermissions();
  } else if (event.key === "Tab") {
    const dialog = document.querySelector(".permission-dialog[role=dialog]");
    const nodes = [...(dialog?.querySelectorAll('button:not(:disabled), input:not(:disabled), select:not(:disabled), textarea:not(:disabled), a[href], [tabindex="0"]') || [])]
      .filter(node => node.getClientRects().length);
    if (!nodes.length) { event.preventDefault(); dialog?.focus(); return; }
    const first = nodes[0], last = nodes[nodes.length - 1];
    if (!dialog.contains(document.activeElement) || (event.shiftKey && document.activeElement === first)) {
      event.preventDefault(); (event.shiftKey ? last : first).focus();
    } else if (!event.shiftKey && document.activeElement === last) {
      event.preventDefault(); first.focus();
    }
  }
}
watch(() => Boolean(selected.value || mappingSelected.value || createOpen.value), async (open) => {
  if (open) {
    previousFocus = document.activeElement;
    previousOverflow = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    document.addEventListener("keydown", modalKeydown, true);
    await nextTick();
    document.querySelector('.permission-dialog button[aria-label="关闭"]')?.focus();
  } else {
    document.removeEventListener("keydown", modalKeydown, true);
    document.body.style.overflow = previousOverflow || "";
    previousFocus?.focus();
  }
});
onBeforeUnmount(() => {
  document.removeEventListener("keydown", modalKeydown, true);
  if (previousOverflow !== undefined) document.body.style.overflow = previousOverflow;
});
onMounted(load);
</script>

<template>
  <AdminLayout page-title="用户与权限" active-page="users">
    <div class="admin-page-heading">
      <div><h1>用户与权限</h1><p>共 {{ users.length }} 个账号</p></div>
      <button v-if="canCreateUsers" class="button button-primary" type="button" @click="openCreate"><UserPlus :size="16"/>新建用户</button>
    </div>
    <div v-if="error" class="notice notice-error">{{ error }}</div>
    <div class="permission-user-toolbar">
      <div class="permission-user-search" role="search">
        <Search class="user-search-icon" :size="17" aria-hidden="true"/>
        <input ref="userSearchInput" v-model.trim="keyword" type="search" aria-label="搜索用户名或教师 ID" placeholder="搜索用户名或教师 ID">
        <button v-if="keyword" class="user-search-clear" type="button" aria-label="清除搜索" title="清除搜索" @click="keyword = ''; userSearchInput?.focus()"><X :size="15" aria-hidden="true"/></button>
      </div>
    </div>
    <div class="document-table-wrap" :aria-busy="loading">
      <table class="document-table permission-user-table">
        <thead><tr><th>用户</th><th>账号类型</th><th>CRM 教师 ID</th><th>已授权</th><th>最近修改</th><th class="actions-column">操作</th></tr></thead>
        <tbody>
          <tr v-for="user in filteredUsers" :key="user.id">
            <td><strong>{{ user.displayName || user.username }}</strong><span class="teacher-public-id">{{ user.teacherId }}</span></td>
            <td><span class="status-badge" :class="user.admin ? 'status-normal' : 'status-warning'">{{ user.admin ? "系统管理员" : "普通用户" }}</span></td>
            <td><span v-if="user.crmTeacherId" class="mapping-value">{{ user.crmTeacherId }}</span><span v-else class="permission-locked">未绑定</span></td>
            <td>{{ user.admin ? "全部权限" : `${user.permissions.length} 项` }}</td>
            <td>{{ formatDateTime(user.updatedAt) }}</td>
            <td>
              <div class="button-row permission-actions">
                <button v-if="canManageCrm && (auth.user?.admin || !user.admin)" class="button button-quiet button-small" type="button" @click="openMapping(user)"><Link2 :size="14"/>绑定 CRM</button>
                <button v-if="canManagePermissions && !user.admin && (auth.user?.admin || user.username !== auth.user?.username)" class="button button-quiet button-small" type="button" @click="openPermissions(user)"><ShieldCheck :size="14"/>设置权限</button>
              </div>
            </td>
          </tr>
          <tr v-if="loading"><td class="empty-table" colspan="6">正在加载用户</td></tr>
          <tr v-else-if="!filteredUsers.length"><td class="empty-table" colspan="6">没有符合条件的用户</td></tr>
        </tbody>
      </table>
    </div>

    <Teleport to="body">
      <div v-if="createOpen" class="permission-dialog-backdrop ui-dialog-backdrop" role="presentation" @click.self="closeCreate">
        <section class="permission-dialog ui-modal mapping-dialog" role="dialog" aria-modal="true" aria-labelledby="create-user-title">
          <header><div><h2 id="create-user-title">新建用户</h2><p>新账号默认仅可访问后台首页</p></div><button class="icon-button" type="button" aria-label="关闭" :disabled="createSaving" @click="closeCreate"><X :size="18"/></button></header>
          <form id="create-user-form" class="mapping-form create-user-form" @submit.prevent="createUser">
            <div v-if="createError" class="notice notice-error" role="alert">{{ createError }}</div>
            <label>用户名<input v-model.trim="createForm.username" autocomplete="off" minlength="3" maxlength="32" pattern="[A-Za-z0-9_.-]+" placeholder="3-32 位字母、数字或 _ . -" required></label>
            <label>密码<input v-model="createForm.password" type="password" autocomplete="new-password" minlength="8" maxlength="72" placeholder="请输入 8-72 位密码" required></label>
            <label>确认密码<input v-model="createForm.confirmation" type="password" autocomplete="new-password" minlength="8" maxlength="72" placeholder="请再次输入密码" required></label>
          </form>
          <footer><span></span><div class="button-row"><button class="button button-quiet" type="button" :disabled="createSaving" @click="closeCreate">取消</button><button class="button button-primary" type="submit" form="create-user-form" :disabled="createSaving"><UserPlus :size="16"/>{{ createSaving ? "创建中" : "创建账号" }}</button></div></footer>
        </section>
      </div>
    </Teleport>

    <Teleport to="body">
      <div v-if="mappingSelected" class="permission-dialog-backdrop ui-dialog-backdrop" role="presentation">
        <section class="permission-dialog ui-modal mapping-dialog" role="dialog" aria-modal="true" aria-labelledby="mapping-dialog-title">
          <header>
            <div><h2 id="mapping-dialog-title">绑定 {{ mappingSelected.displayName || mappingSelected.username }}</h2><p>{{ mappingSelected.teacherId }}</p></div>
            <button class="icon-button" type="button" title="关闭" aria-label="关闭" :disabled="mappingSaving" @click="closeMapping"><X :size="18"/></button>
          </header>
          <form class="mapping-form" @submit.prevent="saveMapping(false)">
            <label>CRM 教师 ID<input v-model.trim="mappingDraft" maxlength="100" autocomplete="off" placeholder="例如 29413" autofocus></label>
          </form>
          <footer>
            <button v-if="mappingSelected.crmTeacherId" class="button button-quiet" type="button" :disabled="mappingSaving" @click="saveMapping(true)"><Unlink :size="15"/>清除绑定</button>
            <span v-else></span>
            <div class="button-row"><button class="button button-quiet" type="button" :disabled="mappingSaving" @click="closeMapping">取消</button><button class="button button-primary" type="button" :disabled="mappingSaving || !mappingDraft.trim()" @click="saveMapping(false)"><Save :size="15"/>{{ mappingSaving ? "保存中" : "保存绑定" }}</button></div>
          </footer>
        </section>
      </div>
    </Teleport>

    <Teleport to="body">
      <div v-if="selected" class="permission-dialog-backdrop ui-dialog-backdrop" role="presentation">
        <section class="permission-dialog ui-modal" role="dialog" aria-modal="true" aria-labelledby="permission-dialog-title">
          <header>
            <div><h2 id="permission-dialog-title">设置 {{ selected.displayName || selected.username }} 的权限</h2><p>页面、数据和操作权限分别控制可见内容及后端接口。</p></div>
            <button class="icon-button" type="button" title="关闭" aria-label="关闭" :disabled="saving" @click="closePermissions"><X :size="18"/></button>
          </header>
          <div class="permission-groups">
            <div class="permission-bulk-actions"><span>一键全选或取消所有可授予的权限</span><button class="button button-quiet button-small" type="button" :disabled="saving || !canManagePermissions || !allEditablePermissions.length" :aria-pressed="allPermissionsSelected" @click="selectAllPermissions">{{ allPermissionsSelected ? "全部取消" : "全部选择" }}</button></div>
            <div v-if="error" class="notice notice-error" role="alert">{{ error }}</div>
            <section v-for="group in catalog" :key="group.key" class="permission-group">
              <label class="permission-group-title"><input type="checkbox" :checked="groupSelected(group)" :disabled="!editablePermissions(group).length" @change="toggleGroup(group)"><strong>{{ group.label }}</strong><span>全选</span></label>
              <div class="permission-options">
                <label v-for="permission in group.permissions" :key="permission.code">
                  <input type="checkbox" :checked="draft.has(permission.code)" :disabled="!canChangePermission(permission)" @change="toggle(group, permission)">
                  <span><strong>{{ permission.label }}</strong><small :class="`permission-kind-${permission.type}`">{{ permission.type === "page" ? "页面" : permission.type === "data" ? "数据" : "按钮" }}</small></span>
                </label>
              </div>
            </section>
          </div>
          <footer><span>已选择 {{ draft.size }} 项权限</span><div class="button-row"><button class="button button-quiet" type="button" :disabled="saving" @click="closePermissions">取消</button><button class="button button-primary" type="button" :disabled="saving" @click="savePermissions"><Save :size="15"/>{{ saving ? "保存中" : "保存权限" }}</button></div></footer>
        </section>
      </div>
    </Teleport>
  </AdminLayout>
</template>

<style scoped>
.permission-user-search { display: flex; align-items: center; gap: 9px; width: min(100%, 320px); height: 40px; box-sizing: border-box; padding: 0 10px 0 12px; border: 1px solid var(--ui-border); border-radius: 8px; background: var(--ui-surface); transition: border-color .15s ease, box-shadow .15s ease; }
.permission-user-search:hover { border-color: #a8bfbd; }
.permission-user-search:focus-within { border-color: var(--ui-primary); box-shadow: 0 0 0 3px rgba(20, 125, 118, .1); }
.user-search-icon { flex: 0 0 auto; color: var(--ui-muted); }
.permission-user-search:focus-within .user-search-icon { color: var(--ui-primary); }
.permission-user-search input { flex: 1; width: 0; min-width: 0; height: 100%; margin: 0; padding: 0; border: 0; border-radius: 0; background: transparent; color: var(--ui-text); font: inherit; font-size: 14px; appearance: none; }
.permission-user-search input:focus, .permission-user-search input:focus-visible { outline: none; box-shadow: none; }
.permission-user-search input::placeholder { color: #86979c; }
.permission-user-search input::-webkit-search-cancel-button, .permission-user-search input::-webkit-search-decoration { appearance: none; }
.user-search-clear { display: grid; place-items: center; flex: 0 0 28px; width: 28px; height: 28px; padding: 0; border: 0; border-radius: 6px; background: transparent; color: var(--ui-muted); cursor: pointer; }
.user-search-clear:hover { color: var(--ui-primary); background: #edf6f4; }
.user-search-clear:focus-visible { outline-offset: 0; }
@media (prefers-reduced-motion: reduce) { .permission-user-search { transition: none; } }

.create-user-form { display: grid; gap: 16px; }
.permission-bulk-actions { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin-bottom: 16px; }
.permission-bulk-actions > span { color: #637984; font-size: 12px; }
</style>
