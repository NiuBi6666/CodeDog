<script setup>
import { computed, onMounted, ref } from "vue";
import { ClipboardCheck, FilePlus2, FileText, ScrollText, Search, ShieldCheck, UserRound } from "@lucide/vue";
import AdminLayout from "../components/AdminLayout.vue";
import { api } from "../api";
import { auth, hasPermission } from "../auth";
import { formatDateTime } from "../utils";

const data = ref(null);
const error = ref("");
const quickPermissions = [
  "students.view", "class_progress.view", "documents.view", "documents.create", "logs.view"
];
const hasQuickActions = computed(() =>
  Boolean(auth.user?.admin || quickPermissions.some((permission) => hasPermission(permission))));
const showDocumentStats = computed(() => hasPermission("dashboard.document_stats"));
const showStudentStats = computed(() => hasPermission("dashboard.student_stats"));
const showMetrics = computed(() => showDocumentStats.value || showStudentStats.value);
const showLatestDocument = computed(() => hasPermission("dashboard.latest_document"));

onMounted(async () => {
  try { data.value = await api("/dashboard"); }
  catch (failure) { error.value = failure.message; }
});
</script>

<template>
  <AdminLayout page-title="首页" active-page="dashboard">
    <div class="admin-page-heading"><div><h1>首页</h1><p>内容发布、学生信息查询与操作审计</p></div></div>
    <div v-if="error" class="notice notice-error">{{ error }}</div>
    <template v-if="data">
      <section v-if="showMetrics" class="metric-grid" aria-label="数据概览">
        <article v-if="showDocumentStats" class="metric-item metric-teal"><span class="metric-symbol"><FileText :size="20"/></span><div><strong>{{ data.documentTotal }}</strong><span>全部文档</span></div></article>
        <article v-if="showDocumentStats" class="metric-item metric-green"><span class="metric-symbol">✓</span><div><strong>{{ data.documentNormal }}</strong><span>正常文档</span></div></article>
        <article v-if="showDocumentStats" class="metric-item metric-gray"><span class="metric-symbol">−</span><div><strong>{{ data.documentOffline }}</strong><span>下线文档</span></div></article>
        <article v-if="showStudentStats" class="metric-item metric-blue"><span class="metric-symbol"><UserRound :size="20"/></span><div><strong>{{ data.studentCount }}</strong><span>学生名单</span></div></article>
      </section>
      <div v-if="hasQuickActions || showLatestDocument" class="dashboard-grid" :class="{ 'dashboard-grid-single': !hasQuickActions || !showLatestDocument }">
        <section v-if="hasQuickActions" class="admin-panel">
          <div class="panel-heading"><h2>快捷入口</h2></div>
          <div class="quick-actions">
            <RouterLink v-if="hasPermission('students.view')" to="/student/query"><span><Search :size="18"/></span><div><strong>查询学生</strong><small>按姓名查询 ID，或按 ID 查询姓名</small></div><b>›</b></RouterLink>
            <RouterLink v-if="hasPermission('class_progress.view')" to="/class/progress"><span><ClipboardCheck :size="18"/></span><div><strong>课堂完成情况</strong><small>按班级和课次查看每道题完成情况</small></div><b>›</b></RouterLink>
            <RouterLink v-if="hasPermission('documents.view')" to="/doc/list"><span><FileText :size="18"/></span><div><strong>文档管理</strong><small>搜索、编辑、下线和恢复文档</small></div><b>›</b></RouterLink>
            <RouterLink v-if="hasPermission('documents.create')" to="/doc/create"><span><FilePlus2 :size="18"/></span><div><strong>新建文档</strong><small>创建一篇新的公开文档</small></div><b>›</b></RouterLink>
            <RouterLink v-if="hasPermission('logs.view')" to="/logs"><span><ScrollText :size="18"/></span><div><strong>操作日志</strong><small>查看登录和后台功能的操作记录</small></div><b>›</b></RouterLink>
            <RouterLink v-if="auth.user?.admin" to="/users"><span><ShieldCheck :size="18"/></span><div><strong>用户与权限</strong><small>为注册用户设置页面和按钮权限</small></div><b>›</b></RouterLink>
          </div>
        </section>
        <section v-if="showLatestDocument" class="admin-panel">
          <div class="panel-heading"><h2>当前公开文档</h2></div>
          <div v-if="data.latestDocument" class="latest-document">
            <span class="status-badge status-normal">正常</span><h3>{{ data.latestDocument.title }}</h3>
            <p>更新于 {{ formatDateTime(data.latestDocument.updatedAt) }}</p>
            <div class="button-row"><a class="button button-quiet" :href="`/doc/show/${data.latestDocument.id}`">查看</a><RouterLink v-if="hasPermission('documents.edit')" class="button button-primary" :to="`/doc/edit/${data.latestDocument.id}`">编辑</RouterLink></div>
          </div>
          <div v-else class="panel-empty">暂无正常状态的公开文档</div>
        </section>
      </div>
      <section v-if="!showMetrics && !hasQuickActions && !showLatestDocument" class="admin-panel dashboard-permission-empty"><strong>欢迎使用 CodeDog</strong><span>当前账号暂无其他功能权限</span></section>
    </template>
  </AdminLayout>
</template>
