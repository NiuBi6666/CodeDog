<script setup>
import { computed, nextTick, onMounted, ref } from "vue";
import { ClipboardCopy, Download, KeyRound, RefreshCw, Search } from "@lucide/vue";
import AdminLayout from "../components/AdminLayout.vue";
import { api, jsonBody, notify, writeClipboard } from "../api";
import { buildStudentProgressRows, formatLessonState, progressResult, progressStatusClass } from "../classProgress";
import { formatDateTime } from "../utils";

const bootstrap = ref(null); const classes = ref([]); const lessons = ref([]); const report = ref(null);
const campId = ref(""); const classId = ref(""); const lessonId = ref(""); const studentKeyword = ref("");
const error = ref(""); const loading = ref(false); const querying = ref(false);
const credentialOpen = ref(false); const credentialRequired = ref(false); const credentialCookie = ref("");
const credentialError = ref(""); const credentialSaving = ref(false); const credentialInput = ref(null);
let retryAfterCredential = null;

const selectedLesson = computed(() => lessons.value.find((lesson) => String(lesson.id) === String(lessonId.value)));
const studentRows = computed(() => buildStudentProgressRows(report.value?.questions || []));
const filteredStudents = computed(() => {
  const keyword = studentKeyword.value.trim().toLowerCase();
  if (!keyword) return studentRows.value;
  return studentRows.value.filter((student) => student.name.toLowerCase().includes(keyword) || student.id.includes(keyword));
});

function requiresCredential(failure) { return failure.payload?.code === "CODEMAO_AUTH_REQUIRED"; }
async function requestCredential(retry) {
  retryAfterCredential = retry; credentialRequired.value = true; credentialError.value = "";
  error.value = ""; credentialOpen.value = true;
  await nextTick(); credentialInput.value?.focus();
}
function handleFailure(failure, retry) {
  if (requiresCredential(failure)) { requestCredential(retry); return; }
  error.value = failure.message;
}
function closeCredential() {
  credentialOpen.value = false; credentialCookie.value = ""; credentialError.value = "";
  retryAfterCredential = null; error.value = "需要有效的编程猫 Cookie 才能加载课堂数据";
}
async function submitCredential() {
  const cookie = credentialCookie.value.trim();
  if (!cookie) { credentialError.value = "请输入 Cookie"; credentialInput.value?.focus(); return; }
  credentialSaving.value = true; credentialError.value = "";
  try {
    const verified = await api("/class-progress/credential", { method: "POST", body: jsonBody({ cookie }) });
    const retry = retryAfterCredential;
    bootstrap.value = verified; credentialOpen.value = false; credentialRequired.value = false;
    credentialCookie.value = ""; retryAfterCredential = null; error.value = "";
    if (retry === loadBootstrap) {
      campId.value = verified.camps.length ? String(verified.camps[0].id) : "";
      await loadClasses();
    } else if (retry) await retry();
  } catch (failure) { credentialError.value = failure.message; }
  finally { credentialSaving.value = false; }
}

async function loadBootstrap() {
  loading.value = true; error.value = "";
  try {
    bootstrap.value = await api("/class-progress/bootstrap");
    if (bootstrap.value.camps.length) { campId.value = String(bootstrap.value.camps[0].id); await loadClasses(); }
  } catch (failure) { handleFailure(failure, loadBootstrap); }
  finally { loading.value = false; }
}

async function loadClasses() {
  classes.value = []; lessons.value = []; classId.value = ""; lessonId.value = ""; report.value = null; error.value = "";
  if (!campId.value) return;
  try {
    classes.value = await api(`/class-progress/camps/${campId.value}/classes`);
    if (classes.value.length) { classId.value = String(classes.value[0].id); await loadLessons(); }
  } catch (failure) { handleFailure(failure, loadClasses); }
}

async function loadLessons() {
  lessons.value = []; lessonId.value = ""; report.value = null; error.value = "";
  if (!classId.value) return;
  try {
    lessons.value = await api(`/class-progress/classes/${classId.value}/lessons`);
    if (lessons.value.length) lessonId.value = String(lessons.value[0].id);
  } catch (failure) { handleFailure(failure, loadLessons); }
}

async function queryProgress() {
  if (!classId.value || !lessonId.value) { error.value = "请选择班级和课次"; return; }
  querying.value = true; error.value = ""; report.value = null; studentKeyword.value = "";
  try { report.value = await api(`/class-progress/classes/${classId.value}/lessons/${lessonId.value}/report`); }
  catch (failure) { handleFailure(failure, queryProgress); }
  finally { querying.value = false; }
}

function matrixText() {
  if (!report.value) return "";
  const headers = ["学生姓名", "学生 ID", ...report.value.questions.map((question) => question.name)];
  const rows = studentRows.value.map((student) => [student.name, student.id,
    ...report.value.questions.map((question) => progressResult(student, question.stepId).resultLabel)]);
  return [headers, ...rows].map((row) => row.join("\t")).join("\n");
}

async function copyResults() { await writeClipboard(matrixText()); notify("课堂完成情况已复制"); }
function exportCsv() {
  const csv = "\uFEFF" + matrixText().split("\n").map((line) => line.split("\t")
    .map((cell) => `"${cell.replaceAll('"', '""')}"`).join(",")).join("\r\n");
  const url = URL.createObjectURL(new Blob([csv], { type: "text/csv;charset=utf-8" }));
  const link = document.createElement("a"); link.href = url;
  link.download = `${selectedLesson.value?.name || "课堂完成情况"}.csv`;
  document.body.appendChild(link); link.click(); link.remove();
  window.setTimeout(() => URL.revokeObjectURL(url), 1000); notify("课堂完成情况已导出");
}

onMounted(loadBootstrap);
</script>

<template>
  <AdminLayout page-title="课堂完成情况" active-page="class-progress">
    <div class="admin-page-heading"><div><h1>课堂完成情况</h1><p>班级课次作业统计</p></div><span v-if="bootstrap" class="teacher-chip">{{ bootstrap.teacher.name }} · {{ bootstrap.teacher.id }}</span></div>
    <div v-if="error" class="notice notice-error progress-error-notice"><span>{{ error }}</span><button v-if="credentialRequired" class="button button-small button-quiet" type="button" @click="requestCredential(loadBootstrap)"><KeyRound :size="14"/>输入 Cookie</button></div>
    <section class="admin-panel progress-filter-panel" :aria-busy="loading || querying">
      <form class="progress-filter-form" @submit.prevent="queryProgress">
        <label><span>营期</span><select v-model="campId" :disabled="loading" @change="loadClasses"><option value="">请选择营期</option><option v-for="camp in bootstrap?.camps || []" :key="camp.id" :value="String(camp.id)">{{ camp.name }} · {{ camp.coursePackageName }}</option></select></label>
        <label><span>班级</span><select v-model="classId" :disabled="!classes.length" @change="loadLessons"><option value="">请选择班级</option><option v-for="item in classes" :key="item.id" :value="String(item.id)">{{ item.name }} · {{ item.level }} · {{ item.ratio }}</option></select></label>
        <label><span>课次</span><select v-model="lessonId" :disabled="!lessons.length"><option value="">请选择课次</option><option v-for="lesson in lessons" :key="lesson.id" :value="String(lesson.id)">{{ lesson.name }} · {{ formatLessonState(lesson.state) }}</option></select></label>
        <button class="button button-primary progress-query-button" type="submit" :disabled="querying || !lessonId"><RefreshCw v-if="querying" class="spin-icon" :size="16"/><Search v-else :size="16"/>{{ querying ? "查询中" : "查询" }}</button>
      </form>
    </section>

    <template v-if="report">
      <section class="progress-summary" aria-label="课堂统计">
        <div><strong>{{ report.summary.questionCount }}</strong><span>编程题</span></div>
        <div><strong>{{ report.summary.totalStudents }}</strong><span>学生</span></div>
        <div><strong>{{ report.summary.submittedCount }}</strong><span>累计提交</span></div>
        <div class="success"><strong>{{ report.summary.acceptedCount }}</strong><span>累计通过</span></div>
        <div :class="{ warn: report.summary.unsubmittedCount }"><strong>{{ report.summary.unsubmittedCount }}</strong><span>累计未提交</span></div>
      </section>

      <section class="admin-panel progress-question-panel">
        <div class="panel-heading"><div><h2>{{ selectedLesson?.name || "题目汇总" }}</h2><small v-if="selectedLesson">{{ formatDateTime(selectedLesson.lessonTime * 1000) }}</small></div></div>
        <div class="document-table-wrap"><table class="document-table progress-question-table">
          <thead><tr><th>#</th><th>分组</th><th>题目</th><th>学生</th><th>已提交</th><th>通过</th><th>未通过</th><th>未提交</th><th>通过率</th></tr></thead>
          <tbody><tr v-for="(question, index) in report.questions" :key="question.stepId">
            <td>{{ index + 1 }}</td><td>{{ question.group || "-" }}</td><td><strong>{{ question.name }}</strong><span class="question-id">#{{ question.questionId }}</span></td><td>{{ question.totalStudents }}</td><td>{{ question.submittedStudents }}</td><td class="progress-count-success">{{ question.acceptedStudents }}</td><td>{{ question.notAcceptedStudents }}</td><td class="progress-count-warn">{{ question.unsubmittedStudents }}</td><td>{{ question.passRate || "-" }}</td>
          </tr><tr v-if="!report.questions.length"><td class="empty-table" colspan="9">本节课没有编程题</td></tr></tbody>
        </table></div>
      </section>

      <section v-if="report.questions.length" class="admin-panel progress-matrix-panel">
        <div class="panel-heading"><div><h2>学生完成明细</h2><small>{{ filteredStudents.length }} / {{ studentRows.length }} 人</small></div><div class="progress-result-actions"><label class="progress-student-search"><Search :size="15"/><input v-model.trim="studentKeyword" type="search" placeholder="姓名或学生 ID"></label><button class="button button-quiet button-small" type="button" title="复制结果" @click="copyResults"><ClipboardCopy :size="14"/>复制</button><button class="button button-quiet button-small" type="button" title="导出 CSV" @click="exportCsv"><Download :size="14"/>导出</button></div></div>
        <div class="progress-matrix-wrap"><table class="progress-matrix-table">
          <thead><tr><th>学生</th><th>学生 ID</th><th v-for="(question, index) in report.questions" :key="question.stepId" :title="question.name"><span>{{ index + 1 }}</span>{{ question.name }}</th></tr></thead>
          <tbody><tr v-for="student in filteredStudents" :key="student.id"><td><strong>{{ student.name || "-" }}</strong></td><td>{{ student.id }}</td><td v-for="question in report.questions" :key="question.stepId"><span class="status-badge" :class="progressStatusClass(progressResult(student, question.stepId).result)">{{ progressResult(student, question.stepId).resultLabel }}</span></td></tr><tr v-if="!filteredStudents.length"><td class="empty-table" :colspan="report.questions.length + 2">没有符合条件的学生</td></tr></tbody>
        </table></div>
      </section>
    </template>

    <Teleport to="body">
      <div v-if="credentialOpen" class="progress-credential-backdrop" role="presentation">
        <section class="progress-credential-dialog" role="dialog" aria-modal="true" aria-labelledby="credential-title">
          <div class="progress-credential-heading"><span><KeyRound :size="18"/></span><div><h2 id="credential-title">输入编程猫 Cookie</h2><p>验证成功后继续加载课堂数据</p></div></div>
          <form @submit.prevent="submitCredential">
            <div v-if="credentialError" class="notice notice-error">{{ credentialError }}</div>
            <label><span>Cookie</span><input ref="credentialInput" v-model="credentialCookie" type="password" autocomplete="off" spellcheck="false" maxlength="16384" placeholder="请输入当前教师账号的 Cookie" required></label>
            <small>凭据仅保存在当前后台登录会话，退出后自动清除。</small>
            <div class="progress-credential-actions"><button class="button button-quiet" type="button" :disabled="credentialSaving" @click="closeCredential">取消</button><button class="button button-primary" type="submit" :disabled="credentialSaving"><RefreshCw v-if="credentialSaving" class="spin-icon" :size="15"/>{{ credentialSaving ? "验证中" : "验证并继续" }}</button></div>
          </form>
        </section>
      </div>
    </Teleport>
  </AdminLayout>
</template>
