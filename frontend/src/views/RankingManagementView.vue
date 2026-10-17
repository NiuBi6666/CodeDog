<script setup>
import { showConfirm } from "../uiDialogs.js";
import { computed, onBeforeUnmount, onMounted, ref } from "vue";
import {
  ArrowDown, ArrowUp, Award, CalendarClock, CheckCircle2, CircleOff, Copy, ExternalLink, Gift, ImagePlus,
  Eye, EyeOff, Megaphone, Minus, PackageCheck, Pencil, Plus, RefreshCw, Save, Search, Share2, Upload,
  Trash2, Trophy, UsersRound, X
} from "@lucide/vue";
import AdminLayout from "../components/AdminLayout.vue";
import { api, jsonBody, notify, writeClipboard } from "../api";
import { auth, hasPermission } from "../auth";
import { rankingShareUrl, rankingSummary, rankingTrendView } from "../rankingAdmin.js";
import { formatDateTime } from "../utils";

const board = ref(null);
const rewards = ref([]);
const redemptions = ref([]);
const announcements = ref([]);
const announcementDraft = ref("");
const announcementPublishAt = ref("");
const announcementUnpublishAt = ref("");
const activeTab = ref("rankings");
const searchText = ref("");
const redemptionSearch = ref("");
const loading = ref(false);
const savingAnnouncement = ref(false);
const error = ref("");
const shareOpen = ref(false);
const rewardOpen = ref(false);
const redemptionOpen = ref(false);
const passwordOpen = ref(false);
const passwordLoading = ref(false);
const passwordSaving = ref(false);
const passwordVisible = ref(false);
const passwordError = ref("");
const passwordStudent = ref(null);
const recoveredPassword = ref("");
const resetPasswordValue = ref("");
const pointsOpen = ref(false);
const pointsSaving = ref(false);
const pointsStudent = ref(null);
const pointsTarget = ref("");
const pointsReason = ref("");
const pointsError = ref("");
const pointsHistory = ref([]);
const pointsHistoryLoading = ref(false);
const pointsHistoryError = ref("");
const pointImportInput = ref(null);
const pointsImporting = ref(false);
const pointsImportSummary = ref(null);
const rewardSaving = ref(false);
const redemptionSaving = ref(false);
const rewardForm = ref(emptyRewardForm());
const rewardImage = ref(null);
const rewardPreview = ref("");
const redemptionForm = ref({ studentId: "", rewardId: "" });
const canBoardRead = computed(() => hasPermission("rankings.board.read"));
const canImportPoints = computed(() => hasPermission("rankings.import"));
const canShare = computed(() => hasPermission("rankings.share"));
const canAnnouncementsRead = computed(() => hasPermission("rankings.announcements.read"));
const canAnnouncementsCreate = computed(() => hasPermission("rankings.announcements.create"));
const canAnnouncementsEdit = computed(() => hasPermission("rankings.announcements.edit"));
const canAnnouncementsStatus = computed(() => hasPermission("rankings.announcements.status"));
const canRewardsRead = computed(() => hasPermission("rankings.rewards.read"));
const canPointsEdit = computed(() => hasPermission("rankings.points.edit"));
const canRewardsCreate = computed(() => hasPermission("rankings.rewards.create"));
const canRewardsEdit = computed(() => hasPermission("rankings.rewards.edit"));
const canRewardsDelete = computed(() => hasPermission("rankings.rewards.delete"));
const canRedemptionsRead = computed(() => hasPermission("rankings.redemptions.read"));
const canRedemptionsCreate = computed(() => hasPermission("rankings.redemptions.create"));
const canRedemptionsFulfill = computed(() => hasPermission("rankings.redemptions.fulfill"));
const canAnnouncementsTab = computed(() => canAnnouncementsRead.value || canAnnouncementsCreate.value || canAnnouncementsEdit.value || canAnnouncementsStatus.value);
const canRewardsTab = computed(() => canRewardsRead.value || canRewardsCreate.value || canRewardsEdit.value || canRewardsDelete.value);
const canRedemptionsTab = computed(() => canRedemptionsRead.value || canRedemptionsCreate.value || canRedemptionsFulfill.value);
const canRefresh = computed(() => canBoardRead.value || canAnnouncementsRead.value || canRewardsRead.value || canRedemptionsRead.value);

const rows = computed(() => board.value?.rankings || []);
const summary = computed(() => rankingSummary(rows.value));
const filteredRows = computed(() => {
  const query = searchText.value.trim().toLowerCase();
  if (!query) return rows.value;
  return rows.value.filter((row) => `${row.studentName} ${row.studentId}`.toLowerCase().includes(query));
});
const filteredRedemptions = computed(() => {
  const query = redemptionSearch.value.trim().toLowerCase();
  if (!query) return redemptions.value;
  return redemptions.value.filter((row) => `${row.studentName} ${row.studentId} ${row.rewardName}`.toLowerCase().includes(query));
});
const enabledRewards = computed(() => rewards.value.filter((reward) => reward.enabled && reward.stock > 0));
const selectedStudent = computed(() => rows.value.find((row) => String(row.studentId) === String(redemptionForm.value.studentId)) || null);
const selectedReward = computed(() => rewards.value.find((reward) => String(reward.id) === String(redemptionForm.value.rewardId)) || null);
const spentByStudent = computed(() => redemptions.value.reduce((map, item) => {
  map[item.studentId] = (map[item.studentId] || 0) + Number(item.pointsSpent || 0); return map;
}, {}));
const availablePoints = computed(() => Math.max(0, Number(selectedStudent.value?.availablePoints ?? selectedStudent.value?.totalPoints ?? 0)));
const redemptionAllowed = computed(() => selectedStudent.value && selectedReward.value && selectedReward.value.stock > 0 && selectedReward.value.requiredPoints <= availablePoints.value);
const shareUrl = computed(() => rankingShareUrl({ origin: window.location.origin }));
const pendingCount = computed(() => redemptions.value.filter((item) => item.status === "PENDING").length);

function emptyRewardForm() { return { id: null, name: "", requiredPoints: 100, stock: 0, expectedStock: 0, enabled: true, imageUrl: "" }; }
function numberText(value) { return new Intl.NumberFormat("zh-CN").format(Number(value || 0)); }
function toDateTimeInput(value) {
  if (!value) return "";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return "";
  const pad = (number) => String(number).padStart(2, "0");
  return date.getFullYear() + "-" + pad(date.getMonth() + 1) + "-" + pad(date.getDate()) + "T" + pad(date.getHours()) + ":" + pad(date.getMinutes());
}
function toInstant(value) { return value ? new Date(value).toISOString() : null; }
function normaliseAnnouncements(values) {
  return (values || []).map((item) => ({
    ...item,
    publishAtInput: toDateTimeInput(item.publishAt),
    unpublishAtInput: toDateTimeInput(item.unpublishAt)
  }));
}
function firstAllowedTab() {
  if (canBoardRead.value) return "rankings";
  if (canRewardsTab.value) return "rewards";
  if (canRedemptionsTab.value) return "redemptions";
  if (canAnnouncementsTab.value) return "announcements";
  return "";
}
function tabAllowed(tab) {
  return tab === "rankings" ? canBoardRead.value : tab === "rewards" ? canRewardsTab.value
    : tab === "redemptions" ? canRedemptionsTab.value : tab === "announcements" ? canAnnouncementsTab.value : false;
}

async function loadAll() {
  loading.value = true; error.value = "";
  try {
    const tasks = [];
    if (canBoardRead.value) tasks.push(api("/rankings/admin/board").then((value) => { board.value = value; }));
    if (canAnnouncementsRead.value) tasks.push(api("/rankings/admin/announcements").then((value) => { announcements.value = normaliseAnnouncements(value); }));
    if (canRewardsRead.value) tasks.push(api("/rankings/admin/rewards").then((value) => { rewards.value = value; }));
    if (canRedemptionsRead.value) tasks.push(api("/rankings/admin/redemptions").then((value) => { redemptions.value = value; }));
    await Promise.all(tasks);
    if (!tabAllowed(activeTab.value)) activeTab.value = firstAllowedTab();
  } catch (failure) { error.value = failure.message; }
  finally { loading.value = false; }
}

async function saveAnnouncement() {
  savingAnnouncement.value = true;
  try {
    const publishAt = toInstant(announcementPublishAt.value);
    const unpublishAt = toInstant(announcementUnpublishAt.value);
    if (publishAt && unpublishAt && new Date(unpublishAt) <= new Date(publishAt)) {
      notify("下线时间必须晚于上线时间"); return;
    }
    const created = await api("/rankings/admin/announcements", {
      method: "POST", body: jsonBody({ text: announcementDraft.value, publishAt, unpublishAt })
    });
    if (canAnnouncementsRead.value) announcements.value = [normaliseAnnouncements([created])[0], ...announcements.value];
    announcementDraft.value = ""; announcementPublishAt.value = ""; announcementUnpublishAt.value = "";
    notify(created.status === "SCHEDULED" ? "公告已设置定时发布" : "公告已发布");
  } catch (failure) { notify(failure.message); }
  finally { savingAnnouncement.value = false; }
}
function announcementStatusLabel(status) {
  return status === "ONLINE" ? "已上线" : status === "SCHEDULED" ? "定时发布" : "已下线";
}
async function setAnnouncementOnline(item, online) {
  try {
    const updated = await api(`/rankings/admin/announcements/${item.id}/status`, {
      method: "PATCH", body: jsonBody({ online })
    });
    announcements.value = announcements.value.map((row) => row.id === updated.id ? { ...normaliseAnnouncements([updated])[0] } : row);
    notify(online ? "公告已上线" : "公告已下线");
  } catch (failure) { notify(failure.message); }
}
async function saveAnnouncementSchedule(item) {
  const publishAt = toInstant(item.publishAtInput);
  const unpublishAt = toInstant(item.unpublishAtInput);
  if (publishAt && unpublishAt && new Date(unpublishAt) <= new Date(publishAt)) {
    notify("下线时间必须晚于上线时间"); return;
  }
  try {
    const updated = await api("/rankings/admin/announcements/" + item.id, {
      method: "PATCH", body: jsonBody({ publishAt, unpublishAt })
    });
    const normalised = normaliseAnnouncements([updated])[0];
    announcements.value = announcements.value.map((row) => row.id === updated.id ? normalised : row);
    notify("公告时间已更新");
  } catch (failure) { notify(failure.message); }
}
function startAnnouncementEdit(item) {
  item.editing = true;
  item.editText = item.text;
}
function cancelAnnouncementEdit(item) {
  item.editing = false;
  item.editText = item.text;
}
async function saveAnnouncementEdit(item) {
  const text = String(item.editText || "").trim();
  if (!text) { notify("公告内容不能为空"); return; }
  try {
    const updated = await api("/rankings/admin/announcements/" + item.id, {
      method: "PATCH",
      body: jsonBody({ text, publishAt: toInstant(item.publishAtInput), unpublishAt: toInstant(item.unpublishAtInput) })
    });
    const normalised = normaliseAnnouncements([updated])[0];
    announcements.value = announcements.value.map((row) => row.id === updated.id ? normalised : row);
    notify("公告内容已更新");
  } catch (failure) { notify(failure.message); }
}

function openReward(reward = null) {
  rewardForm.value = reward ? { id: reward.id, name: reward.name, requiredPoints: reward.requiredPoints, stock: reward.stock, expectedStock: reward.stock, enabled: reward.enabled, imageUrl: reward.imageUrl || "" } : emptyRewardForm();
  rewardImage.value = null; rewardPreview.value = reward?.imageUrl || ""; rewardOpen.value = true;
}
function closeReward() { rewardOpen.value = false; rewardImage.value = null; rewardPreview.value = ""; }
function chooseRewardImage(event) {
  const file = event.target.files?.[0] || null; rewardImage.value = file;
  if (file) rewardPreview.value = URL.createObjectURL(file);
}
async function saveReward() {
  rewardSaving.value = true;
  try {
    const body = new FormData();
    body.set("stock", String(rewardForm.value.stock));
    if (rewardForm.value.id) body.set("expectedStock", String(rewardForm.value.expectedStock));
    body.set("name", rewardForm.value.name); body.set("requiredPoints", String(rewardForm.value.requiredPoints)); body.set("enabled", String(rewardForm.value.enabled));
    if (rewardImage.value) body.set("image", rewardImage.value);
    const path = rewardForm.value.id ? `/rankings/admin/rewards/${rewardForm.value.id}` : "/rankings/admin/rewards";
    await api(path, { method: rewardForm.value.id ? "PUT" : "POST", body });
    if (canRewardsRead.value) rewards.value = await api("/rankings/admin/rewards");
    closeReward(); notify(rewardForm.value.id ? "奖品已更新" : "奖品已添加");
  } catch (failure) {
    if (failure.status === 409 && canRewardsRead.value) {
      try { rewards.value = await api("/rankings/admin/rewards"); } catch (_) { /* Keep the original conflict visible. */ }
    }
    notify(failure.message);
  }
  finally { rewardSaving.value = false; }
}
async function deleteReward(reward) {
  if (!await showConfirm(`确定删除奖品“${reward.name}”吗？历史兑换记录会保留。`, { title: "删除奖品", confirmText: "确认删除", danger: true })) return;
  try { await api(`/rankings/admin/rewards/${reward.id}`, { method: "DELETE" }); rewards.value = rewards.value.filter((item) => item.id !== reward.id); notify("奖品已删除"); }
  catch (failure) { notify(failure.message); }
}

function openRedemption() {
  redemptionForm.value = { studentId: rows.value[0]?.studentId || "", rewardId: enabledRewards.value[0]?.id || "" }; redemptionOpen.value = true;
}
async function createRedemption() {
  redemptionSaving.value = true;
  try {
    await api("/rankings/admin/redemptions", { method: "POST", body: jsonBody({ studentId: redemptionForm.value.studentId, rewardId: Number(redemptionForm.value.rewardId) }) });
    if (canRedemptionsRead.value) redemptions.value = await api("/rankings/admin/redemptions");
    board.value = await api("/rankings/admin/board");
    if (canRewardsRead.value) rewards.value = await api("/rankings/admin/rewards");
    redemptionOpen.value = false; notify("兑换已登记，等待老师发放");
  } catch (failure) { notify(failure.message); }
  finally { redemptionSaving.value = false; }
}
async function setFulfilled(item, fulfilled) {
  try {
    const updated = await api(`/rankings/admin/redemptions/${item.id}/fulfillment`, { method: "PATCH", body: jsonBody({ fulfilled }) });
    redemptions.value = redemptions.value.map((row) => row.id === updated.id ? updated : row); notify(fulfilled ? "已标记为已发放" : "已恢复为待发放");
  } catch (failure) { notify(failure.message); }
}
async function copyShareLink() { await writeClipboard(shareUrl.value); notify("学生排行榜链接已复制"); shareOpen.value = false; }
async function openStudentPassword(row) {
  passwordOpen.value = true; passwordStudent.value = row; passwordLoading.value = true;
  passwordVisible.value = false; passwordError.value = ""; recoveredPassword.value = ""; resetPasswordValue.value = "";
  try {
    const value = await api(`/rankings/admin/students/${encodeURIComponent(row.studentId)}/password`);
    recoveredPassword.value = value?.password || "";
  } catch (failure) { passwordError.value = failure.message || "密码读取失败"; }
  finally { passwordLoading.value = false; }
}
function closeStudentPassword() {
  if (passwordSaving.value) return;
  passwordOpen.value = false; passwordStudent.value = null; recoveredPassword.value = ""; resetPasswordValue.value = ""; passwordError.value = "";
}
async function resetStudentPassword() {
  if (!passwordStudent.value || resetPasswordValue.value.length < 6) { passwordError.value = "新密码至少 6 个字符"; return; }
  passwordSaving.value = true; passwordError.value = "";
  try {
    const value = await api(`/rankings/admin/students/${encodeURIComponent(passwordStudent.value.studentId)}/password`, { method: "PUT", body: jsonBody({ password: resetPasswordValue.value }) });
    recoveredPassword.value = value.password || ""; passwordVisible.value = true; resetPasswordValue.value = ""; notify("学生密码已重置");
  } catch (failure) { passwordError.value = failure.message || "密码重置失败"; }
  finally { passwordSaving.value = false; }
}
function openPointsImport() { if (!pointsImporting.value) pointImportInput.value?.click(); }
async function importPointFiles(event) {
  const files = Array.from(event.target.files || []);
  event.target.value = "";
  if (!files.length) return;
  const invalid = files.find((file) => !file.name.toLowerCase().endsWith(".xlsx") || file.size > 10 * 1024 * 1024);
  if (invalid) { notify("仅支持不超过 10 MB 的 .xlsx 文件"); return; }
  pointsImporting.value = true; pointsImportSummary.value = null;
  try {
    const body = new FormData();
    body.set("campId", "spreadsheet-points"); body.set("campName", "表格积分");
    files.forEach((file) => body.append("files", file));
    const result = await api("/rankings/admin/imports/xlsx", { method: "POST", body });
    pointsImportSummary.value = result;
    if (canBoardRead.value) board.value = await api("/rankings/admin/board");
    const message = `导入 ${result.receivedRows} 行，更新 ${result.changedRows} 行，未变化 ${result.unchangedRows} 行`;
    notify(result.rejectedRows ? `${message}，失败 ${result.rejectedRows} 行` : message);
  } catch (failure) { notify(failure.message || "积分导入失败"); }
  finally { pointsImporting.value = false; }
}
async function openPoints(row) {
  pointsStudent.value = row; pointsTarget.value = String(row.availablePoints ?? row.totalPoints ?? 0);
  pointsReason.value = ""; pointsError.value = ""; pointsHistory.value = []; pointsHistoryError.value = "";
  pointsOpen.value = true; pointsHistoryLoading.value = true;
  try { pointsHistory.value = await api(`/rankings/admin/students/${encodeURIComponent(row.studentId)}/point-adjustments`); }
  catch (failure) { pointsHistoryError.value = failure.message || "调整记录读取失败"; }
  finally { pointsHistoryLoading.value = false; }
}
function closePoints() {
  if (pointsSaving.value) return;
  pointsOpen.value = false; pointsStudent.value = null; pointsError.value = "";
  pointsHistory.value = []; pointsHistoryError.value = ""; pointsHistoryLoading.value = false;
}
async function savePoints() {
  const target = Number(pointsTarget.value);
  if (!pointsStudent.value || !Number.isInteger(target) || target < 0 || target > 999999999) { pointsError.value = "目标积分必须是 0 至 999999999 的整数"; return; }
  if (!pointsReason.value.trim()) { pointsError.value = "请填写调整原因"; return; }
  pointsSaving.value = true; pointsError.value = "";
  try {
    await api(`/rankings/admin/students/${encodeURIComponent(pointsStudent.value.studentId)}/points`, { method: "PUT", body: jsonBody({ campId: pointsStudent.value.campId, classId: pointsStudent.value.classId, targetPoints: target, reason: pointsReason.value.trim() }) });
    board.value = await api("/rankings/admin/board");
    pointsOpen.value = false; pointsStudent.value = null; notify("学生积分已更新，排名已刷新");
  } catch (failure) { pointsError.value = failure.message || "积分调整失败"; }
  finally { pointsSaving.value = false; }
}
async function copyStudentPassword() { await writeClipboard(recoveredPassword.value); notify("学生密码已复制"); }
function closeOnEscape(event) { if (event.key === "Escape") { shareOpen.value = false; closeReward(); redemptionOpen.value = false; closeStudentPassword(); closePoints(); } }

onMounted(() => { document.addEventListener("keydown", closeOnEscape); activeTab.value = firstAllowedTab(); loadAll(); });
onBeforeUnmount(() => document.removeEventListener("keydown", closeOnEscape));
</script>

<template>
  <AdminLayout page-title="学生排名" active-page="rankings" content-class="admin-main--rankings">
    <div class="admin-page-heading ranking-page-heading">
      <div><h1>学生排名</h1><p>管理全量积分排名、排行榜公告、奖品与发放记录</p></div>
      <div class="ranking-heading-actions">
        <input ref="pointImportInput" hidden type="file" accept=".xlsx,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" multiple @change="importPointFiles">
        <button v-if="canImportPoints" class="button button-quiet" type="button" :disabled="pointsImporting" @click="openPointsImport"><Upload :size="15"/>{{ pointsImporting ? '导入中' : '导入积分' }}</button>
        <button v-if="canRefresh" class="button button-quiet" type="button" :disabled="loading" @click="loadAll"><RefreshCw :class="{ 'spin-icon': loading }" :size="15"/>刷新</button>
        <button v-if="canShare" class="button button-primary" type="button" @click="shareOpen = true"><Share2 :size="15"/>分享</button>
      </div>
    </div>
    <div v-if="error" class="notice notice-error">{{ error }}</div>
    <div v-if="pointsImportSummary" class="notice" :class="pointsImportSummary.rejectedRows ? 'notice-error' : 'notice-success'">
      导入 {{ pointsImportSummary.receivedRows }} 行，更新 {{ pointsImportSummary.changedRows }} 行，未变化 {{ pointsImportSummary.unchangedRows }} 行<span v-if="pointsImportSummary.rejectedRows">，失败 {{ pointsImportSummary.rejectedRows }} 行：{{ pointsImportSummary.errors?.[0]?.message }}</span>
    </div>

    <nav class="ranking-workspace-tabs" aria-label="排行榜管理功能">
      <button v-if="canBoardRead" type="button" :class="{ active: activeTab === 'rankings' }" @click="activeTab = 'rankings'"><Trophy :size="17"/>全量排名</button>
      <button v-if="canRewardsTab" type="button" :class="{ active: activeTab === 'rewards' }" @click="activeTab = 'rewards'"><Gift :size="17"/>奖品管理 <span v-if="canRewardsRead">{{ rewards.length }}</span></button>
      <button v-if="canRedemptionsTab" type="button" :class="{ active: activeTab === 'redemptions' }" @click="activeTab = 'redemptions'"><PackageCheck :size="17"/>兑换记录 <span v-if="canRedemptionsRead && pendingCount">{{ pendingCount }}</span></button>
      <button v-if="canAnnouncementsTab" type="button" :class="{ active: activeTab === 'announcements' }" @click="activeTab = 'announcements'"><Megaphone :size="17"/>公告管理 <span v-if="canAnnouncementsRead">{{ announcements.length }}</span></button>
    </nav>

    <template v-if="activeTab === 'rankings'">


      <section v-if="board" class="ranking-admin-summary" aria-label="排名概览">
        <article><span class="ranking-summary-icon ranking-summary-gold"><Trophy :size="20"/></span><div><strong>全量榜</strong><small>全部营期、全部班级</small></div></article>
        <article><span class="ranking-summary-icon ranking-summary-blue"><UsersRound :size="20"/></span><div><strong>{{ summary.studentCount }}</strong><small>学员总数</small></div></article>
        <article><span class="ranking-summary-icon ranking-summary-teal">Σ</span><div><strong>{{ numberText(summary.availablePoints) }}</strong><small>当前积分总计</small></div></article>
        <article><span class="ranking-summary-icon ranking-summary-gray">Ø</span><div><strong>{{ numberText(summary.averagePoints) }}</strong><small>平均当前积分</small></div></article>
      </section>

      <section class="admin-panel ranking-list-panel">
        <div class="panel-heading ranking-list-heading ranking-toolbar"><div><h2>全部学员排名</h2><small>{{ searchText ? `找到 ${filteredRows.length} 名学员` : `共 ${summary.studentCount} 名学员` }}</small></div><label class="ranking-search"><Search :size="16"/><input v-model="searchText" type="search" placeholder="搜索学员姓名或 ID" aria-label="搜索学员姓名或 ID"></label></div>
        <div v-if="loading && !board" class="ranking-admin-state"><RefreshCw class="spin-icon" :size="22"/><span>正在加载排名</span></div>
        <div v-else-if="board && !filteredRows.length" class="ranking-admin-state"><Search :size="23"/><span>没有找到匹配的学员</span></div>
        <div v-else-if="board" class="ranking-admin-table-wrap">
          <table class="ranking-admin-table"><thead><tr><th>名次</th><th>学员</th><th>所属班级</th><th>当前积分</th><th>积分构成</th><th>正确率</th><th>等级</th><th>趋势</th><th v-if="canPointsEdit">积分调整</th><th v-if="auth.user?.admin">密码</th></tr></thead>
            <tbody><tr v-for="row in filteredRows" :key="row.studentId">
              <td><span class="ranking-number" :class="`rank-${Math.min(row.rank, 4)}`">{{ row.rank }}</span></td>
              <td><div class="ranking-student"><img class="ranking-student-avatar" src="/favicon-dog-20260913.png" alt="" width="38" height="38"><span><strong>{{ row.studentName }}</strong><small>ID {{ row.studentId }}</small></span></div></td>
              <td><span class="ranking-class-name">{{ row.className || '—' }}</span></td><td><strong class="ranking-total-points">{{ numberText(row.availablePoints ?? row.totalPoints) }}</strong></td>
              <td><div class="ranking-score-parts"><span>课程 {{ row.totalPoints }}</span><span v-if="row.adjustmentPoints">调整 {{ row.adjustmentPoints > 0 ? '+' : '' }}{{ row.adjustmentPoints }}</span><span v-if="row.spentPoints">兑换 -{{ row.spentPoints }}</span></div></td>
              <td>{{ Number(row.accuracyRate || 0).toFixed(1) }}%</td><td><span class="ranking-level" :class="`ranking-level-${row.level}`">{{ row.levelName }}</span></td>
              <td><span class="ranking-trend" :class="`ranking-trend-${rankingTrendView(row).direction}`" :title="rankingTrendView(row).title"><ArrowUp v-if="rankingTrendView(row).direction === 'up'" :size="14"/><ArrowDown v-else-if="rankingTrendView(row).direction === 'down'" :size="14"/><Minus v-else :size="14"/>{{ rankingTrendView(row).direction === 'same' ? '' : Math.abs(row.rankChange) }}</span></td>
              <td v-if="canPointsEdit"><button class="button button-quiet button-small" type="button" @click="openPoints(row)"><Pencil :size="14"/>调整积分</button></td><td v-if="auth.user?.admin"><button class="button button-quiet button-small" type="button" @click="openStudentPassword(row)"><Eye :size="14"/>查看密码</button></td>
            </tr></tbody>
          </table>
        </div>
      </section>
    </template>

    <template v-else-if="activeTab === 'announcements'">
      <section v-if="canAnnouncementsCreate" class="admin-panel ranking-announcement-panel">
        <div class="ranking-announcement-description"><Megaphone :size="19"/><span><strong>学生端公告</strong><small>发布后会显示在公开排行榜顶部，最多 500 字；可立即发布或设置定时上线。</small></span></div>
        <div class="ranking-announcement-form-row">
          <textarea v-model="announcementDraft" maxlength="500" rows="2" placeholder="例如：本周五积分商城开放兑换，请合理安排积分。"></textarea>
          <div class="ranking-announcement-schedules">
            <label class="ranking-announcement-schedule"><CalendarClock :size="16"/><span>上线时间（可选）</span><input v-model="announcementPublishAt" type="datetime-local"></label>
            <label class="ranking-announcement-schedule"><CalendarClock :size="16"/><span>下线时间（可选）</span><input v-model="announcementUnpublishAt" type="datetime-local"></label>
          </div>
          <button class="button button-primary" type="button" :disabled="savingAnnouncement || !announcementDraft.trim()" @click="saveAnnouncement">{{ savingAnnouncement ? "发布中" : "发布公告" }}</button>
        </div>
      </section>

      <section v-if="canAnnouncementsRead" class="admin-panel ranking-announcement-history">
      <div class="panel-heading ranking-toolbar"><div><h2>公告管理</h2><small>共 {{ announcements.length }} 条，支持手动上线、下线和定时发布</small></div><CalendarClock :size="19" class="ranking-history-heading-icon"/></div>
      <div v-if="!announcements.length" class="ranking-admin-state"><Megaphone :size="25"/><span>还没有发布过公告</span></div>
      <div v-else class="ranking-announcement-list">
        <article v-for="item in announcements" :key="item.id" class="ranking-announcement-row">
          <div class="ranking-announcement-copy">
            <template v-if="item.editing && canAnnouncementsEdit">
              <textarea v-model="item.editText" class="ranking-announcement-edit-text" maxlength="500" rows="2"></textarea>
              <div class="ranking-announcement-edit-actions"><button class="button button-primary" type="button" @click="saveAnnouncementEdit(item)">保存内容</button><button class="button button-quiet" type="button" @click="cancelAnnouncementEdit(item)">取消</button></div>
            </template>
            <template v-else>
              <p>{{ item.text }}</p>
              <div class="ranking-announcement-meta"><span class="ranking-status" :class="'ranking-announcement-status-' + item.status.toLowerCase()">{{ announcementStatusLabel(item.status) }}</span><span v-if="item.publishAt">上线 {{ formatDateTime(item.publishAt) }}</span><span v-if="item.unpublishAt">下线 {{ formatDateTime(item.unpublishAt) }}</span><span>创建于 {{ formatDateTime(item.createdAt) }}</span></div>
            </template>
          </div>
          <div class="ranking-announcement-controls">
            <div v-if="canAnnouncementsEdit" class="ranking-announcement-time-editor">
              <label>上线 <input v-model="item.publishAtInput" type="datetime-local"></label>
              <label>下线 <input v-model="item.unpublishAtInput" type="datetime-local"></label>
              <button class="button button-quiet ranking-announcement-save" type="button" @click="saveAnnouncementSchedule(item)"><Save :size="14"/>保存时间</button>
            </div>
            <div class="ranking-row-actions"><button v-if="canAnnouncementsEdit && !item.editing" class="button button-quiet ranking-announcement-action" type="button" @click="startAnnouncementEdit(item)"><Pencil :size="14"/>编辑</button><button v-if="canAnnouncementsStatus && item.status === 'ONLINE' && !item.editing" class="button button-quiet ranking-announcement-action" type="button" @click="setAnnouncementOnline(item, false)"><CircleOff :size="14"/>下线</button><button v-else-if="canAnnouncementsStatus && !item.editing" class="button button-quiet ranking-announcement-action" type="button" @click="setAnnouncementOnline(item, true)"><CheckCircle2 :size="14"/>{{ item.status === 'SCHEDULED' ? '立即上线' : '上线' }}</button></div>
          </div>
        </article>
      </div>
      </section>
    </template>

    <section v-else-if="activeTab === 'rewards'" class="admin-panel ranking-list-panel">
      <div class="panel-heading ranking-toolbar"><div><h2>奖品管理</h2><small>维护奖品、图片、兑换积分和剩余库存</small></div><button v-if="canRewardsCreate" class="button button-primary" type="button" @click="openReward()"><Plus :size="15"/>添加奖品</button></div>
      <div v-if="!canRewardsRead" class="ranking-admin-state"><Gift :size="25"/><span>当前账号没有查看奖品的权限</span></div>
      <div v-else-if="!rewards.length" class="ranking-admin-state"><Gift :size="25"/><span>还没有奖品</span></div>
      <div v-else class="ranking-reward-list">
        <article v-for="reward in rewards" :key="reward.id" class="ranking-reward-row">
          <div class="ranking-reward-image"><img v-if="reward.imageUrl" :src="reward.imageUrl" :alt="reward.name"><Gift v-else :size="26"/></div>
          <div class="ranking-reward-copy"><strong>{{ reward.name }}</strong><span>{{ numberText(reward.requiredPoints) }} 积分 · 剩余 {{ numberText(reward.stock) }} 件</span></div>
          <span class="ranking-status" :class="reward.enabled ? 'is-enabled' : 'is-disabled'">{{ !reward.enabled ? '已停用' : reward.stock > 0 ? '兑换中' : '已兑完' }}</span>
          <div class="ranking-row-actions"><button v-if="canRewardsEdit" class="icon-button" type="button" title="编辑奖品" @click="openReward(reward)"><Pencil :size="16"/></button><button v-if="canRewardsDelete" class="icon-button ranking-danger-button" type="button" title="删除奖品" @click="deleteReward(reward)"><Trash2 :size="16"/></button></div>
        </article>
      </div>
    </section>

    <section v-else-if="activeTab === 'redemptions'" class="admin-panel ranking-list-panel">
      <div class="panel-heading ranking-toolbar"><div><h2>兑换与发放</h2><small v-if="canRedemptionsRead">{{ pendingCount }} 条待发放 · 共 {{ redemptions.length }} 条记录</small></div><div class="ranking-toolbar-actions"><label v-if="canRedemptionsRead" class="ranking-search"><Search :size="16"/><input v-model="redemptionSearch" type="search" placeholder="搜索学员或奖品" aria-label="搜索兑换记录"></label><button v-if="canRedemptionsCreate" class="button button-primary" type="button" :disabled="!rows.length || !enabledRewards.length" @click="openRedemption"><Plus :size="15"/>登记兑换</button></div></div>
      <div v-if="!canRedemptionsRead" class="ranking-admin-state"><PackageCheck :size="25"/><span>当前账号没有查看兑换记录的权限</span></div>
      <div v-else-if="!redemptions.length" class="ranking-admin-state"><PackageCheck :size="25"/><span>暂无兑换记录</span></div>
      <div v-else class="ranking-admin-table-wrap"><table class="ranking-admin-table ranking-redemption-table"><thead><tr><th>学员</th><th>奖品</th><th>扣除积分</th><th>兑换时间</th><th>发放状态</th><th>操作</th></tr></thead>
        <tbody><tr v-for="item in filteredRedemptions" :key="item.id"><td><strong>{{ item.studentName }}</strong><small class="ranking-cell-note">ID {{ item.studentId }}</small></td><td>{{ item.rewardName }}</td><td>{{ numberText(item.pointsSpent) }}</td><td>{{ formatDateTime(item.redeemedAt) }}</td><td><span class="ranking-status" :class="item.status === 'FULFILLED' ? 'is-enabled' : 'is-pending'">{{ item.status === 'FULFILLED' ? '已发放' : '待发放' }}</span><small v-if="item.fulfilledAt" class="ranking-cell-note">{{ formatDateTime(item.fulfilledAt) }}</small></td><td><button v-if="canRedemptionsFulfill" class="button button-quiet ranking-fulfill-button" type="button" @click="setFulfilled(item, item.status !== 'FULFILLED')"><CheckCircle2 :size="14"/>{{ item.status === 'FULFILLED' ? '恢复待发放' : '确认已发放' }}</button></td></tr></tbody>
      </table></div>
    </section>

    <div v-if="passwordOpen && passwordStudent" class="ranking-share-backdrop ui-dialog-backdrop" @click.self="closeStudentPassword">
      <section class="ranking-share-dialog ui-modal ranking-form-dialog" role="dialog" aria-modal="true" aria-label="排行榜管理"><header><div><span><Eye :size="18"/></span><div><h2>学生密码</h2><p>{{ passwordStudent.studentName }} · ID {{ passwordStudent.studentId }}</p></div></div><button class="icon-button" type="button" title="关闭" @click="closeStudentPassword"><X :size="17"/></button></header>
        <div class="ranking-password-body"><p class="ranking-password-warning">仅系统管理员可查看。每次查看和重置都会写入操作日志。</p><div v-if="passwordLoading" class="ranking-admin-state"><RefreshCw class="spin-icon" :size="20"/><span>正在读取密码</span></div><template v-else><div v-if="recoveredPassword" class="ranking-password-value"><span>{{ passwordVisible ? recoveredPassword : '••••••••' }}</span><button class="icon-button" type="button" :title="passwordVisible ? '隐藏密码' : '显示密码'" @click="passwordVisible = !passwordVisible"><EyeOff v-if="passwordVisible" :size="16"/><Eye v-else :size="16"/></button><button class="button button-quiet button-small" type="button" @click="copyStudentPassword"><Copy :size="14"/>复制</button></div><p v-else class="ranking-password-empty">现有密码尚未保存为可恢复密文。学生成功登录一次后即可查看，也可以在下方直接重置。</p><p v-if="passwordError" class="notice notice-error">{{ passwordError }}</p><label class="ranking-password-reset"><span>重置新密码</span><input v-model="resetPasswordValue" type="text" minlength="6" maxlength="72" placeholder="请输入 6-72 个字符"><button class="button button-primary" type="button" :disabled="passwordSaving || resetPasswordValue.length < 6" @click="resetStudentPassword">{{ passwordSaving ? '重置中' : '重置密码' }}</button></label></template></div>
      </section>
    </div>
    <div v-if="pointsOpen && canPointsEdit && pointsStudent" class="ranking-share-backdrop ui-dialog-backdrop" @click.self="closePoints">
      <form class="ranking-share-dialog ui-modal ranking-form-dialog" role="dialog" aria-modal="true" aria-label="排行榜管理" @submit.prevent="savePoints"><header><div><span><Pencil :size="18"/></span><div><h2>调整学生积分</h2><p>{{ pointsStudent.studentName }} · 当前积分 {{ pointsStudent.availablePoints ?? pointsStudent.totalPoints }}</p></div></div><button class="icon-button" type="button" title="关闭" @click="closePoints"><X :size="17"/></button></header>
        <div class="ranking-form-body"><label><span>调整后积分</span><input v-model.number="pointsTarget" type="number" min="0" max="999999999" required></label><label><span>调整原因</span><textarea v-model.trim="pointsReason" maxlength="255" rows="3" required placeholder="例如：课堂表现奖励 50 分"></textarea><small>保存后，管理端和学生排行榜统一显示该积分；后续兑换会继续扣减。</small></label><p v-if="pointsError" class="notice notice-error">{{ pointsError }}</p>
          <section class="ranking-point-history"><header><div><strong>积分调整记录</strong><small>记录每次调整前后积分、原因和操作人</small></div><span>{{ pointsHistory.length }} 条</span></header>
            <div v-if="pointsHistoryLoading" class="ranking-point-history-state"><RefreshCw class="spin-icon" :size="16"/>正在读取调整记录</div>
            <div v-else-if="pointsHistoryError" class="ranking-point-history-state is-error">{{ pointsHistoryError }}</div>
            <div v-else-if="!pointsHistory.length" class="ranking-point-history-state">暂无手动调整记录</div>
            <ol v-else><li v-for="item in pointsHistory" :key="item.id"><div class="ranking-point-history-balance"><strong>{{ numberText(item.availablePointsBefore) }} → {{ numberText(item.availablePointsAfter) }}</strong><span :class="item.delta >= 0 ? 'is-positive' : 'is-negative'">{{ item.delta >= 0 ? '+' : '' }}{{ numberText(item.delta) }}</span></div><p>{{ item.reason }}</p><small>{{ formatDateTime(item.createdAt) }} · {{ item.actor }}</small></li></ol>
          </section>
        </div>
        <footer><button class="button button-quiet" type="button" @click="closePoints">取消</button><button class="button button-primary" type="submit" :disabled="pointsSaving">{{ pointsSaving ? '保存中' : '保存积分' }}</button></footer>
      </form>
    </div>
    <div v-if="rewardOpen && (rewardForm.id ? canRewardsEdit : canRewardsCreate)" class="ranking-share-backdrop ui-dialog-backdrop" @click.self="closeReward">
      <form class="ranking-share-dialog ui-modal ranking-form-dialog" role="dialog" aria-modal="true" aria-label="排行榜管理" @submit.prevent="saveReward"><header><div><span><Gift :size="18"/></span><div><h2>{{ rewardForm.id ? '编辑奖品' : '添加奖品' }}</h2><p>设置展示信息与兑换积分</p></div></div><button class="icon-button" type="button" title="关闭" @click="closeReward"><X :size="17"/></button></header>
        <div class="ranking-form-body"><label><span>奖品名称</span><input v-model.trim="rewardForm.name" required maxlength="100" placeholder="例如：编程主题笔记本"></label><label><span>所需积分</span><input v-model.number="rewardForm.requiredPoints" required type="number" min="1" max="999999999"></label><label><span>剩余库存</span><input v-model.number="rewardForm.stock" required type="number" min="0" max="999999999" step="1"><small>每次兑换扣减 1 件；0 件时无法兑换。</small></label><label class="ranking-image-field"><span>奖品图片</span><div><div class="ranking-image-preview"><img v-if="rewardPreview" :src="rewardPreview" alt="奖品预览"><ImagePlus v-else :size="25"/></div><input type="file" accept="image/png,image/jpeg,image/webp,image/gif" @change="chooseRewardImage"><small>PNG、JPG、WebP 或 GIF，最大 2 MB</small></div></label><label class="ranking-checkbox"><input v-model="rewardForm.enabled" type="checkbox"><span>允许兑换</span></label></div>
        <footer><button class="button button-quiet" type="button" @click="closeReward">取消</button><button class="button button-primary" type="submit" :disabled="rewardSaving">{{ rewardSaving ? '保存中' : '保存奖品' }}</button></footer>
      </form>
    </div>

    <div v-if="redemptionOpen && canRedemptionsCreate" class="ranking-share-backdrop ui-dialog-backdrop" @click.self="redemptionOpen = false">
      <form class="ranking-share-dialog ui-modal ranking-form-dialog" role="dialog" aria-modal="true" aria-label="排行榜管理" @submit.prevent="createRedemption"><header><div><span><Award :size="18"/></span><div><h2>登记奖品兑换</h2><p>确认后立即占用学员积分，记录默认为待发放</p></div></div><button class="icon-button" type="button" title="关闭" @click="redemptionOpen = false"><X :size="17"/></button></header>
        <div class="ranking-form-body"><label><span>兑换学员</span><select v-model="redemptionForm.studentId" required><option v-for="row in rows" :key="row.studentId" :value="row.studentId">{{ row.studentName }} · 第 {{ row.rank }} 名 · 可用 {{ row.availablePoints ?? row.totalPoints }} 分</option></select></label><label><span>兑换奖品</span><select v-model="redemptionForm.rewardId" required><option v-for="reward in enabledRewards" :key="reward.id" :value="reward.id">{{ reward.name }} · {{ reward.requiredPoints }} 分 · 剩余 {{ reward.stock }} 件</option></select></label><div class="ranking-redemption-check" :class="{ invalid: selectedReward && !redemptionAllowed }"><span>当前可用积分</span><strong>{{ numberText(availablePoints) }}</strong><small v-if="selectedReward">兑换后剩余 {{ numberText(Math.max(0, availablePoints - selectedReward.requiredPoints)) }} 分</small></div></div>
        <footer><button class="button button-quiet" type="button" @click="redemptionOpen = false">取消</button><button class="button button-primary" type="submit" :disabled="redemptionSaving || !redemptionAllowed">{{ redemptionSaving ? '登记中' : '确认兑换' }}</button></footer>
      </form>
    </div>

    <div v-if="shareOpen && canShare" class="ranking-share-backdrop ui-dialog-backdrop" @click.self="shareOpen = false"><section class="ranking-share-dialog ui-modal"><header><div><span><Share2 :size="18"/></span><div><h2>分享学生排行榜</h2><p>当前账号名下全部学员总榜</p></div></div><button class="icon-button" type="button" title="关闭" @click="shareOpen = false"><X :size="17"/></button></header><div class="ranking-share-content"><label>分享链接</label><input :value="shareUrl" readonly @focus="$event.target.select()"></div><footer><a class="button button-quiet" :href="shareUrl" target="_blank" rel="noopener"><ExternalLink :size="15"/>打开预览</a><button class="button button-primary" type="button" @click="copyShareLink"><Copy :size="15"/>复制链接</button></footer></section></div>
  </AdminLayout>
</template>
