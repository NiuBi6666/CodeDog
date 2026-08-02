<script setup>
import { computed, ref } from "vue";
import { BarChart3, ClipboardCopy, Download, FileSpreadsheet, RefreshCw, RotateCcw, Search, Upload, X } from "@lucide/vue";
import AdminLayout from "../components/AdminLayout.vue";
import { api, notify, writeClipboard } from "../api";
import { hasPermission } from "../auth";
import { buildImportMatrix, filterImportedRows, matrixToCsv, uniqueColumnValues } from "../classProgressImport";

const files = ref([]);
const report = ref(null);
const selectedClassIndex = ref(0);
const keyword = ref("");
const filters = ref({ course: "", inClassCompletion: "", afterClassCompletion: "" });
const error = ref("");
const importing = ref(false);
const dragActive = ref(false);
const fileInput = ref(null);

const activeClass = computed(() => report.value?.classes?.[selectedClassIndex.value] || null);
const filterRows = computed(() => activeClass.value?.rows || []);
const courseOptions = computed(() => uniqueColumnValues(filterRows.value, "M"));
const filteredRows = computed(() => filterImportedRows(filterRows.value, keyword.value, {
  M: filters.value.course,
  inClassCompletion: filters.value.inClassCompletion,
  afterClassCompletion: filters.value.afterClassCompletion
}));
const hasActiveFilters = computed(() => Boolean(keyword.value || Object.values(filters.value).some(Boolean)));
const courseCount = computed(() => new Set(
  (report.value?.classes || []).flatMap((item) => item.courseNames || [])
).size);
const columnGroups = computed(() => {
  const groups = [];
  for (const column of report.value?.columns || []) {
    const current = groups[groups.length - 1];
    if (current?.label === column.group) current.count += 1;
    else groups.push({ label: column.group, count: 1 });
  }
  return groups;
});

function fileKey(file) {
  return `${file.name}-${file.size}-${file.lastModified}`;
}

function formatFileSize(size) {
  if (size < 1024) return `${size} B`;
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`;
  return `${(size / 1024 / 1024).toFixed(1)} MB`;
}

function resetFilters() {
  keyword.value = "";
  filters.value = { course: "", inClassCompletion: "", afterClassCompletion: "" };
}

function addFiles(fileList) {
  const incoming = Array.from(fileList || []);
  error.value = "";
  if (!incoming.length) return;
  const invalid = incoming.find((file) => !file.name.toLowerCase().endsWith(".xlsx") || file.size > 10 * 1024 * 1024);
  if (invalid) {
    error.value = `${invalid.name} 不是有效的 10MB 以内 .xlsx 文件`;
    return;
  }
  const next = [...files.value];
  const known = new Set(next.map(fileKey));
  for (const file of incoming) {
    if (!known.has(fileKey(file))) {
      next.push(file);
      known.add(fileKey(file));
    }
  }
  if (next.length > 20) {
    error.value = "一次最多上传 20 个班级文件";
    return;
  }
  files.value = next;
  report.value = null;
}

function chooseFiles() {
  fileInput.value?.click();
}

function handleFileChange(event) {
  addFiles(event.target.files);
  event.target.value = "";
}

function handleDrop(event) {
  dragActive.value = false;
  addFiles(event.dataTransfer?.files);
}

function removeFile(file) {
  files.value = files.value.filter((item) => fileKey(item) !== fileKey(file));
  report.value = null;
}

async function importFiles() {
  if (!files.value.length) {
    error.value = "请先选择班级 Excel 文件";
    return;
  }
  importing.value = true;
  error.value = "";
  try {
    const body = new FormData();
    files.value.forEach((file) => body.append("files", file, file.name));
    report.value = await api("/class-progress/import", { method: "POST", body });
    selectedClassIndex.value = 0;
    resetFilters();
    notify(`已导入 ${report.value.fileCount} 个班级文件`);
  } catch (failure) {
    report.value = null;
    error.value = failure.message;
  } finally {
    importing.value = false;
  }
}

function selectClass(index) {
  selectedClassIndex.value = index;
  resetFilters();
}

function matrixText() {
  return buildImportMatrix(report.value?.columns || [], filteredRows.value);
}

async function copyResults() {
  await writeClipboard(matrixText());
  notify("当前班级数据已复制");
}

function exportCsv() {
  const csv = matrixToCsv(matrixText());
  const url = URL.createObjectURL(new Blob([csv], { type: "text/csv;charset=utf-8" }));
  const link = document.createElement("a");
  link.href = url;
  link.download = `${(activeClass.value?.className || "课堂完成情况").replace(/[\\/:*?"<>|]/g, "-")}.csv`;
  document.body.appendChild(link);
  link.click();
  link.remove();
  window.setTimeout(() => URL.revokeObjectURL(url), 1000);
  notify("当前班级数据已导出");
}
</script>

<template>
  <AdminLayout page-title="课堂完成情况" active-page="class-progress">
    <div class="admin-page-heading">
      <div><h1>课堂完成情况</h1><p>多班级 Excel 数据汇总</p></div>
    </div>

    <div v-if="error" class="notice notice-error">{{ error }}</div>

    <section class="admin-panel progress-upload-panel">
      <input
        ref="fileInput"
        class="progress-file-input"
        type="file"
        accept=".xlsx,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        multiple
        @change="handleFileChange"
      >
      <div
        class="progress-dropzone"
        :class="{ active: dragActive }"
        @dragenter.prevent="dragActive = true"
        @dragover.prevent="dragActive = true"
        @dragleave.prevent="dragActive = false"
        @drop.prevent="handleDrop"
      >
        <span class="progress-upload-icon"><FileSpreadsheet :size="22"/></span>
        <div><strong>上传班级数据</strong><small>每个 .xlsx 文件对应一个班级，可同时选择多个文件</small></div>
        <button v-if="hasPermission('class_progress.import')" class="button button-quiet" type="button" @click="chooseFiles"><Upload :size="15"/>选择文件</button>
      </div>

      <div v-if="files.length" class="progress-file-list">
        <div v-for="file in files" :key="fileKey(file)" class="progress-file-item">
          <FileSpreadsheet :size="16"/>
          <span><strong>{{ file.name }}</strong><small>{{ formatFileSize(file.size) }}</small></span>
          <button class="icon-button" type="button" title="移除文件" aria-label="移除文件" :disabled="importing" @click="removeFile(file)"><X :size="15"/></button>
        </div>
      </div>

      <div class="progress-upload-footer">
        <span>{{ files.length ? `已选择 ${files.length} 个班级文件` : "尚未选择文件" }}</span>
        <button v-if="hasPermission('class_progress.import')" class="button button-primary" type="button" :disabled="importing || !files.length" @click="importFiles">
          <RefreshCw v-if="importing" class="spin-icon" :size="15"/><BarChart3 v-else :size="15"/>
          {{ importing ? "分析中" : "分析并查看" }}
        </button>
      </div>
    </section>

    <template v-if="report">
      <section class="progress-import-summary" aria-label="导入汇总">
        <div><strong>{{ report.classes.length }}</strong><span>班级</span></div>
        <div><strong>{{ report.rowCount }}</strong><span>数据行</span></div>
        <div><strong>{{ courseCount }}</strong><span>课程</span></div>
        <div><strong>{{ report.fileCount }}</strong><span>文件</span></div>
      </section>

      <div class="progress-class-tabs" role="tablist" aria-label="班级">
        <button
          v-for="(item, index) in report.classes"
          :key="item.className"
          type="button"
          role="tab"
          :aria-selected="selectedClassIndex === index"
          :class="{ active: selectedClassIndex === index }"
          @click="selectClass(index)"
        >
          <strong>{{ item.className }}</strong><span>{{ item.rowCount }} 行</span>
        </button>
      </div>

      <section v-if="activeClass" class="admin-panel progress-import-panel">
        <div class="panel-heading progress-import-heading">
          <div>
            <h2>{{ activeClass.className }}</h2>
            <small>{{ activeClass.fileName }} · {{ activeClass.courseNames.join("、") }}</small>
          </div>
          <div class="progress-result-actions">
            <button v-if="hasPermission('class_progress.copy')" class="button button-quiet button-small" type="button" :disabled="!filteredRows.length" @click="copyResults"><ClipboardCopy :size="14"/>复制</button>
            <button v-if="hasPermission('class_progress.export')" class="button button-quiet button-small" type="button" :disabled="!filteredRows.length" @click="exportCsv"><Download :size="14"/>导出</button>
          </div>
        </div>

        <div class="progress-filter-bar" aria-label="数据筛选">
          <div class="progress-filter-field progress-filter-search">
            <span>关键词</span>
            <label class="progress-student-search"><Search :size="15"/><input v-model.trim="keyword" type="search" placeholder="姓名、ID 或课程"></label>
          </div>
          <label class="progress-filter-field">
            <span>课程名称</span>
            <select v-model="filters.course"><option value="">全部课程</option><option v-for="value in courseOptions" :key="value" :value="value">{{ value }}</option></select>
          </label>
          <label class="progress-filter-field">
            <span>课中作业是否完成</span>
            <select v-model="filters.inClassCompletion">
              <option value="">全部状态</option>
              <option value="complete">已完成</option>
              <option value="incomplete">未完成</option>
            </select>
          </label>
          <label class="progress-filter-field">
            <span>课后作业是否完成</span>
            <select v-model="filters.afterClassCompletion">
              <option value="">全部状态</option>
              <option value="complete">已完成</option>
              <option value="incomplete">未完成</option>
            </select>
          </label>
          <span class="progress-filter-count">{{ filteredRows.length }} / {{ activeClass.rowCount }} 条</span>
          <button class="button button-quiet button-small progress-filter-reset" type="button" :disabled="!hasActiveFilters" @click="resetFilters"><RotateCcw :size="14"/>重置</button>
        </div>

        <div class="progress-import-table-wrap">
          <table class="progress-import-table">
            <thead>
              <tr class="progress-group-row"><th v-for="group in columnGroups" :key="group.label" :colspan="group.count">{{ group.label }}</th></tr>
              <tr><th v-for="column in report.columns" :key="column.key"><span>{{ column.key }}</span>{{ column.label }}</th></tr>
            </thead>
            <tbody>
              <tr v-for="(row, index) in filteredRows" :key="`${row.values.A}-${row.values.M}-${index}`">
                <td v-for="column in report.columns" :key="column.key">{{ row.values[column.key] || "-" }}</td>
              </tr>
              <tr v-if="!filteredRows.length"><td class="empty-table" :colspan="report.columns.length">没有符合条件的数据</td></tr>
            </tbody>
          </table>
        </div>
      </section>
    </template>
  </AdminLayout>
</template>
