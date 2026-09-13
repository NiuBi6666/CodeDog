<script setup>
import { computed, onMounted, ref } from "vue";
import { FileSpreadsheet, Copy, ExternalLink } from "@lucide/vue";
import AdminLayout from "../components/AdminLayout.vue";
import { api, jsonBody, notify, writeClipboard } from "../api";
import { formatDateTime } from "../utils";
import { displayExamScore, suggestExamColumns, examUrl, EXAM_URL_PREFIX, validExamSuffix } from "../examImport";

const title = ref("");
const file = ref(null);
const fileInput = ref(null);
const sheetIndex = ref(0);
const headerRow = ref(1);
const inspection = ref(null);
const nameColumn = ref(0);
const selected = ref([]);
const labels = ref({});
const busy = ref(false);
const error = ref("");
const listError = ref("");
const created = ref(null);
const exams = ref({ exams: [], total: 0, page: 0, pageCount: 0 });
const loadingList = ref(false);
const changing = ref(null);
const linkDrafts = ref({});
const linkErrors = ref({});
const savingLink = ref(null);
const mappingCurrent = computed(() => inspection.value && inspection.value.sheetIndex === Number(sheetIndex.value) && inspection.value.headerRow === Number(headerRow.value));
const chosen = computed(() => inspection.value?.columns.filter(c => selected.value.includes(c.index)) || []);
const canCreate = computed(() => mappingCurrent.value && chosen.value.length > 0 && chosen.value.length <= 20 && !chosen.value.some(c => c.index === nameColumn.value || !labels.value[c.index]?.trim()) && title.value.trim());
const selectableColumns = computed(() => (inspection.value?.columns || []).filter(c => c.index !== nameColumn.value).slice().sort((a, b) => Number(selected.value.includes(b.index)) - Number(selected.value.includes(a.index)) || a.index - b.index));
const sampleRows = computed(() => inspection.value?.samples.filter(row => row[nameColumn.value]?.trim()).slice(0, 5) || []);
const link = exam => examUrl(exam.queryPath);

async function load(page = 0) {
  loadingList.value = true; listError.value = "";
  try { exams.value = await api("/admin/exams?page=" + page); }
  catch (failure) { listError.value = failure.status ? failure.message : "连接未成功，请检查网络后重试。"; }
  finally { loadingList.value = false; }
}
function editLink(exam) {
  linkDrafts.value[exam.id] = { suffix: exam.publicId, expectedSuffix: exam.publicId };
  delete linkErrors.value[exam.id];
}
async function cancelLink(exam) {
  delete linkDrafts.value[exam.id];
  delete linkErrors.value[exam.id];
  await load(exams.value.page);
}
async function saveLink(exam) {
  const draft = linkDrafts.value[exam.id];
  if (!draft || savingLink.value !== null) return;
  if (!validExamSuffix(draft.suffix)) {
    linkErrors.value[exam.id] = "请输入 8 位后缀，同时包含大写字母、小写字母和数字。";
    return;
  }
  savingLink.value = exam.id;
  delete linkErrors.value[exam.id];
  try {
    const saved = await api("/admin/exams/" + exam.id + "/link", {
      method: "PATCH", body: jsonBody({ suffix: draft.suffix, expectedSuffix: draft.expectedSuffix })
    });
    exams.value.exams = exams.value.exams.map(row => row.id === saved.id ? saved : row);
    if (created.value?.id === saved.id) created.value = saved;
    delete linkDrafts.value[exam.id];
    notify("查询链接已修改，复制和查看将使用新链接");
  } catch (failure) {
    linkErrors.value[exam.id] = failure.status ? failure.message : "连接未成功，请重试；复制和查看仍使用已保存的链接。";
  } finally { savingLink.value = null; }
}
async function chooseFile(event) {
  file.value = event.target.files?.[0] || null;
  inspection.value = null; created.value = null; error.value = "";
  sheetIndex.value = 0; headerRow.value = 1; selected.value = [];
  if (!file.value) return;
  if (file.value.size > 10 * 1024 * 1024) { error.value = "请上传不超过 10 MB 的 Excel 文件。"; return; }
  if (!title.value.trim()) title.value = file.value.name.replace(/\.(xlsx|xls)$/i, "").slice(0, 120);
  await inspectFile();
}
async function inspectFile() {
  if (!file.value) return;
  busy.value = true; error.value = ""; created.value = null;
  const body = new FormData();
  body.append("file", file.value);
  body.append("sheetIndex", String(sheetIndex.value));
  body.append("headerRow", String(headerRow.value));
  try {
    inspection.value = await api("/admin/exams/inspect", { method: "POST", body });
    const suggested = suggestExamColumns(inspection.value.columns);
    nameColumn.value = suggested.nameColumn;
    selected.value = suggested.scoreColumns;
    labels.value = Object.fromEntries(inspection.value.columns.map(c => [c.index, c.label || c.letter + "列成绩"]));
  } catch (failure) { inspection.value = null; error.value = failure.status ? failure.message : "连接未成功，请检查网络后重试。"; }
  finally { busy.value = false; }
}
async function createExam() {
  if (!canCreate.value || busy.value || created.value) return;
  busy.value = true; error.value = "";
  const body = new FormData();
  body.append("file", file.value);
  body.append("mapping", JSON.stringify({
    title: title.value.trim(), sheetIndex: Number(sheetIndex.value), headerRow: Number(headerRow.value),
    nameColumn: Number(nameColumn.value), scoreColumns: chosen.value.map(c => c.index),
    scoreLabels: chosen.value.map(c => labels.value[c.index].trim())
  }));
  try {
    created.value = await api("/admin/exams", { method: "POST", body });
    notify("成绩已上传，查询链接已生成");
    await load();
  } catch (failure) { error.value = failure.status ? failure.message : "连接未成功，请检查网络后重试。"; }
  finally { busy.value = false; }
}
function reset() {
  title.value = ""; file.value = null; inspection.value = null; created.value = null; selected.value = [];
  error.value = ""; if (fileInput.value) fileInput.value.value = "";
}
async function copy(exam) {
  try { await writeClipboard(link(exam)); notify("查询链接已复制"); }
  catch { notify("复制失败，请选中链接手动复制"); }
}
async function toggle(exam) {
  changing.value = exam.id; listError.value = "";
  try {
    await api("/admin/exams/" + exam.id + "/status", { method: "PATCH", body: jsonBody({ enabled: !exam.enabled }) });
    await load(exams.value.page);
    if (created.value?.id === exam.id) created.value = { ...created.value, enabled: !exam.enabled };
    notify(exam.enabled ? "已暂停查询" : "已恢复查询");
  } catch (failure) { listError.value = failure.status ? failure.message : "连接未成功，请检查网络后重试。"; }
  finally { changing.value = null; }
}
onMounted(() => load());
</script>

<template>
  <AdminLayout page-title="成绩管理" active-page="exams">
    <div class="admin-page-heading"><div><h1>成绩管理</h1><p>上传每场考试的成绩，生成专属查询链接。</p></div></div>
    <form class="exam-panel" @submit.prevent="createExam">
      <h2><FileSpreadsheet :size="21"/>上传成绩</h2>
      <fieldset :disabled="busy || Boolean(created)">
        <div class="exam-form-grid">
          <label>考试类型 / 名称<input v-model="title" maxlength="120" required placeholder="例如：CSP-J 九月模拟考"></label>
          <label>Excel 成绩表<input ref="fileInput" type="file" accept=".xlsx,.xls" @change="chooseFile"><small>支持 .xlsx、.xls，最大 10 MB，每次最多 10000 名学员。</small></label>
        </div>
        <div v-if="inspection" class="exam-mapping">
          <div class="exam-form-grid exam-source">
            <label>工作表<select v-model.number="sheetIndex"><option v-for="sheet in inspection.sheets" :key="sheet.index" :value="sheet.index">{{ sheet.name }}</option></select></label>
            <label>表头所在行<input v-model.number="headerRow" type="number" min="1" max="100"></label>
            <button class="button button-quiet" type="button" @click="inspectFile">读取列信息</button>
          </div>
          <p v-if="!mappingCurrent" class="notice">工作表或表头行已改变，请点击“读取列信息”。</p>
          <template v-else>
            <label class="exam-name-column">姓名列<select v-model.number="nameColumn" @change="selected = selected.filter(c => c !== nameColumn)"><option v-for="col in inspection.columns" :key="col.index" :value="col.index">{{ col.letter }} 列 · {{ col.label || '无表头' }}</option></select></label>
            <h3>选择要展示的成绩列</h3>
            <p class="exam-hint">可选择多列；右侧名称会显示在家长查询页。只勾选需要公开的成绩。</p>
            <div class="exam-columns">
              <label v-for="col in selectableColumns" :key="col.index" class="exam-column">
                <span><input v-model="selected" type="checkbox" :value="col.index"><span>{{ col.letter }} 列 · {{ col.label || '无表头' }}</span></span>
                <input v-if="selected.includes(col.index)" v-model="labels[col.index]" :aria-label="col.letter + '列成绩名称'" maxlength="64" required>
              </label>
            </div>
            <p v-if="chosen.length > 20" class="notice notice-error">最多选择 20 列成绩。</p>
            <template v-if="chosen.length">
              <h3>成绩预览 <small>前 {{ sampleRows.length }} 行</small></h3>
              <div class="exam-table-wrap"><table class="document-table"><thead><tr><th>学员姓名</th><th v-for="col in chosen" :key="col.index">{{ labels[col.index] }}</th></tr></thead><tbody>
                <tr v-for="(row, i) in sampleRows" :key="i"><td>{{ row[nameColumn] }}</td><td v-for="col in chosen" :key="col.index">{{ displayExamScore(row[col.index]) }}</td></tr>
              </tbody></table></div>
              <p class="exam-hint">家长按完整姓名查询，仅看到对应成绩。0 分显示“未参考”，空白显示“暂无成绩”。</p>
            </template>
          </template>
        </div>
      </fieldset>
      <p v-if="error" class="notice notice-error" role="alert">{{ error }}</p>
      <div v-if="!created" class="exam-form-actions"><button class="button button-primary" type="submit" :disabled="busy || !canCreate">{{ busy ? '正在处理…' : '生成查询链接' }}</button></div>
      <div v-else class="exam-created" role="status">
        <strong>“{{ created.title }}”已导入 {{ created.studentCount }} 名学员</strong>
        <label>家长查询链接<input :value="link(created)" readonly @focus="$event.target.select()"></label>
        <div class="row-actions"><button class="button button-primary" type="button" @click="copy(created)"><Copy :size="15"/>复制链接</button><a class="button button-quiet" :href="link(created)" target="_blank" rel="noopener">打开查询页<ExternalLink :size="15"/></a><button class="button button-quiet" type="button" @click="reset">再上传一场考试</button></div>
      </div>
    </form>

    <section class="exam-panel exam-history">
      <h2>已上传的考试 <small>{{ exams.total }} 场</small></h2>
      <p class="exam-hint">查询后缀为 8 位，须包含大写字母、小写字母和数字，区分大小写。修改后点击“保存”，再复制新链接。</p>
      <p v-if="listError" class="notice notice-error" role="alert">{{ listError }}</p>
      <div class="exam-table-wrap"><table class="document-table">
        <thead><tr><th>考试名称</th><th>成绩项目</th><th>学员数</th><th>状态</th><th>上传时间</th><th>查询链接</th><th>操作</th></tr></thead>
        <tbody>
          <tr v-for="exam in exams.exams" :key="exam.id">
            <td><strong>{{ exam.title }}</strong></td><td>{{ exam.scoreLabels.join('、') }}</td><td>{{ exam.studentCount }}</td>
            <td><span class="status-badge" :class="exam.enabled ? 'status-normal' : 'status-offline'">{{ exam.enabled ? '可查询' : '已暂停' }}</span></td>
            <td>{{ formatDateTime(exam.createdAt) }}</td>
            <td class="exam-link-cell">
              <div class="exam-link-editor">
                <span class="exam-link-prefix">{{ EXAM_URL_PREFIX }}</span>
                <input v-if="linkDrafts[exam.id]" v-model="linkDrafts[exam.id].suffix" class="exam-link-suffix" :aria-label="exam.title + '查询后缀'" :aria-describedby="'link-hint-' + exam.id" :aria-invalid="Boolean(linkErrors[exam.id])" :disabled="savingLink === exam.id" minlength="8" maxlength="8" autocomplete="off" autocapitalize="off" spellcheck="false" @keydown.enter.prevent="saveLink(exam)" @keydown.esc.prevent="cancelLink(exam)">
                <code v-else class="exam-saved-suffix">{{ exam.publicId }}</code>
              </div>
              <div v-if="linkDrafts[exam.id]" class="exam-link-controls">
                <button class="button button-primary button-small" :disabled="savingLink !== null" @click="saveLink(exam)">{{ savingLink === exam.id ? '保存中…' : '保存' }}</button>
                <button class="button button-quiet button-small" :disabled="savingLink === exam.id" @click="cancelLink(exam)">取消</button>
                <small :id="'link-hint-' + exam.id">仅可修改后缀</small>
              </div>
              <button v-else class="button button-quiet button-small exam-edit-link" @click="editLink(exam)">修改后缀</button>
              <p v-if="linkErrors[exam.id]" class="exam-link-error" role="alert">{{ linkErrors[exam.id] }}</p>
            </td>
            <td><div class="row-actions"><button class="button button-quiet button-small" @click="copy(exam)">复制链接</button><a class="button button-quiet button-small" :href="link(exam)" target="_blank" rel="noopener">查看</a><button class="button button-quiet button-small" :disabled="changing !== null" @click="toggle(exam)">{{ changing === exam.id ? '处理中…' : exam.enabled ? '暂停查询' : '恢复查询' }}</button></div></td>
          </tr>
          <tr v-if="!exams.exams.length"><td colspan="7" class="empty-table">{{ loadingList ? '正在加载…' : '还没有上传考试，请先上传 Excel 成绩表。' }}</td></tr>
        </tbody>
      </table></div>
      <nav v-if="exams.pageCount > 1" class="pagination" aria-label="考试分页"><button class="button button-quiet" :disabled="loadingList || exams.page === 0" @click="load(exams.page - 1)">上一页</button><span>第 {{ exams.page + 1 }} / {{ exams.pageCount }} 页</span><button class="button button-quiet" :disabled="loadingList || exams.page + 1 >= exams.pageCount" @click="load(exams.page + 1)">下一页</button></nav>
    </section>
  </AdminLayout>
</template>

<style scoped>
.exam-panel{background:#fff;border:1px solid #dce5e9;border-radius:8px;padding:24px;margin-bottom:24px}
.exam-panel h2{font-size:19px;display:flex;align-items:center;gap:9px;margin:0 0 22px}
.exam-panel h3{font-size:15px;margin:24px 0 10px}.exam-panel small,.exam-hint{font-size:13px;color:#687c87;font-weight:400;line-height:1.7}.exam-hint{margin:8px 0 14px}
fieldset{border:0;padding:0;margin:0;min-width:0}
.exam-form-grid{display:grid;grid-template-columns:1fr 1fr;gap:24px}
.exam-panel label{display:flex;flex-direction:column;gap:8px;font-weight:600;font-size:14px}
.exam-panel input:not([type=checkbox]),.exam-panel select{width:100%;min-width:0;padding:10px 12px;border:1px solid #c9d6dd;border-radius:5px;background:#fff;font:inherit;color:#263b44}
.exam-panel input:focus-visible,.exam-panel select:focus-visible{outline:2px solid #168e8b;outline-offset:2px}
.exam-mapping{margin-top:24px;padding-top:22px;border-top:1px solid #e3ebef}
.exam-source{grid-template-columns:minmax(160px,1fr) minmax(100px,160px) auto;align-items:end;margin-bottom:20px}
.exam-name-column{max-width:440px}
.exam-columns{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:10px 20px;max-height:340px;overflow:auto;padding:2px}
.exam-panel .exam-column{display:grid;grid-template-columns:1fr 1fr;align-items:center;border:1px solid #e3ebef;border-radius:5px;padding:8px 10px;font-weight:400;min-height:50px}
.exam-column>span{display:flex;gap:8px;align-items:center;overflow-wrap:anywhere}
.exam-column input[type=checkbox]{width:17px;height:17px;accent-color:#177d77;flex-shrink:0}
.exam-table-wrap{overflow:auto}.exam-table-wrap td{max-width:300px;overflow-wrap:anywhere}
.exam-form-actions{margin-top:24px}.exam-created{margin-top:22px;background:#eff9f6;border:1px solid #bdded4;border-radius:6px;padding:20px;display:grid;gap:16px}
.exam-link-cell{min-width:330px;max-width:none!important}
.exam-link-editor{display:flex;align-items:center;gap:0;white-space:nowrap;font-size:12px}
.exam-link-prefix{color:#687c87;user-select:all}
.exam-panel .exam-link-suffix{width:102px;flex:0 0 102px;padding:8px;margin-left:4px;font:600 13px ui-monospace,monospace;letter-spacing:.5px}
.exam-saved-suffix{font:600 13px ui-monospace,monospace;color:#263b44}
.exam-link-controls{display:flex;align-items:center;gap:8px;margin-top:10px}
.exam-edit-link{margin-top:10px}
.exam-link-error{font-size:12px;color:#b33d32;white-space:normal;max-width:330px;margin:8px 0 0}
.exam-panel button:disabled{opacity:.55;cursor:not-allowed}
@media(max-width:850px){.exam-columns,.exam-form-grid{grid-template-columns:1fr}.exam-source{grid-template-columns:1fr 110px}.exam-source button{grid-column:1/-1}.exam-panel{padding:18px}.exam-panel .exam-column{grid-template-columns:minmax(0,1fr) minmax(0,1fr)}}
</style>
