<script setup>
import { onMounted, ref } from "vue";
import { FilePlus2, FileText, ScrollText, Search, UserRound } from "@lucide/vue";
import AdminLayout from "../components/AdminLayout.vue";
import { api } from "../api";
import { formatDateTime } from "../utils";

const data = ref(null); const error = ref("");
onMounted(async () => { try { data.value = await api("/dashboard"); } catch (failure) { error.value = failure.message; } });
</script>

<template>
  <AdminLayout page-title="首页" active-page="dashboard">
    <div class="admin-page-heading"><div><h1>首页</h1><p>内容发布、学生信息查询与操作审计</p></div></div>
    <div v-if="error" class="notice notice-error">{{ error }}</div>
    <template v-if="data">
      <section class="metric-grid" aria-label="数据概览">
        <article class="metric-item metric-teal"><span class="metric-symbol"><FileText :size="20"/></span><div><strong>{{ data.documentTotal }}</strong><span>全部文档</span></div></article>
        <article class="metric-item metric-green"><span class="metric-symbol">✓</span><div><strong>{{ data.documentNormal }}</strong><span>正常文档</span></div></article>
        <article class="metric-item metric-gray"><span class="metric-symbol">−</span><div><strong>{{ data.documentOffline }}</strong><span>下线文档</span></div></article>
        <article class="metric-item metric-blue"><span class="metric-symbol"><UserRound :size="20"/></span><div><strong>{{ data.studentCount }}</strong><span>学生名单</span></div></article>
      </section>
      <div class="dashboard-grid">
        <section class="admin-panel">
          <div class="panel-heading"><h2>快捷入口</h2></div>
          <div class="quick-actions">
            <RouterLink to="/student/query"><span><Search :size="18"/></span><div><strong>查询学生</strong><small>按姓名查询 ID，或按 ID 查询姓名</small></div><b>›</b></RouterLink>
            <RouterLink to="/doc/list"><span><FileText :size="18"/></span><div><strong>文档管理</strong><small>搜索、编辑、下线和恢复文档</small></div><b>›</b></RouterLink>
            <RouterLink to="/doc/create"><span><FilePlus2 :size="18"/></span><div><strong>新建文档</strong><small>创建一篇新的公开文档</small></div><b>›</b></RouterLink>
            <RouterLink to="/logs"><span><ScrollText :size="18"/></span><div><strong>操作日志</strong><small>查看登录和后台功能的操作记录</small></div><b>›</b></RouterLink>
          </div>
        </section>
        <section class="admin-panel">
          <div class="panel-heading"><h2>当前公开文档</h2></div>
          <div v-if="data.latestDocument" class="latest-document">
            <span class="status-badge status-normal">正常</span><h3>{{ data.latestDocument.title }}</h3>
            <p>更新于 {{ formatDateTime(data.latestDocument.updatedAt) }}</p>
            <div class="button-row"><a class="button button-quiet" :href="`/doc/show/${data.latestDocument.id}`">查看</a><RouterLink class="button button-primary" :to="`/doc/edit/${data.latestDocument.id}`">编辑</RouterLink></div>
          </div>
          <div v-else class="panel-empty">暂无正常状态的公开文档</div>
        </section>
      </div>
    </template>
  </AdminLayout>
</template>
