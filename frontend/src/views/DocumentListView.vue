<script setup>
import { onMounted, reactive, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { Share2 } from "@lucide/vue";
import AdminLayout from "../components/AdminLayout.vue";
import { api, jsonBody, notify, writeClipboard } from "../api";
import { documentShareUrl, formatDateTime, statusLabel } from "../utils";

const route = useRoute(); const router = useRouter();
const data = ref({ documents: [], total: 0, page: 0, pageCount: 0 }); const error = ref(""); const busy = ref(false);
const filters = reactive({ createdDate: "", updatedDate: "", status: "" });
async function load() {
  busy.value = true; error.value = "";
  const params = new URLSearchParams();
  for (const key of ["createdDate", "updatedDate", "status", "page"]) if (route.query[key]) params.set(key, route.query[key]);
  try { data.value = await api(`/documents?${params}`); } catch (failure) { error.value = failure.message; }
  finally { busy.value = false; }
}
function syncFilters() { filters.createdDate = route.query.createdDate || ""; filters.updatedDate = route.query.updatedDate || ""; filters.status = route.query.status || ""; }
function search() { router.push({ path: "/doc/list", query: { ...filters, page: undefined } }); }
async function changeStatus(document) {
  const next = document.status === "normal" ? "offline" : "normal";
  if (next === "offline" && !window.confirm("确定下线这篇文档？")) return;
  try { await api(`/documents/${document.id}/status`, { method: "PATCH", body: jsonBody({ status: next }) }); notify(next === "normal" ? "文档已上线" : "文档已下线"); await load(); }
  catch (failure) { error.value = failure.message; }
}
async function share(document) {
  try {
    await writeClipboard(documentShareUrl(document.id, window.location.origin));
    notify("查看链接已复制");
  } catch (_failure) {
    notify("复制失败，请稍后重试");
  }
}
watch(() => route.fullPath, () => { syncFilters(); load(); });
onMounted(() => { syncFilters(); load(); });
</script>

<template>
  <AdminLayout page-title="文档管理" active-page="documents">
    <div class="admin-page-heading"><div><h1>文档</h1><p>共 {{ data.total }} 篇</p></div></div>
    <div v-if="error" class="notice notice-error">{{ error }}</div>
    <form class="filter-bar" @submit.prevent="search">
      <label><span>创建时间</span><input v-model="filters.createdDate" type="date"></label>
      <label><span>修改时间</span><input v-model="filters.updatedDate" type="date"></label>
      <label><span>文档状态</span><select v-model="filters.status"><option value="">全部</option><option value="normal">正常</option><option value="offline">下线</option></select></label>
      <div class="filter-actions"><button class="button button-quiet" type="submit">查询</button><RouterLink class="button button-quiet" to="/doc/list">重置</RouterLink><RouterLink class="button button-primary" to="/doc/create">新建</RouterLink></div>
    </form>
    <div class="document-table-wrap"><table class="document-table">
      <thead><tr><th>标题</th><th>状态</th><th>创建时间</th><th>修改时间</th><th class="actions-column">操作</th></tr></thead>
      <tbody>
        <tr v-for="document in data.documents" :key="document.id"><td><RouterLink class="document-title-link" :to="`/doc/edit/${document.id}`">{{ document.title }}</RouterLink><span class="document-id">#{{ document.id }}</span></td><td><span class="status-badge" :class="`status-${document.status}`">{{ statusLabel(document.status) }}</span></td><td>{{ formatDateTime(document.createdAt) }}</td><td>{{ formatDateTime(document.updatedAt) }}</td><td><div class="row-actions"><button class="button button-quiet button-small" type="button" title="复制查看链接" @click="share(document)"><Share2 :size="14"/>分享</button><a class="button button-quiet button-small" :href="`/doc/show/${document.id}`">查看</a><RouterLink class="button button-quiet button-small" :to="`/doc/edit/${document.id}`">编辑</RouterLink><button class="button button-small" :class="document.status === 'normal' ? 'button-danger' : 'button-primary'" type="button" @click="changeStatus(document)">{{ document.status === "normal" ? "下线" : "上线" }}</button></div></td></tr>
        <tr v-if="!busy && !data.documents.length"><td class="empty-table" colspan="5">没有符合条件的文档</td></tr>
      </tbody>
    </table></div>
    <nav v-if="data.pageCount > 1" class="pagination" aria-label="文档分页"><RouterLink v-if="data.page > 0" class="button button-quiet" :to="{ query: { ...route.query, page: data.page - 1 } }">上一页</RouterLink><span>第 {{ data.page + 1 }} / {{ data.pageCount }} 页</span><RouterLink v-if="data.page + 1 < data.pageCount" class="button button-quiet" :to="{ query: { ...route.query, page: data.page + 1 } }">下一页</RouterLink></nav>
  </AdminLayout>
</template>
