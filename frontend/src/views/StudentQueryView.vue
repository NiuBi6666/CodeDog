<script setup>
import { computed, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import AdminLayout from "../components/AdminLayout.vue";
import { api, jsonBody, notify, writeClipboard } from "../api";
import { splitQueryValues } from "../utils";

const route = useRoute(); const router = useRouter();
const mode = computed(() => route.query.mode === "id" ? "id" : "name");
const rawValues = ref(""); const result = ref(null); const error = ref(""); const busy = ref(false);
watch(mode, () => { rawValues.value = ""; result.value = null; error.value = ""; });
function setMode(value) { router.push({ path: "/student/query", query: { mode: value } }); }
async function query() {
  error.value = ""; result.value = null;
  const values = splitQueryValues(rawValues.value);
  if (!values.length) { error.value = `请至少输入一个学生${mode.value === 'id' ? 'ID' : '姓名'}`; return; }
  if (values.length > 500) { error.value = "单次最多查询 500 条记录"; return; }
  busy.value = true;
  try { result.value = await api("/students/query", { method: "POST", body: jsonBody({ mode: mode.value, values }) }); }
  catch (failure) { error.value = failure.message; }
  finally { busy.value = false; }
}
function resultRows() {
  if (!result.value) return [];
  return result.value.results.flatMap((item) => mode.value === "id"
    ? [{ input: item.inputId, student: item.student, state: item.student ? "已找到" : "未找到" }]
    : item.matches.length
      ? item.matches.map((student) => ({ input: item.inputName, student, state: item.matches.length > 1 ? "重名候选" : "已找到" }))
      : [{ input: item.inputName, student: null, state: "未找到" }]);
}
function tableText() {
  const header = mode.value === "id" ? "输入 ID\t姓名\t性别\t年龄\t年级\t班级\t状态" : "输入姓名\t学生 ID\t性别\t年龄\t年级\t班级\t状态";
  return [header, ...resultRows().map((row) => [row.input, mode.value === "id" ? row.student?.name : row.student?.userId,
    row.student?.gender, row.student?.age, row.student?.grade, row.student?.className, row.state].map((value) => value || "-").join("\t"))].join("\n");
}
async function copyResults() { await writeClipboard(tableText()); notify("查询结果已复制"); }
function exportCsv() {
  const csv = "\uFEFF" + tableText().split("\n").map((line) => line.split("\t").map((cell) => `"${cell.replaceAll('"', '""')}"`).join(",")).join("\r\n");
  const url = URL.createObjectURL(new Blob([csv], { type: "text/csv;charset=utf-8" }));
  const link = document.createElement("a"); link.href = url; link.download = "学生查询结果.csv"; document.body.appendChild(link); link.click(); link.remove();
  window.setTimeout(() => URL.revokeObjectURL(url), 1000); notify("查询结果已导出");
}
</script>

<template>
  <AdminLayout page-title="查询学生" active-page="students">
    <div class="admin-page-heading"><div><h1>查询学生</h1><p>姓名与学生 ID 双向查询</p></div></div>
    <div class="segmented-tabs" role="tablist" aria-label="查询方式">
      <button role="tab" :aria-selected="mode === 'name'" :class="{ active: mode === 'name' }" type="button" @click="setMode('name')">按姓名查 ID</button>
      <button role="tab" :aria-selected="mode === 'id'" :class="{ active: mode === 'id' }" type="button" @click="setMode('id')">按 ID 查姓名</button>
    </div>
    <div v-if="error" class="notice notice-error">{{ error }}</div>
    <section class="admin-panel query-panel">
      <form class="student-query-form" @submit.prevent="query">
        <label for="student-values">{{ mode === "id" ? "学生 ID" : "学生姓名" }}</label>
        <textarea id="student-values" v-model="rawValues" rows="7" :placeholder="mode === 'id' ? '每行输入一个 ID，也支持逗号分隔' : '每行输入一个姓名，也支持逗号分隔'" required></textarea>
        <div class="query-form-footer"><span>单次最多 500 个{{ mode === "id" ? "ID" : "姓名" }}，重复输入自动去重</span><button class="button button-primary" type="submit" :disabled="busy">{{ busy ? "查询中..." : "查询" }}</button></div>
      </form>
    </section>
    <template v-if="result">
      <section class="query-summary" aria-label="查询统计">
        <div><strong>{{ result.summary.total }}</strong><span>输入</span></div><div><strong>{{ result.summary.found }}</strong><span>已找到</span></div>
        <div :class="{ warn: result.summary.missing }"><strong>{{ result.summary.missing }}</strong><span>未找到</span></div>
        <div v-if="mode === 'name'" :class="{ warn: result.summary.ambiguous }"><strong>{{ result.summary.ambiguous }}</strong><span>重名</span></div>
      </section>
      <section class="admin-panel results-panel">
        <div class="panel-heading"><h2>查询结果</h2><div class="button-row"><button class="button button-quiet button-small" type="button" @click="copyResults">复制结果</button><button class="button button-quiet button-small" type="button" @click="exportCsv">导出 CSV</button></div></div>
        <div class="document-table-wrap"><table class="document-table" data-result-table>
          <thead><tr><th>{{ mode === "id" ? "输入 ID" : "输入姓名" }}</th><th>{{ mode === "id" ? "姓名" : "学生 ID" }}</th><th>性别</th><th>年龄</th><th>年级</th><th>班级</th><th>状态</th></tr></thead>
          <tbody><tr v-for="(row, index) in resultRows()" :key="`${row.input}-${row.student?.userId || index}`">
            <td>{{ row.input }}</td><td>{{ mode === "id" ? row.student?.name || "-" : row.student?.userId || "-" }}</td><td>{{ row.student?.gender || "-" }}</td><td>{{ row.student?.age || "-" }}</td><td>{{ row.student?.grade || "-" }}</td><td>{{ row.student?.className || "-" }}</td>
            <td><span class="status-badge" :class="row.state === '已找到' ? 'status-normal' : row.state === '重名候选' ? 'status-warning' : 'status-offline'">{{ row.state }}</span></td>
          </tr></tbody>
        </table></div>
      </section>
    </template>
  </AdminLayout>
</template>
