<script setup>
import { showConfirm } from "../uiDialogs.js";
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from "vue";
import {
  ArrowDown, ArrowUp, Award, CalendarClock, CheckCircle2, CircleOff, Copy, ExternalLink, Gift, ImagePlus,
  Eye, EyeOff, Ellipsis, Megaphone, Minus, PackageCheck, Pencil, Plus, RefreshCw, Save, Search, Share2, Upload,
  Trash2, Trophy, UsersRound, X, GraduationCap, RotateCcw
} from "@lucide/vue";
import RankingAftercare from "../components/RankingAftercare.vue";
import RankingOperations from "../components/RankingOperations.vue";
import AdminLayout from "../components/AdminLayout.vue";
import { api, jsonBody, notify, writeClipboard } from "../api";
import { auth, hasPermission } from "../auth";
import { rankingShareUrl, rankingSummary, rankingTrendView } from "../rankingAdmin.js";
import { formatDateTime } from "../utils";

const rowMenuStudent = ref(null);
const rowMenuStyle = ref({});
const rowMenuTrigger = ref(null);
function closeRowMenu() { rowMenuStudent.value = null; }
function positionRowMenu() {
  if (!rowMenuStudent.value || !rowMenuTrigger.value) return;
  const rect = rowMenuTrigger.value.getBoundingClientRect();
  if (rect.bottom < 0 || rect.top > window.innerHeight || rect.right < 0 || rect.left > window.innerWidth) { closeRowMenu(); return; }
  const menu = document.querySelector(".ranking-row-menu");
  const height = menu?.offsetHeight || 96;
  rowMenuStyle.value = {
    left: `${Math.max(8, Math.min(rect.right - 168, window.innerWidth - 176))}px`,
    top: `${rect.bottom + height + 6 > window.innerHeight - 8 ? Math.max(8, rect.top - height - 6) : rect.bottom + 6}px`
  };
}
function dismissRowMenu(event) { if (!event.target.closest?.(".ranking-row-menu, .ranking-more-button")) closeRowMenu(); }
async function toggleRowMenu(row, event) {
  if (rowMenuStudent.value?.studentId === row.studentId) { closeRowMenu(); return; }
  rowMenuTrigger.value = event.currentTarget;
  rowMenuStudent.value = row;
  await nextTick();
  positionRowMenu();
  document.querySelector(".ranking-row-menu button")?.focus({ preventScroll: true });
}
function runRowAction(action) { const row = rowMenuStudent.value; closeRowMenu(); if (row) action(row); }
const board = ref(null);
const accountStatuses = ref({});
const graduatesOpen = ref(false);
const graduates = ref([]);
const graduatesLoading = ref(false);
const graduatesError = ref("");
const graduatesSearch = ref("");
const graduationBusy = ref("");
const canGraduatesRead = computed(() => hasPermission("rankings.graduates.read"));
const canGraduate = computed(() => hasPermission("rankings.students.graduate"));
const canRestore = computed(() => hasPermission("rankings.students.restore"));
const canRankingTab = computed(() => canBoardRead.value || canGraduatesRead.value);
const filteredGraduates = computed(() => {
  const query = graduatesSearch.value.trim().toLowerCase();
  return graduates.value.filter(row => !query || `${row.studentName} ${row.studentId}`.toLowerCase().includes(query));
});
async function loadGraduates() {
  if (!canGraduatesRead.value) return;
  graduatesLoading.value = true; graduatesError.value = "";
  try { graduates.value = await api("/rankings/admin/graduates"); }
  catch (failure) { graduatesError.value = failure.message || "已毕业学员加载失败"; }
  finally { graduatesLoading.value = false; }
}
async function openGraduates() { graduatesOpen.value = true; graduatesSearch.value = ""; await loadGraduates(); }
async function setGraduated(row, graduated) {
  if (graduationBusy.value) return;
  if (!await showConfirm(graduated
    ? `确认将“${row.studentName}”标记为毕业？毕业后不参与教师端和学生端排名，积分、账号及兑换记录保留，可在“已毕业学员”中恢复。`
    : `确认恢复“${row.studentName}”？恢复后将按当前积分重新参与排名。`,
    { title: graduated ? "确认毕业" : "恢复学员", confirmText: graduated ? "确认毕业" : "确认恢复" })) return;
  graduationBusy.value = row.studentId;
  try {
    await api(`/rankings/admin/students/${encodeURIComponent(row.studentId)}/graduation`, { method: graduated ? "PUT" : "DELETE" });
    await loadAll();
    if (graduatesOpen.value) await loadGraduates();
    notify(graduated ? "已标记毕业，学员已从排名中隐藏" : "已恢复，学员重新参与排名");
  } catch (failure) { notify(failure.message || "操作失败，请重试"); }
  finally { graduationBusy.value = ""; }
}
function closeGraduates() { if (!graduationBusy.value) graduatesOpen.value = false; }
const phoneImportInput = ref(null);
const phonesImporting = ref(false);
const phoneImportSummary = ref(null);
const phoneOpen = ref(false);
const phoneLoading = ref(false);
const phoneSaving = ref(false);
const phoneStudent = ref(null);
const phoneValue = ref("");
const phoneOriginal = ref("");
const phoneError = ref("");
const canImportPhones = computed(() => hasPermission("rankings.accounts.import"));
const canEditPhone = computed(() => hasPermission("rankings.accounts.edit"));
function statusLabel(status) { return status === "ACTIVE" ? "已开通" : status === "PENDING" ? "待开通" : status === "DISABLED" ? "已停用" : "—"; }
async function loadAccountStatuses() {
  if (!canBoardRead.value) return;
  const values = await api("/rankings/admin/students/account-status");
  accountStatuses.value = Object.fromEntries(values.map((item) => [item.studentId, item]));
}
function openPhoneImport() { if (!phonesImporting.value) phoneImportInput.value?.click(); }
async function importPhones(event) {
  const file = event.target.files?.[0]; event.target.value = "";
  if (!file) return;
  if (!file.name.toLowerCase().endsWith(".xlsx") || file.size > 10 * 1024 * 1024) { notify("仅支持不超过 10 MB 的 .xlsx 文件"); return; }
  phonesImporting.value = true; phoneImportSummary.value = null;
  try {
    const body = new FormData(); body.append("file", file);
    phoneImportSummary.value = await api("/rankings/admin/imports/phones", { method: "POST", body });
    await loadAccountStatuses();
    notify(`手机号导入完成，开通 ${phoneImportSummary.value.createdRows} 人，更新 ${phoneImportSummary.value.updatedRows} 人，失败 ${phoneImportSummary.value.rejectedRows} 行`);
  } catch (failure) { notify(failure.message || "手机号导入失败"); }
  finally { phonesImporting.value = false; }
}
async function openStudentPhone(row) {
  phoneStudent.value = row; phoneValue.value = ""; phoneOriginal.value = ""; phoneError.value = "";
  phoneOpen.value = true; phoneLoading.value = true;
  try {
    const value = await api(`/rankings/admin/students/${encodeURIComponent(row.studentId)}/phone`);
    if (!phoneOpen.value || phoneStudent.value?.studentId !== row.studentId) return;
    phoneValue.value = value.phone; phoneOriginal.value = value.phone;
  } catch (failure) { phoneError.value = failure.message || "手机号读取失败"; }
  finally { phoneLoading.value = false; }
}
function closeStudentPhone() { if (!phoneSaving.value && !phoneLoading.value) { phoneOpen.value = false; phoneStudent.value = null; } }
async function saveStudentPhone() {
  if (!/^1\d{10}$/.test(phoneValue.value)) { phoneError.value = "请输入 11 位有效手机号"; return; }
  phoneSaving.value = true; phoneError.value = "";
  try {
    await api(`/rankings/admin/students/${encodeURIComponent(phoneStudent.value.studentId)}/phone`, { method: "PUT", body: jsonBody({ phone: phoneValue.value, expectedPhone: phoneOriginal.value }) });
    await loadAccountStatuses();
    phoneOpen.value = false; phoneStudent.value = null; notify("手机号已保存，学生请使用新手机号登录");
  } catch (failure) { phoneError.value = failure.message || "手机号保存失败"; }
  finally { phoneSaving.value = false; }
}
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
const importPreview=ref(null);
const importConfirming=ref(false);
async function confirmImport(){if(importConfirming.value)return;importConfirming.value=true;try{pointsImportSummary.value=await api(`/rankings/admin/operations/import-previews/${importPreview.value.previewId}/confirm`,{method:"POST"});importPreview.value=null;await loadAll();}catch(e){notify(e.message);}finally{importConfirming.value=false;}}
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
const extraTabs = computed(()=>({
  aftercare:auth.user?.admin||['rankings.cancellations.read','rankings.recovery.read'].some(hasPermission),
  operations:['rankings.reconciliation.read','rankings.reconciliation.run','rankings.reconciliation.repair','rankings.batches.read'].some(hasPermission),
}));
function firstAllowedTab() {
  if (canRankingTab.value) return "rankings";
  if (canRewardsTab.value) return "rewards";
  if (canRedemptionsTab.value) return "redemptions";
  if (extraTabs.value.aftercare) return "aftercare";
  if (extraTabs.value.operations) return "operations";
  if (canAnnouncementsTab.value) return "announcements";
  return "";
}
function tabAllowed(tab) {
  if(Object.hasOwn(extraTabs.value,tab))return extraTabs.value[tab];
  return tab === "rankings" ? canRankingTab.value : tab === "rewards" ? canRewardsTab.value
    : tab === "redemptions" ? canRedemptionsTab.value : tab === "announcements" ? canAnnouncementsTab.value : false;
}

async function loadAll() {
  loading.value = true; error.value = "";
  try {
    const tasks = [];
    if (canBoardRead.value) tasks.push(loadAccountStatuses());
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
    importPreview.value = await api("/rankings/admin/operations/import-previews", { method: "POST", body });
    return;
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
function closeOnEscape(event) { if (event.key === "Escape") { if (rowMenuStudent.value) { closeRowMenu(); rowMenuTrigger.value?.focus(); return; } closeGraduates(); shareOpen.value = false; closeReward(); redemptionOpen.value = false; closeStudentPassword(); closePoints(); closeStudentPhone(); } }

onMounted(() => { document.addEventListener("keydown", closeOnEscape); document.addEventListener("click", dismissRowMenu); window.addEventListener("resize", closeRowMenu); window.addEventListener("scroll", positionRowMenu, true); activeTab.value = firstAllowedTab(); loadAll(); });
onBeforeUnmount(() => { document.removeEventListener("keydown", closeOnEscape); document.removeEventListener("click", dismissRowMenu); window.removeEventListener("resize", closeRowMenu); window.removeEventListener("scroll", positionRowMenu, true); });
</script>

<template>
  <AdminLayout page-title="学生排名" active-page="rankings" content-class="admin-main--rankings">
    <div class="admin-page-heading ranking-page-heading">
      <div><h1>学生排名</h1><p>管理全量积分排名、排行榜公告、奖品与发放记录</p></div>
      <div class="ranking-heading-actions">
        <input ref="pointImportInput" hidden type="file" accept=".xlsx,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" multiple @change="importPointFiles">
        <button v-if="canImportPoints" class="button button-primary" type="button" :disabled="pointsImporting" @click="openPointsImport"><Upload :size="15"/>{{ pointsImporting ? '导入中' : '导入积分' }}</button>
        <input ref="phoneImportInput" hidden type="file" accept=".xlsx,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" @change="importPhones">
        <button v-if="canImportPhones" class="button button-quiet" type="button" :disabled="phonesImporting" title="A列用户ID、B列姓名、C列手机号；仅更新当前老师名下账号，不改积分" @click="openPhoneImport"><Upload :size="15"/>{{ phonesImporting ? '导入中' : '导入手机号' }}</button>
        <button v-if="canRefresh" class="icon-button toolbar-icon-button" type="button" title="刷新" aria-label="刷新" :disabled="loading" @click="loadAll"><RefreshCw :class="{ 'spin-icon': loading }" :size="18"/></button>
        <button v-if="canShare" class="icon-button toolbar-icon-button" type="button" title="分享" aria-label="分享" @click="shareOpen = true"><Share2 :size="18"/></button>
      </div>
    </div>
    <div v-if="error" class="notice notice-error">{{ error }}</div>
    <div v-if="pointsImportSummary" class="notice" :class="pointsImportSummary.rejectedRows ? 'notice-error' : 'notice-success'">
      导入 {{ pointsImportSummary.receivedRows }} 行，更新 {{ pointsImportSummary.changedRows }} 行，未变化 {{ pointsImportSummary.unchangedRows }} 行<span v-if="pointsImportSummary.rejectedRows">，失败 {{ pointsImportSummary.rejectedRows }} 行：{{ pointsImportSummary.errors?.[0]?.message }}</span>
    </div>

    <div v-if="phoneImportSummary" class="notice" :class="phoneImportSummary.rejectedRows ? 'notice-error' : 'notice-success'" role="status">
      手机号导入 {{ phoneImportSummary.receivedRows }} 行：开通 {{ phoneImportSummary.createdRows }} 人，更新 {{ phoneImportSummary.updatedRows }} 人，未变化 {{ phoneImportSummary.unchangedRows }} 人，失败 {{ phoneImportSummary.rejectedRows }} 行。新账号初始密码为 123456，已有账号密码不变，积分不变。
      <details v-if="phoneImportSummary.errors?.length"><summary>查看失败明细（{{ phoneImportSummary.errors.length }} 行）</summary><ul><li v-for="item in phoneImportSummary.errors" :key="item.rowNumber">第 {{ item.rowNumber }} 行 · ID {{ item.studentId || '缺失' }}：{{ item.message }}</li></ul></details>
    </div>

    <div v-if="importPreview" class="notice"><h3>导入预览 · {{importPreview.rows.length}} 条结果</h3><p>确认后才会更新积分；预览30分钟有效。</p><div style="max-height:320px;overflow:auto"><table class="ranking-admin-table"><thead><tr><th>学员</th><th>课程</th><th>原积分</th><th>新积分</th><th>校验</th></tr></thead><tbody><tr v-for="(r,i) in importPreview.rows" :key="i"><td>{{r.name}} · {{r.studentId}}</td><td>{{r.lesson}}</td><td>{{r.before}}</td><td>{{r.after}}</td><td>{{r.error||'通过'}}</td></tr></tbody></table></div><div class="button-row"><button class="button button-quiet" :disabled="importConfirming" @click="importPreview=null">取消</button><button v-if="hasPermission('rankings.import.confirm')" class="button button-primary" :disabled="importConfirming||importPreview.hasErrors" @click="confirmImport">确认导入</button></div></div>
    <nav class="ranking-workspace-tabs" aria-label="排行榜管理功能">
      <button v-if="canRankingTab" type="button" :class="{ active: activeTab === 'rankings' }" @click="activeTab = 'rankings'"><Trophy :size="17"/>全量排名</button>
      <button v-if="canRewardsTab" type="button" :class="{ active: activeTab === 'rewards' }" @click="activeTab = 'rewards'"><Gift :size="17"/>奖品管理 <span v-if="canRewardsRead">{{ rewards.length }}</span></button>
      <button v-if="canRedemptionsTab" type="button" :class="{ active: activeTab === 'redemptions' }" @click="activeTab = 'redemptions'"><PackageCheck :size="17"/>兑换记录 <span v-if="canRedemptionsRead && pendingCount">{{ pendingCount }}</span></button>
      <button v-if="auth.user?.admin||['rankings.cancellations.read','rankings.recovery.read'].some(hasPermission)" type="button" :class="{active:activeTab==='aftercare'}" @click="activeTab='aftercare'">取消兑换与账号找回</button><button v-if="['rankings.reconciliation.read','rankings.reconciliation.run','rankings.batches.read'].some(hasPermission)" type="button" :class="{active:activeTab==='operations'}" @click="activeTab='operations'">积分对账与导入记录</button>
      <button v-if="canAnnouncementsTab" type="button" :class="{ active: activeTab === 'announcements' }" @click="activeTab = 'announcements'"><Megaphone :size="17"/>公告管理 <span v-if="canAnnouncementsRead">{{ announcements.length }}</span></button>
    </nav>
    <RankingAftercare v-if="activeTab==='aftercare'"/>
    <RankingOperations v-if="activeTab==='operations'" :students="rows"/>

    <template v-if="activeTab === 'rankings'">


      <section v-if="board" class="ranking-admin-summary" aria-label="排名概览">
        <article><span class="ranking-summary-icon ranking-summary-gold"><Trophy :size="20"/></span><div><strong>全量榜</strong><small>全部营期、全部班级</small></div></article>
        <article><span class="ranking-summary-icon ranking-summary-blue"><UsersRound :size="20"/></span><div><strong>{{ summary.studentCount }}</strong><small>学员总数</small></div></article>
        <article><span class="ranking-summary-icon ranking-summary-teal">Σ</span><div><strong>{{ numberText(summary.availablePoints) }}</strong><small>当前积分总计</small></div></article>
        <article><span class="ranking-summary-icon ranking-summary-gray">Ø</span><div><strong>{{ numberText(summary.averagePoints) }}</strong><small>平均当前积分</small></div></article>
      </section>

      <section class="admin-panel ranking-list-panel">
        <div class="panel-heading ranking-list-heading ranking-toolbar"><div><h2>全部学员排名</h2><small>{{ searchText ? `找到 ${filteredRows.length} 名学员` : `共 ${summary.studentCount} 名学员` }}</small></div><div class="ranking-list-header-actions"><button v-if="canGraduatesRead" class="button button-quiet" type="button" @click="openGraduates"><GraduationCap :size="16"/>已毕业学员</button><label v-if="canBoardRead" class="ranking-search"><Search :size="16"/><input v-model="searchText" type="search" placeholder="搜索学员姓名或 ID" aria-label="搜索学员姓名或 ID"></label></div></div>
        <div v-if="loading && !board" class="ranking-admin-state"><RefreshCw class="spin-icon" :size="22"/><span>正在加载排名</span></div>
        <div v-else-if="board && !filteredRows.length" class="ranking-admin-state"><Search :size="23"/><span>没有找到匹配的学员</span></div>
        <div v-else-if="board" class="ranking-admin-table-wrap">
          <table class="ranking-admin-table"><thead><tr><th>名次</th><th>学员</th><th>账号状态</th><th>所属班级</th><th>当前积分</th><th>积分构成</th><th>正确率</th><th>等级</th><th>趋势</th><th v-if="canPointsEdit || canEditPhone || auth.user?.admin" class="ranking-actions-column">操作</th><th v-if="canGraduate" class="ranking-graduation-column">毕业</th></tr></thead>
            <tbody><tr v-for="row in filteredRows" :key="row.studentId">
              <td><span class="ranking-number" :class="`rank-${Math.min(row.rank, 4)}`">{{ row.rank }}</span></td>
              <td><div class="ranking-student"><img class="ranking-student-avatar" src="/favicon-dog-20260913.png" alt="" width="38" height="38"><span><strong>{{ row.studentName }}</strong><small>ID {{ row.studentId }}</small></span></div></td>
              <td><span class="ranking-status" :class="accountStatuses[row.studentId]?.status === 'ACTIVE' ? 'is-enabled' : accountStatuses[row.studentId]?.status === 'DISABLED' ? 'is-disabled' : 'is-pending'">{{ statusLabel(accountStatuses[row.studentId]?.status) }}</span><small v-if="accountStatuses[row.studentId]?.maskedPhone" class="ranking-account-phone">{{ accountStatuses[row.studentId].maskedPhone }}</small></td>
              <td><span class="ranking-class-name">{{ row.className || '—' }}</span></td><td><strong class="ranking-total-points">{{ numberText(row.availablePoints ?? row.totalPoints) }}</strong></td>
              <td><div class="ranking-score-parts"><span>课程 {{ row.totalPoints }}</span><span v-if="row.adjustmentPoints">调整 {{ row.adjustmentPoints > 0 ? '+' : '' }}{{ row.adjustmentPoints }}</span><span v-if="row.spentPoints">兑换 -{{ row.spentPoints }}</span></div></td>
              <td>{{ Number(row.accuracyRate || 0).toFixed(1) }}%</td><td><span class="ranking-level" :class="`ranking-level-${row.level}`">{{ row.levelName }}</span></td>
              <td><span class="ranking-trend" :class="`ranking-trend-${rankingTrendView(row).direction}`" :title="rankingTrendView(row).title"><ArrowUp v-if="rankingTrendView(row).direction === 'up'" :size="14"/><ArrowDown v-else-if="rankingTrendView(row).direction === 'down'" :size="14"/><Minus v-else :size="14"/>{{ rankingTrendView(row).direction === 'same' ? '' : Math.abs(row.rankChange) }}</span></td>
              <td v-if="canPointsEdit || canEditPhone || auth.user?.admin" class="ranking-actions-column"><div class="ranking-row-actions">
                <button v-if="canPointsEdit" class="ranking-primary-action" type="button" @click="openPoints(row)"><Pencil :size="14"/>调整积分</button>
                <button v-if="canEditPhone || auth.user?.admin" class="icon-button ranking-more-button" type="button" :aria-label="`${row.studentName}的更多操作`" title="更多操作" :aria-expanded="rowMenuStudent?.studentId === row.studentId" aria-controls="ranking-row-actions-menu" @click.stop="toggleRowMenu(row, $event)"><Ellipsis :size="18"/></button>
              </div></td>
              <td v-if="canGraduate" class="ranking-graduation-column"><button class="ranking-primary-action" type="button" :disabled="Boolean(graduationBusy)" :aria-label="`将${row.studentName}标记为毕业`" @click="setGraduated(row, true)"><GraduationCap :size="15"/>毕业</button></td>
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
        <tbody><tr v-for="item in filteredRedemptions" :key="item.id"><td><strong>{{ item.studentName }}</strong><small class="ranking-cell-note">ID {{ item.studentId }}</small></td><td>{{ item.rewardName }}</td><td>{{ numberText(item.pointsSpent) }}</td><td>{{ formatDateTime(item.redeemedAt) }}</td><td><span class="ranking-status" :class="item.status === 'FULFILLED' ? 'is-enabled' : 'is-pending'">{{ item.status === 'FULFILLED' ? '已发放' : item.status === 'CANCELLED' ? '已取消 / 已退款' : item.status === 'CANCEL_REQUESTED' ? '申请取消中' : '待发放' }}</span><small v-if="item.fulfilledAt" class="ranking-cell-note">{{ formatDateTime(item.fulfilledAt) }}</small></td><td><button v-if="canRedemptionsFulfill&&['PENDING','FULFILLED'].includes(item.status)" class="button button-quiet ranking-fulfill-button" type="button" @click="setFulfilled(item, item.status !== 'FULFILLED')"><CheckCircle2 :size="14"/>{{ item.status === 'FULFILLED' ? '恢复待发放' : '确认已发放' }}</button></td></tr></tbody>
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
    <div v-if="phoneOpen && canEditPhone" class="ranking-share-backdrop ui-dialog-backdrop" @click.self="closeStudentPhone">
      <form class="ranking-share-dialog ranking-form-dialog ui-modal" role="dialog" aria-modal="true" aria-labelledby="student-phone-title" @submit.prevent="saveStudentPhone">
        <header><div><span><UsersRound :size="18"/></span><div><h2 id="student-phone-title">修改登录手机号</h2><p>{{ phoneStudent?.studentName }} · ID {{ phoneStudent?.studentId }}</p></div></div><button class="icon-button" type="button" aria-label="关闭手机号窗口" :disabled="phoneSaving || phoneLoading" @click="closeStudentPhone"><X :size="17"/></button></header>
        <div class="ranking-form-body"><p v-if="phoneLoading">正在读取手机号…</p><template v-else><label><span>学生手机号</span><input v-model.trim="phoneValue" type="tel" inputmode="numeric" autocomplete="off" maxlength="11" required placeholder="请输入 11 位手机号" aria-label="学生手机号"><small>保存后使用此手机号登录；已有密码和积分保持不变。未开通账号将以初始密码 123456 开通。</small></label><div v-if="phoneError" class="notice notice-error" role="alert">{{ phoneError }}</div></template></div>
        <footer><button class="button button-quiet" type="button" :disabled="phoneSaving || phoneLoading" @click="closeStudentPhone">取消</button><button class="button button-primary" type="submit" :disabled="phoneSaving || phoneLoading">{{ phoneSaving ? '保存中' : '保存手机号' }}</button></footer>
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

    <div v-if="graduatesOpen && canGraduatesRead" class="ranking-share-backdrop ui-dialog-backdrop" @click.self="closeGraduates">
      <section class="ranking-share-dialog ui-modal graduation-dialog" role="dialog" aria-modal="true" aria-labelledby="graduates-title">
        <header><div><span><GraduationCap :size="18"/></span><div><h2 id="graduates-title">已毕业学员</h2><p>毕业后不参与排名，恢复后按当前积分重新排名</p></div></div><button class="icon-button" type="button" aria-label="关闭已毕业学员" :disabled="Boolean(graduationBusy)" @click="closeGraduates"><X :size="17"/></button></header>
        <div class="graduation-dialog-body">
          <label class="ranking-search"><Search :size="16"/><input v-model="graduatesSearch" type="search" placeholder="搜索已毕业学员姓名或 ID" aria-label="搜索已毕业学员"></label>
          <div v-if="graduatesError" class="notice notice-error" role="alert">{{ graduatesError }} <button class="button button-quiet" type="button" @click="loadGraduates">重试</button></div>
          <div v-else-if="graduatesLoading" class="ranking-admin-state">正在加载已毕业学员</div>
          <div v-else-if="!filteredGraduates.length" class="ranking-admin-state">{{ graduatesSearch ? '没有找到匹配的学员' : '暂无已毕业学员' }}</div>
          <div v-else class="ranking-admin-table-wrap"><table class="ranking-admin-table"><thead><tr><th>学员</th><th>毕业时间</th><th v-if="canRestore">操作</th></tr></thead><tbody><tr v-for="row in filteredGraduates" :key="row.studentId"><td><div class="ranking-student"><span><strong>{{ row.studentName }}</strong><small>ID {{ row.studentId }}</small></span></div></td><td>{{ formatDateTime(row.graduatedAt) }}</td><td v-if="canRestore"><button class="button button-quiet" type="button" :disabled="Boolean(graduationBusy)" :aria-label="`恢复${row.studentName}参与排名`" @click="setGraduated(row, false)"><RotateCcw :size="14"/>恢复</button></td></tr></tbody></table></div>
        </div>
        <footer><button class="button button-quiet" type="button" :disabled="Boolean(graduationBusy)" @click="closeGraduates">关闭</button></footer>
      </section>
    </div>
    <div v-if="rowMenuStudent" id="ranking-row-actions-menu" class="ranking-row-menu" :style="rowMenuStyle" aria-label="学员更多操作" @click.stop>
      <button v-if="canEditPhone" type="button" @click="runRowAction(openStudentPhone)"><Pencil :size="15"/>修改手机号</button>
      <button v-if="auth.user?.admin" type="button" @click="runRowAction(openStudentPassword)"><Eye :size="15"/>查看密码</button>
    </div>
    <div v-if="shareOpen && canShare" class="ranking-share-backdrop ui-dialog-backdrop" @click.self="shareOpen = false"><section class="ranking-share-dialog ui-modal"><header><div><span><Share2 :size="18"/></span><div><h2>分享学生排行榜</h2><p>当前账号名下全部学员总榜</p></div></div><button class="icon-button" type="button" title="关闭" @click="shareOpen = false"><X :size="17"/></button></header><div class="ranking-share-content"><label>分享链接</label><input :value="shareUrl" readonly @focus="$event.target.select()"></div><footer><a class="button button-quiet" :href="shareUrl" target="_blank" rel="noopener"><ExternalLink :size="15"/>打开预览</a><button class="button button-primary" type="button" @click="copyShareLink"><Copy :size="15"/>复制链接</button></footer></section></div>
  </AdminLayout>
</template>

<style scoped>
.ranking-list-header-actions { display:flex; align-items:center; justify-content:flex-end; gap:12px; flex-wrap:wrap; }
.ranking-graduation-column { width:100px; min-width:100px; white-space:nowrap; }
.graduation-dialog { width:min(760px,calc(100vw - 32px)); }
.graduation-dialog-body { padding:20px 24px; max-height:65vh; overflow:auto; }
.graduation-dialog-body > .ranking-search { width:100%; margin-bottom:16px; }
.graduation-dialog .ranking-search input { width:100%; min-width:0; padding:0; border:0; border-radius:0; background:transparent; box-shadow:none; outline:none; }
.graduation-dialog-body .ranking-admin-table { min-width:520px; }
@media(max-width:760px) {
 .ranking-list-header-actions { width:100%; justify-content:flex-start; }
 .ranking-list-header-actions .ranking-search { width:100%; }
 .graduation-dialog-body { padding:16px; }
}
</style>
