<script setup>
import { computed, onMounted, ref } from "vue";
import { Link2, Save, Search, ShieldCheck, Unlink, X } from "@lucide/vue";
import AdminLayout from "../components/AdminLayout.vue";
import { api, jsonBody, notify } from "../api";
import { formatDateTime } from "../utils";

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
  if (user.admin) return;
  selected.value = user;
  draft.value = new Set(user.permissions);
}

function closePermissions() {
  if (saving.value) return;
  selected.value = null;
  draft.value = new Set();
}

function openMapping(user) {
  mappingSelected.value = user;
  mappingDraft.value = user.crmTeacherId || "";
}

function closeMapping() {
  if (mappingSaving.value) return;
  mappingSelected.value = null;
  mappingDraft.value = "";
}

function toggle(group, permission) {
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

function toggleGroup(group) {
  const next = new Set(draft.value);
  const enable = !groupSelected(group);
  group.permissions.forEach((permission) => enable ? next.add(permission.code) : next.delete(permission.code));
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
    closeMapping();
  } catch (failure) {
    error.value = failure.message;
  } finally {
    mappingSaving.value = false;
  }
}

onMounted(load);
</script>

<template>
  <AdminLayout page-title="用户与权限" active-page="users">
    <div class="admin-page-heading">
      <div><h1>用户与权限</h1><p>共 {{ users.length }} 个账号</p></div>
    </div>
    <div v-if="error" class="notice notice-error">{{ error }}</div>
    <div class="permission-user-toolbar">
      <label><Search :size="16"/><input v-model.trim="keyword" type="search" placeholder="搜索用户名或教师 ID"></label>
    </div>
    <div class="document-table-wrap" :aria-busy="loading">
      <table class="document-table permission-user-table">
        <thead><tr><th>用户</th><th>账号类型</th><th>CRM 教师 ID</th><th>已授权</th><th>最近修改</th><th class="actions-column">操作</th></tr></thead>
        <tbody>
          <tr v-for="user in filteredUsers" :key="user.id">
            <td><strong>{{ user.username }}</strong><span class="teacher-public-id">{{ user.teacherId }}</span></td>
            <td><span class="status-badge" :class="user.admin ? 'status-normal' : 'status-warning'">{{ user.admin ? "系统管理员" : "普通用户" }}</span></td>
            <td><span v-if="user.crmTeacherId" class="mapping-value">{{ user.crmTeacherId }}</span><span v-else class="permission-locked">未绑定</span></td>
            <td>{{ user.admin ? "全部权限" : `${user.permissions.length} 项` }}</td>
            <td>{{ formatDateTime(user.updatedAt) }}</td>
            <td>
              <div class="button-row permission-actions">
                <button class="button button-quiet button-small" type="button" @click="openMapping(user)"><Link2 :size="14"/>绑定 CRM</button>
                <button v-if="!user.admin" class="button button-quiet button-small" type="button" @click="openPermissions(user)"><ShieldCheck :size="14"/>设置权限</button>
              </div>
            </td>
          </tr>
          <tr v-if="loading"><td class="empty-table" colspan="6">正在加载用户</td></tr>
          <tr v-else-if="!filteredUsers.length"><td class="empty-table" colspan="6">没有符合条件的用户</td></tr>
        </tbody>
      </table>
    </div>

    <Teleport to="body">
      <div v-if="mappingSelected" class="permission-dialog-backdrop" role="presentation">
        <section class="permission-dialog mapping-dialog" role="dialog" aria-modal="true" aria-labelledby="mapping-dialog-title">
          <header>
            <div><h2 id="mapping-dialog-title">绑定 {{ mappingSelected.username }}</h2><p>{{ mappingSelected.teacherId }}</p></div>
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
      <div v-if="selected" class="permission-dialog-backdrop" role="presentation">
        <section class="permission-dialog" role="dialog" aria-modal="true" aria-labelledby="permission-dialog-title">
          <header>
            <div><h2 id="permission-dialog-title">设置 {{ selected.username }} 的权限</h2><p>页面、数据和操作权限分别控制可见内容及后端接口。</p></div>
            <button class="icon-button" type="button" title="关闭" aria-label="关闭" :disabled="saving" @click="closePermissions"><X :size="18"/></button>
          </header>
          <div class="permission-groups">
            <section v-for="group in catalog" :key="group.key" class="permission-group">
              <label class="permission-group-title"><input type="checkbox" :checked="groupSelected(group)" @change="toggleGroup(group)"><strong>{{ group.label }}</strong><span>全选</span></label>
              <div class="permission-options">
                <label v-for="permission in group.permissions" :key="permission.code">
                  <input type="checkbox" :checked="draft.has(permission.code)" @change="toggle(group, permission)">
                  <span><strong>{{ permission.label }}</strong><small>{{ permission.type === "page" ? "页面" : permission.type === "data" ? "数据" : "按钮" }}</small></span>
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
