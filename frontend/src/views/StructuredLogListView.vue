<script setup>
import { computed, onMounted, reactive, ref, watch } from "vue";
import { Copy, Download, X } from "@lucide/vue";
import { useRoute, useRouter } from "vue-router";
import AdminLayout from "../components/AdminLayout.vue";
import { api, notify, writeClipboard } from "../api";
import { auth, hasPermission } from "../auth";
import { actorLabel, buildAuditQuery, formatAuditValue, resultLabel } from "../auditUi";
import { formatDateTime } from "../utils";

const route = useRoute();
const router = useRouter();
const data = ref({ logs: [], total: 0, page: 0, pageCount: 0 });
const detail = ref(null);
const error = ref("");
const busy = ref(false);
const detailBusy = ref(false);
const exporting = ref(false);
const filters = reactive({ startDate: "", endDate: "", ownerUsername: "", module: "", eventType: "",
  actorType: "", actor: "", result: "", statusCode: "", targetType: "", targetId: "", keyword: "", requestId: "" });
const canExport = computed(() => hasPermission("logs.export"));

function syncFilters() {
  for (const key of Object.keys(filters)) filters[key] = route.query[key] || "";
}
async function load() {
  busy.value = true; error.value = "";
  try { data.value = await api(`/logs?${buildAuditQuery(route.query, Number(route.query.page || 0))}`); }
  catch (failure) { error.value = failure.message; }
  finally { busy.value = false; }
}
function search() {
  const query = Object.fromEntries([...buildAuditQuery(filters).entries()]);
  router.push({ path: "/logs", query });
}
async function openDetail(id) {
  detailBusy.value = true; detail.value = null;
  try { detail.value = await api(`/logs/${id}`); }
  catch (failure) { notify(failure.message); }
  finally { detailBusy.value = false; }
}
function closeDetail() { detail.value = null; }
async function copyValue(value) {
  await writeClipboard(formatAuditValue(value)); notify("已复制");
}
async function exportCsv() {
  exporting.value = true;
  try {
    const response = await fetch(`/api/logs/export?${buildAuditQuery(filters)}`, { credentials: "same-origin" });
    if (!response.ok) {
      const payload = await response.json().catch(() => ({}));
      throw new Error(payload.error || "导出失败");
    }
    const url = URL.createObjectURL(await response.blob());
    const link = document.createElement("a"); link.href = url; link.download = "codedog-audit-logs.csv"; link.click();
    URL.revokeObjectURL(url); notify("日志已导出");
  } catch (failure) { notify(failure.message); }
  finally { exporting.value = false; }
}
function targetText(log) { return log.targetId ? `${log.targetType || "目标"} #${log.targetId}` : "-"; }
function durationText(value) { return value === null || value === undefined ? "-" : `${value} ms`; }
function go(page) { router.push({ path: "/logs", query: { ...route.query, page: page || undefined } }); }

watch(() => route.fullPath, () => { syncFilters(); load(); });
onMounted(() => { syncFilters(); load(); });
</script>

<template>
  <AdminLayout page-title="操作日志" active-page="logs">
    <div class="admin-page-heading audit-heading">
      <div><h1>操作日志</h1><p>共 {{ data.total }} 条，每次请求与业务变更可通过 Request ID 关联</p></div>
      <button v-if="canExport" class="button button-quiet" type="button" :disabled="exporting" @click="exportCsv">
        <Download :size="16" aria-hidden="true" />{{ exporting ? "正在导出" : "导出 CSV" }}
      </button>
    </div>
    <div v-if="error" class="notice notice-error">{{ error }}</div>

    <form class="audit-filter" @submit.prevent="search">
      <label><span>开始日期</span><input v-model="filters.startDate" type="date"></label>
      <label><span>结束日期</span><input v-model="filters.endDate" type="date"></label>
      <label v-if="auth.user?.admin"><span>租户</span><input v-model.trim="filters.ownerUsername" placeholder="教师用户名"></label>
      <label><span>模块</span><select v-model="filters.module"><option value="">全部模块</option><option value="auth">登录认证</option><option value="account">账户与权限</option><option value="documents">文档管理</option><option value="students">学生查询</option><option value="classes">课堂完成情况</option><option value="rankings">学生排名</option><option value="exams">成绩管理</option><option value="questionnaire">问卷与作业</option><option value="logs">操作日志</option><option value="system">系统</option></select></label>
      <label><span>事件类型</span><select v-model="filters.eventType"><option value="">全部类型</option><option value="read">读取</option><option value="create">新增/执行</option><option value="update">修改</option><option value="delete">删除</option><option value="export">导出</option></select></label>
      <label><span>主体类型</span><select v-model="filters.actorType"><option value="">全部主体</option><option value="TEACHER">教师</option><option value="STUDENT">学生</option><option value="EXTENSION_DEVICE">扩展设备</option><option value="ANONYMOUS">匿名访问者</option><option value="SYSTEM">系统任务</option></select></label>
      <label><span>结果</span><select v-model="filters.result"><option value="">全部结果</option><option value="success">成功</option><option value="failed">失败</option><option value="rejected">已拒绝</option></select></label>
      <label><span>状态码</span><input v-model.trim="filters.statusCode" inputmode="numeric" placeholder="如 403"></label>
      <label><span>操作者</span><input v-model.trim="filters.actor" placeholder="姓名或 ID"></label>
      <label><span>目标类型</span><input v-model.trim="filters.targetType" placeholder="如 DOCUMENT"></label>
      <label><span>目标 ID</span><input v-model.trim="filters.targetId" placeholder="资源 ID"></label>
      <label class="audit-filter-wide"><span>Request ID</span><input v-model.trim="filters.requestId" placeholder="完整 Request ID"></label>
      <label class="audit-filter-wide"><span>关键词</span><input v-model.trim="filters.keyword" type="search" placeholder="操作、目标、操作者或 IP"></label>
      <div class="audit-filter-actions"><button class="button button-primary" type="submit">查询</button><RouterLink class="button button-quiet" to="/logs">重置</RouterLink></div>
    </form>

    <div class="document-table-wrap audit-table-wrap" :aria-busy="busy">
      <table class="document-table audit-table">
        <thead><tr><th>时间</th><th>操作者 / 租户</th><th>模块 / 操作</th><th>目标</th><th>结果</th><th>状态</th><th>耗时</th><th>IP</th></tr></thead>
        <tbody>
          <tr v-for="log in data.logs" :key="log.id" tabindex="0" @click="openDetail(log.id)" @keydown.enter="openDetail(log.id)">
            <td class="log-time">{{ formatDateTime(log.createdAt) }}</td>
            <td><strong>{{ log.actorName }}</strong><span class="audit-subline">{{ actorLabel(log.actorType) }} · {{ log.ownerUsername || "无租户" }}</span></td>
            <td><strong>{{ log.operation }}</strong><span class="audit-subline">{{ log.moduleLabel }}</span></td>
            <td class="audit-target">{{ targetText(log) }}</td>
            <td><span class="status-badge" :class="`status-${log.result}`">{{ resultLabel(log.result) }}</span></td>
            <td><code>{{ log.statusCode ?? "-" }}</code></td><td class="audit-duration">{{ durationText(log.durationMs) }}</td><td class="log-ip">{{ log.ipAddress }}</td>
          </tr>
          <tr v-if="busy"><td class="empty-table" colspan="8">正在加载日志</td></tr>
          <tr v-else-if="!data.logs.length"><td class="empty-table" colspan="8">没有符合条件的日志</td></tr>
        </tbody>
      </table>
    </div>
    <nav v-if="data.pageCount > 1" class="pagination" aria-label="日志分页">
      <button v-if="data.page > 0" class="button button-quiet" type="button" @click="go(data.page - 1)">上一页</button>
      <span>第 {{ data.page + 1 }} / {{ data.pageCount }} 页</span>
      <button v-if="data.page + 1 < data.pageCount" class="button button-quiet" type="button" @click="go(data.page + 1)">下一页</button>
    </nav>

    <div v-if="detail || detailBusy" class="audit-drawer-backdrop" @click.self="closeDetail">
      <aside class="audit-drawer" aria-label="日志详情">
        <header><div><span class="audit-drawer-eyebrow">AUDIT EVENT</span><h2>{{ detail?.operation || "正在加载" }}</h2></div><button class="icon-button" type="button" title="关闭" @click="closeDetail"><X :size="19" /></button></header>
        <div v-if="detailBusy" class="audit-drawer-loading">正在读取完整日志</div>
        <div v-else-if="detail" class="audit-drawer-body">
          <section><h3>执行结果</h3><dl class="audit-kv"><div><dt>结果</dt><dd><span class="status-badge" :class="`status-${detail.result}`">{{ resultLabel(detail.result) }}</span></dd></div><div><dt>HTTP 状态</dt><dd>{{ detail.statusCode ?? "-" }}</dd></div><div><dt>耗时</dt><dd>{{ durationText(detail.durationMs) }}</dd></div><div><dt>时间</dt><dd>{{ formatDateTime(detail.createdAt) }}</dd></div></dl><p v-if="detail.errorMessage" class="audit-error-message">{{ detail.errorMessage }}</p></section>
          <section><h3>操作者与目标</h3><dl class="audit-kv"><div><dt>操作者</dt><dd>{{ detail.actorName }}（{{ actorLabel(detail.actorType) }}）</dd></div><div><dt>主体 ID</dt><dd>{{ detail.actorId || "-" }}</dd></div><div><dt>租户</dt><dd>{{ detail.ownerUsername || "无租户" }}</dd></div><div><dt>目标</dt><dd>{{ detail.targetId ? `${detail.targetType} #${detail.targetId}` : "-" }}</dd></div></dl></section>
          <section><h3>请求信息</h3><dl class="audit-kv audit-kv-stack"><div><dt>Request ID</dt><dd><code>{{ detail.requestId }}</code><button class="audit-copy" type="button" title="复制 Request ID" @click="copyValue(detail.requestId)"><Copy :size="14" /></button></dd></div><div><dt>请求</dt><dd><code>{{ detail.httpMethod }} {{ detail.requestPath }}</code></dd></div><div><dt>IP</dt><dd>{{ detail.ipAddress }}</dd></div><div><dt>User-Agent</dt><dd class="audit-wrap">{{ detail.userAgent || "-" }}</dd></div></dl></section>
          <section v-for="block in [{ key: 'changes', title: '字段前后值', value: detail.changes }, { key: 'detail', title: '业务明细', value: detail.detail }, { key: 'query', title: '查询条件', value: detail.query }]" :key="block.key" v-show="block.value">
            <div class="audit-section-heading"><h3>{{ block.title }}</h3><button class="audit-copy" type="button" title="复制" @click="copyValue(block.value)"><Copy :size="14" /></button></div><pre>{{ formatAuditValue(block.value) }}</pre>
          </section>
        </div>
      </aside>
    </div>
  </AdminLayout>
</template>
