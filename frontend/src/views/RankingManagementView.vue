<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from "vue";
import {
  ArrowDown, ArrowUp, Award, CalendarClock, CheckCircle2, CircleOff, Copy, ExternalLink, Gift, ImagePlus,
  Megaphone, Minus, PackageCheck, Pencil, Plus, RefreshCw, Save, Search, Share2,
  Trash2, Trophy, UsersRound, X
} from "@lucide/vue";
import AdminLayout from "../components/AdminLayout.vue";
import { api, jsonBody, notify, writeClipboard } from "../api";
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
const rewardSaving = ref(false);
const redemptionSaving = ref(false);
const rewardForm = ref(emptyRewardForm());
const rewardImage = ref(null);
const rewardPreview = ref("");
const redemptionForm = ref({ studentId: "", rewardId: "" });

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
const enabledRewards = computed(() => rewards.value.filter((reward) => reward.enabled));
const selectedStudent = computed(() => rows.value.find((row) => String(row.studentId) === String(redemptionForm.value.studentId)) || null);
const selectedReward = computed(() => rewards.value.find((reward) => String(reward.id) === String(redemptionForm.value.rewardId)) || null);
const spentByStudent = computed(() => redemptions.value.reduce((map, item) => {
  map[item.studentId] = (map[item.studentId] || 0) + Number(item.pointsSpent || 0); return map;
}, {}));
const availablePoints = computed(() => Math.max(0, Number(selectedStudent.value?.totalPoints || 0) - Number(spentByStudent.value[selectedStudent.value?.studentId] || 0)));
const redemptionAllowed = computed(() => selectedStudent.value && selectedReward.value && selectedReward.value.requiredPoints <= availablePoints.value);
const shareUrl = computed(() => rankingShareUrl({ origin: window.location.origin }));
const pendingCount = computed(() => redemptions.value.filter((item) => item.status === "PENDING").length);

function emptyRewardForm() { return { id: null, name: "", requiredPoints: 100, enabled: true, imageUrl: "" }; }
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

async function loadAll() {
  loading.value = true; error.value = "";
  try {
    const [boardValue, announcementValues, rewardValues, redemptionValues] = await Promise.all([
      api("/rankings/admin/board"), api("/rankings/admin/announcements"),
      api("/rankings/admin/rewards"), api("/rankings/admin/redemptions")
    ]);
    board.value = boardValue; announcements.value = normaliseAnnouncements(announcementValues);
    rewards.value = rewardValues; redemptions.value = redemptionValues;
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
    announcements.value = [normaliseAnnouncements([created])[0], ...announcements.value];
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

function openReward(reward = null) {
  rewardForm.value = reward ? { id: reward.id, name: reward.name, requiredPoints: reward.requiredPoints, enabled: reward.enabled, imageUrl: reward.imageUrl || "" } : emptyRewardForm();
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
    body.set("name", rewardForm.value.name); body.set("requiredPoints", String(rewardForm.value.requiredPoints)); body.set("enabled", String(rewardForm.value.enabled));
    if (rewardImage.value) body.set("image", rewardImage.value);
    const path = rewardForm.value.id ? `/rankings/admin/rewards/${rewardForm.value.id}` : "/rankings/admin/rewards";
    await api(path, { method: rewardForm.value.id ? "PUT" : "POST", body });
    rewards.value = await api("/rankings/admin/rewards"); closeReward(); notify(rewardForm.value.id ? "奖品已更新" : "奖品已添加");
  } catch (failure) { notify(failure.message); }
  finally { rewardSaving.value = false; }
}
async function deleteReward(reward) {
  if (!window.confirm(`确定删除奖品“${reward.name}”吗？历史兑换记录会保留。`)) return;
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
    redemptions.value = await api("/rankings/admin/redemptions"); redemptionOpen.value = false; notify("兑换已登记，等待老师发放");
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
function closeOnEscape(event) { if (event.key === "Escape") { shareOpen.value = false; closeReward(); redemptionOpen.value = false; } }

onMounted(() => { document.addEventListener("keydown", closeOnEscape); loadAll(); });
onBeforeUnmount(() => document.removeEventListener("keydown", closeOnEscape));
</script>

<template>
  <AdminLayout page-title="学生排名" active-page="rankings" content-class="admin-main--rankings">
    <div class="admin-page-heading ranking-page-heading">
      <div><h1>学生排名</h1><p>管理全量积分排名、排行榜公告、奖品与发放记录</p></div>
      <div class="ranking-heading-actions">
        <button class="button button-quiet" type="button" :disabled="loading" @click="loadAll"><RefreshCw :class="{ 'spin-icon': loading }" :size="15"/>刷新</button>
        <button class="button button-primary" type="button" @click="shareOpen = true"><Share2 :size="15"/>分享</button>
      </div>
    </div>
    <div v-if="error" class="notice notice-error">{{ error }}</div>

    <nav class="ranking-workspace-tabs" aria-label="排行榜管理功能">
      <button type="button" :class="{ active: activeTab === 'rankings' }" @click="activeTab = 'rankings'"><Trophy :size="17"/>全量排名</button>
      <button type="button" :class="{ active: activeTab === 'rewards' }" @click="activeTab = 'rewards'"><Gift :size="17"/>奖品管理 <span>{{ rewards.length }}</span></button>
      <button type="button" :class="{ active: activeTab === 'redemptions' }" @click="activeTab = 'redemptions'"><PackageCheck :size="17"/>兑换记录 <span v-if="pendingCount">{{ pendingCount }}</span></button>
      <button type="button" :class="{ active: activeTab === 'announcements' }" @click="activeTab = 'announcements'"><Megaphone :size="17"/>公告管理 <span>{{ announcements.length }}</span></button>
    </nav>

    <template v-if="activeTab === 'rankings'">


      <section v-if="board" class="ranking-admin-summary" aria-label="排名概览">
        <article><span class="ranking-summary-icon ranking-summary-gold"><Trophy :size="20"/></span><div><strong>全量榜</strong><small>全部营期、全部班级</small></div></article>
        <article><span class="ranking-summary-icon ranking-summary-blue"><UsersRound :size="20"/></span><div><strong>{{ summary.studentCount }}</strong><small>学员总数</small></div></article>
        <article><span class="ranking-summary-icon ranking-summary-teal">Σ</span><div><strong>{{ numberText(summary.totalPoints) }}</strong><small>累计积分</small></div></article>
        <article><span class="ranking-summary-icon ranking-summary-gray">Ø</span><div><strong>{{ numberText(summary.averagePoints) }}</strong><small>平均积分</small></div></article>
      </section>

      <section class="admin-panel ranking-list-panel">
        <div class="panel-heading ranking-list-heading ranking-toolbar"><div><h2>全部学员排名</h2><small>{{ searchText ? `找到 ${filteredRows.length} 名学员` : `共 ${summary.studentCount} 名学员` }}</small></div><label class="ranking-search"><Search :size="16"/><input v-model="searchText" type="search" placeholder="搜索学员姓名或 ID" aria-label="搜索学员姓名或 ID"></label></div>
        <div v-if="loading && !board" class="ranking-admin-state"><RefreshCw class="spin-icon" :size="22"/><span>正在加载排名</span></div>
        <div v-else-if="board && !filteredRows.length" class="ranking-admin-state"><Search :size="23"/><span>没有找到匹配的学员</span></div>
        <div v-else-if="board" class="ranking-admin-table-wrap">
          <table class="ranking-admin-table"><thead><tr><th>名次</th><th>学员</th><th>所属班级</th><th>总积分</th><th>可用积分</th><th>积分构成</th><th>正确率</th><th>等级</th><th>趋势</th></tr></thead>
            <tbody><tr v-for="row in filteredRows" :key="row.studentId">
              <td><span class="ranking-number" :class="`rank-${Math.min(row.rank, 4)}`">{{ row.rank }}</span></td>
              <td><div class="ranking-student"><img class="ranking-student-avatar" src="/favicon-dog-20260913.png" alt="" width="38" height="38"><span><strong>{{ row.studentName }}</strong><small>ID {{ row.studentId }}</small></span></div></td>
              <td><span class="ranking-class-name">{{ row.className || '—' }}</span></td><td><strong class="ranking-total-points">{{ numberText(row.totalPoints) }}</strong></td>
              <td>{{ numberText(Math.max(0, row.totalPoints - Number(spentByStudent[row.studentId] || 0))) }}</td>
              <td><div class="ranking-score-parts"><span>完课 {{ row.completionPoints }}</span><span>课上 {{ row.inclassPoints }}</span><span>课后 {{ row.homeworkPoints }}</span></div></td>
              <td>{{ Number(row.accuracyRate || 0).toFixed(1) }}%</td><td><span class="ranking-level" :class="`ranking-level-${row.level}`">{{ row.levelName }}</span></td>
              <td><span class="ranking-trend" :class="`ranking-trend-${rankingTrendView(row).direction}`" :title="rankingTrendView(row).title"><ArrowUp v-if="rankingTrendView(row).direction === 'up'" :size="14"/><ArrowDown v-else-if="rankingTrendView(row).direction === 'down'" :size="14"/><Minus v-else :size="14"/>{{ rankingTrendView(row).direction === 'same' ? '' : Math.abs(row.rankChange) }}</span></td>
            </tr></tbody>
          </table>
        </div>
      </section>
    </template>

    <template v-else-if="activeTab === 'announcements'">
      <section class="admin-panel ranking-announcement-panel">
        <div><Megaphone :size="19"/><span><strong>学生端公告</strong><small>发布后会显示在公开排行榜顶部，最多 500 字；可立即发布或设置定时上线。</small></span></div>
        <textarea v-model="announcementDraft" maxlength="500" rows="2" placeholder="例如：本周五积分商城开放兑换，请合理安排积分。"></textarea>
        <div class="ranking-announcement-schedules">
          <label class="ranking-announcement-schedule"><CalendarClock :size="16"/><span>上线时间（可选）</span><input v-model="announcementPublishAt" type="datetime-local"></label>
          <label class="ranking-announcement-schedule"><CalendarClock :size="16"/><span>下线时间（可选）</span><input v-model="announcementUnpublishAt" type="datetime-local"></label>
        </div>
        <button class="button button-primary" type="button" :disabled="savingAnnouncement || !announcementDraft.trim()" @click="saveAnnouncement">{{ savingAnnouncement ? "发布中" : "发布公告" }}</button>
      </section>

      <section class="admin-panel ranking-announcement-history">
      <div class="panel-heading ranking-toolbar"><div><h2>公告管理</h2><small>共 {{ announcements.length }} 条，支持手动上线、下线和定时发布</small></div><CalendarClock :size="19" class="ranking-history-heading-icon"/></div>
      <div v-if="!announcements.length" class="ranking-admin-state"><Megaphone :size="25"/><span>还没有发布过公告</span></div>
      <div v-else class="ranking-announcement-list">
        <article v-for="item in announcements" :key="item.id" class="ranking-announcement-row">
          <div class="ranking-announcement-copy"><p>{{ item.text }}</p><div class="ranking-announcement-meta"><span class="ranking-status" :class="'ranking-announcement-status-' + item.status.toLowerCase()">{{ announcementStatusLabel(item.status) }}</span><span v-if="item.publishAt">上线 {{ formatDateTime(item.publishAt) }}</span><span v-if="item.unpublishAt">下线 {{ formatDateTime(item.unpublishAt) }}</span><span>创建于 {{ formatDateTime(item.createdAt) }}</span></div></div>
          <div class="ranking-announcement-controls">
            <div class="ranking-announcement-time-editor">
              <label>上线 <input v-model="item.publishAtInput" type="datetime-local"></label>
              <label>下线 <input v-model="item.unpublishAtInput" type="datetime-local"></label>
              <button class="button button-quiet ranking-announcement-save" type="button" @click="saveAnnouncementSchedule(item)"><Save :size="14"/>保存时间</button>
            </div>
            <div class="ranking-row-actions"><button v-if="item.status === 'ONLINE'" class="button button-quiet ranking-announcement-action" type="button" @click="setAnnouncementOnline(item, false)"><CircleOff :size="14"/>下线</button><button v-else class="button button-quiet ranking-announcement-action" type="button" @click="setAnnouncementOnline(item, true)"><CheckCircle2 :size="14"/>{{ item.status === 'SCHEDULED' ? '立即上线' : '上线' }}</button></div>
          </div>
        </article>
      </div>
      </section>
    </template>

    <section v-else-if="activeTab === 'rewards'" class="admin-panel ranking-list-panel">
      <div class="panel-heading ranking-toolbar"><div><h2>奖品管理</h2><small>维护学生可兑换的奖品、图片和积分门槛</small></div><button class="button button-primary" type="button" @click="openReward()"><Plus :size="15"/>添加奖品</button></div>
      <div v-if="!rewards.length" class="ranking-admin-state"><Gift :size="25"/><span>还没有奖品，先添加一个奖品</span></div>
      <div v-else class="ranking-reward-list">
        <article v-for="reward in rewards" :key="reward.id" class="ranking-reward-row">
          <div class="ranking-reward-image"><img v-if="reward.imageUrl" :src="reward.imageUrl" :alt="reward.name"><Gift v-else :size="26"/></div>
          <div class="ranking-reward-copy"><strong>{{ reward.name }}</strong><span>{{ numberText(reward.requiredPoints) }} 积分</span></div>
          <span class="ranking-status" :class="reward.enabled ? 'is-enabled' : 'is-disabled'">{{ reward.enabled ? '兑换中' : '已停用' }}</span>
          <div class="ranking-row-actions"><button class="icon-button" type="button" title="编辑奖品" @click="openReward(reward)"><Pencil :size="16"/></button><button class="icon-button ranking-danger-button" type="button" title="删除奖品" @click="deleteReward(reward)"><Trash2 :size="16"/></button></div>
        </article>
      </div>
    </section>

    <section v-else class="admin-panel ranking-list-panel">
      <div class="panel-heading ranking-toolbar"><div><h2>兑换与发放</h2><small>{{ pendingCount }} 条待发放 · 共 {{ redemptions.length }} 条记录</small></div><div class="ranking-toolbar-actions"><label class="ranking-search"><Search :size="16"/><input v-model="redemptionSearch" type="search" placeholder="搜索学员或奖品" aria-label="搜索兑换记录"></label><button class="button button-primary" type="button" :disabled="!rows.length || !enabledRewards.length" @click="openRedemption"><Plus :size="15"/>登记兑换</button></div></div>
      <div v-if="!redemptions.length" class="ranking-admin-state"><PackageCheck :size="25"/><span>暂无兑换记录</span></div>
      <div v-else class="ranking-admin-table-wrap"><table class="ranking-admin-table ranking-redemption-table"><thead><tr><th>学员</th><th>奖品</th><th>扣除积分</th><th>兑换时间</th><th>发放状态</th><th>操作</th></tr></thead>
        <tbody><tr v-for="item in filteredRedemptions" :key="item.id"><td><strong>{{ item.studentName }}</strong><small class="ranking-cell-note">ID {{ item.studentId }}</small></td><td>{{ item.rewardName }}</td><td>{{ numberText(item.pointsSpent) }}</td><td>{{ formatDateTime(item.redeemedAt) }}</td><td><span class="ranking-status" :class="item.status === 'FULFILLED' ? 'is-enabled' : 'is-pending'">{{ item.status === 'FULFILLED' ? '已发放' : '待发放' }}</span><small v-if="item.fulfilledAt" class="ranking-cell-note">{{ formatDateTime(item.fulfilledAt) }}</small></td><td><button class="button button-quiet ranking-fulfill-button" type="button" @click="setFulfilled(item, item.status !== 'FULFILLED')"><CheckCircle2 :size="14"/>{{ item.status === 'FULFILLED' ? '恢复待发放' : '确认已发放' }}</button></td></tr></tbody>
      </table></div>
    </section>

    <div v-if="rewardOpen" class="ranking-share-backdrop" @click.self="closeReward">
      <form class="ranking-share-dialog ranking-form-dialog" @submit.prevent="saveReward"><header><div><span><Gift :size="18"/></span><div><h2>{{ rewardForm.id ? '编辑奖品' : '添加奖品' }}</h2><p>设置展示信息与兑换积分</p></div></div><button class="icon-button" type="button" title="关闭" @click="closeReward"><X :size="17"/></button></header>
        <div class="ranking-form-body"><label><span>奖品名称</span><input v-model.trim="rewardForm.name" required maxlength="100" placeholder="例如：编程主题笔记本"></label><label><span>所需积分</span><input v-model.number="rewardForm.requiredPoints" required type="number" min="1" max="1000000"></label><label class="ranking-image-field"><span>奖品图片</span><div><div class="ranking-image-preview"><img v-if="rewardPreview" :src="rewardPreview" alt="奖品预览"><ImagePlus v-else :size="25"/></div><input type="file" accept="image/png,image/jpeg,image/webp,image/gif" @change="chooseRewardImage"><small>PNG、JPG、WebP 或 GIF，最大 2 MB</small></div></label><label class="ranking-checkbox"><input v-model="rewardForm.enabled" type="checkbox"><span>允许兑换</span></label></div>
        <footer><button class="button button-quiet" type="button" @click="closeReward">取消</button><button class="button button-primary" type="submit" :disabled="rewardSaving">{{ rewardSaving ? '保存中' : '保存奖品' }}</button></footer>
      </form>
    </div>

    <div v-if="redemptionOpen" class="ranking-share-backdrop" @click.self="redemptionOpen = false">
      <form class="ranking-share-dialog ranking-form-dialog" @submit.prevent="createRedemption"><header><div><span><Award :size="18"/></span><div><h2>登记奖品兑换</h2><p>确认后立即占用学员积分，记录默认为待发放</p></div></div><button class="icon-button" type="button" title="关闭" @click="redemptionOpen = false"><X :size="17"/></button></header>
        <div class="ranking-form-body"><label><span>兑换学员</span><select v-model="redemptionForm.studentId" required><option v-for="row in rows" :key="row.studentId" :value="row.studentId">{{ row.studentName }} · 第 {{ row.rank }} 名 · 可用 {{ Math.max(0, row.totalPoints - Number(spentByStudent[row.studentId] || 0)) }} 分</option></select></label><label><span>兑换奖品</span><select v-model="redemptionForm.rewardId" required><option v-for="reward in enabledRewards" :key="reward.id" :value="reward.id">{{ reward.name }} · {{ reward.requiredPoints }} 分</option></select></label><div class="ranking-redemption-check" :class="{ invalid: selectedReward && !redemptionAllowed }"><span>当前可用积分</span><strong>{{ numberText(availablePoints) }}</strong><small v-if="selectedReward">兑换后剩余 {{ numberText(Math.max(0, availablePoints - selectedReward.requiredPoints)) }} 分</small></div></div>
        <footer><button class="button button-quiet" type="button" @click="redemptionOpen = false">取消</button><button class="button button-primary" type="submit" :disabled="redemptionSaving || !redemptionAllowed">{{ redemptionSaving ? '登记中' : '确认兑换' }}</button></footer>
      </form>
    </div>

    <div v-if="shareOpen" class="ranking-share-backdrop" @click.self="shareOpen = false"><section class="ranking-share-dialog"><header><div><span><Share2 :size="18"/></span><div><h2>分享学生排行榜</h2><p>当前账号名下全部学员总榜</p></div></div><button class="icon-button" type="button" title="关闭" @click="shareOpen = false"><X :size="17"/></button></header><div class="ranking-share-content"><label>分享链接</label><input :value="shareUrl" readonly @focus="$event.target.select()"></div><footer><a class="button button-quiet" :href="shareUrl" target="_blank" rel="noopener"><ExternalLink :size="15"/>打开预览</a><button class="button button-primary" type="button" @click="copyShareLink"><Copy :size="15"/>复制链接</button></footer></section></div>
  </AdminLayout>
</template>
