<script setup>
import { onMounted, reactive, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import AdminLayout from "../components/AdminLayout.vue";
import { api } from "../api";
import { formatDateTime } from "../utils";

const route = useRoute();
const router = useRouter();
const data = ref({ logs: [], total: 0, page: 0, pageCount: 0 });
const error = ref("");
const busy = ref(false);
const filters = reactive({ startDate: "", endDate: "", module: "", result: "", keyword: "" });

function syncFilters() {
  for (const key of Object.keys(filters)) filters[key] = route.query[key] || "";
}

async function load() {
  busy.value = true;
  error.value = "";
  const params = new URLSearchParams();
  for (const key of ["startDate", "endDate", "module", "result", "keyword", "page"])
    if (route.query[key]) params.set(key, route.query[key]);
  try { data.value = await api(`/logs?${params}`); }
  catch (failure) { error.value = failure.message; }
  finally { busy.value = false; }
}

function search() {
  const query = {};
  for (const [key, value] of Object.entries(filters)) if (value) query[key] = value;
  router.push({ path: "/logs", query });
}

watch(() => route.fullPath, () => { syncFilters(); load(); });
onMounted(() => { syncFilters(); load(); });
</script>

<template>
  <AdminLayout page-title="操作日志" active-page="logs">
    <div class="admin-page-heading"><div><h1>操作日志</h1><p>共 {{ data.total }} 条，日志仅供查询</p></div></div>
    <div v-if="error" class="notice notice-error">{{ error }}</div>
    <form class="filter-bar log-filter-bar" @submit.prevent="search">
      <label><span>开始日期</span><input v-model="filters.startDate" type="date"></label>
      <label><span>结束日期</span><input v-model="filters.endDate" type="date"></label>
      <label><span>功能模块</span><select v-model="filters.module"><option value="">全部</option><option value="auth">登录认证</option><option value="account">账户安全</option><option value="documents">文档管理</option><option value="students">学生查询</option></select></label>
      <label><span>执行结果</span><select v-model="filters.result"><option value="">全部</option><option value="success">成功</option><option value="failed">失败</option></select></label>
      <label class="log-filter-keyword"><span>关键词</span><input v-model.trim="filters.keyword" type="search" placeholder="文档 ID 或 IP 地址"></label>
      <div class="filter-actions"><button class="button button-quiet" type="submit">查询</button><RouterLink class="button button-quiet" to="/logs">重置</RouterLink></div>
    </form>
    <div class="document-table-wrap" :aria-busy="busy">
      <table class="document-table log-table">
        <thead><tr><th>操作时间</th><th>功能模块</th><th>操作内容</th><th>执行结果</th><th>IP 地址</th></tr></thead>
        <tbody>
          <tr v-for="log in data.logs" :key="log.id">
            <td class="log-time">{{ formatDateTime(log.createdAt) }}</td>
            <td><span class="log-module">{{ log.moduleLabel }}</span></td>
            <td><strong class="log-operation">{{ log.operation }}</strong><span v-if="log.detail" class="log-detail">{{ log.detail }}</span></td>
            <td><span class="status-badge" :class="`status-${log.result}`">{{ log.result === "success" ? "成功" : "失败" }}</span></td>
            <td class="log-ip">{{ log.ipAddress }}</td>
          </tr>
          <tr v-if="busy"><td class="empty-table" colspan="5">正在加载日志</td></tr>
          <tr v-else-if="!data.logs.length"><td class="empty-table" colspan="5">没有符合条件的日志</td></tr>
        </tbody>
      </table>
    </div>
    <nav v-if="data.pageCount > 1" class="pagination" aria-label="日志分页">
      <RouterLink v-if="data.page > 0" class="button button-quiet" :to="{ query: { ...route.query, page: data.page - 1 } }">上一页</RouterLink>
      <span>第 {{ data.page + 1 }} / {{ data.pageCount }} 页</span>
      <RouterLink v-if="data.page + 1 < data.pageCount" class="button button-quiet" :to="{ query: { ...route.query, page: data.page + 1 } }">下一页</RouterLink>
    </nav>
  </AdminLayout>
</template>
