<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from "vue";
import { ArrowDown, ArrowUp, Copy, ExternalLink, Minus, RefreshCw, Share2, Trophy, UsersRound, X } from "@lucide/vue";
import AdminLayout from "../components/AdminLayout.vue";
import { api, notify, writeClipboard } from "../api";
import { auth } from "../auth";
import { rankingAvatarText, rankingShareUrl, rankingSummary, rankingTrendView } from "../rankingAdmin.js";
import { formatDateTime } from "../utils";

const catalog = ref(null);
const board = ref(null);
const campId = ref("");
const classId = ref("");
const scope = ref("class");
const loading = ref(false);
const error = ref("");
const shareOpen = ref(false);

const camps = computed(() => catalog.value?.camps || []);
const selectedCamp = computed(() => camps.value.find((item) => String(item.id) === String(campId.value)) || null);
const classes = computed(() => selectedCamp.value?.classes || []);
const rows = computed(() => board.value?.rankings || []);
const summary = computed(() => rankingSummary(rows.value));
const shareUrl = computed(() => rankingShareUrl({
  origin: window.location.origin,
  teacherId: catalog.value?.teacherId || auth.user?.teacherId,
  campId: campId.value,
  classId: classId.value,
  scope: scope.value
}));
const boardLabel = computed(() => scope.value === "camp" ? "训练营榜" : board.value?.className || "班级榜");

function numberText(value) {
  return new Intl.NumberFormat("zh-CN").format(Number(value || 0));
}

function selectDefaultClass(preferred = "") {
  classId.value = classes.value.some((item) => String(item.id) === String(preferred))
    ? String(preferred)
    : String(classes.value[0]?.id || "");
}

async function loadCatalog() {
  const teacherId = auth.user?.teacherId;
  if (!teacherId) {
    error.value = "当前账号缺少教师 ID，无法读取学生排名";
    return;
  }
  loading.value = true;
  error.value = "";
  try {
    catalog.value = await api(`/public/rankings/catalog?teacherId=${encodeURIComponent(teacherId)}`);
    if (!camps.value.length) {
      board.value = null;
      return;
    }
    campId.value = String(camps.value[0].id);
    selectDefaultClass();
    await loadBoard();
  } catch (failure) {
    error.value = failure.message;
  } finally {
    loading.value = false;
  }
}

async function loadBoard() {
  if (!campId.value || (scope.value === "class" && !classId.value)) {
    board.value = null;
    return;
  }
  loading.value = true;
  error.value = "";
  try {
    const params = new URLSearchParams({
      teacherId: catalog.value?.teacherId || auth.user?.teacherId || "",
      campId: campId.value,
      scope: scope.value
    });
    if (scope.value === "class") params.set("classId", classId.value);
    board.value = await api(`/public/rankings?${params}`);
  } catch (failure) {
    board.value = null;
    error.value = failure.message;
  } finally {
    loading.value = false;
  }
}

async function changeCamp() {
  selectDefaultClass();
  await loadBoard();
}

async function changeScope(nextScope) {
  scope.value = nextScope;
  if (nextScope === "class" && !classId.value) selectDefaultClass();
  await loadBoard();
}

function openShare() {
  if (!campId.value || (scope.value === "class" && !classId.value)) return;
  shareOpen.value = true;
}

async function copyShareLink() {
  await writeClipboard(shareUrl.value);
  notify("学生排行榜链接已复制");
  shareOpen.value = false;
}

function closeOnEscape(event) {
  if (event.key === "Escape") shareOpen.value = false;
}

onMounted(() => {
  document.addEventListener("keydown", closeOnEscape);
  loadCatalog();
});
onBeforeUnmount(() => document.removeEventListener("keydown", closeOnEscape));
</script>

<template>
  <AdminLayout page-title="学生排名" active-page="rankings" content-class="admin-main--rankings">
    <div class="admin-page-heading ranking-page-heading">
      <div><h1>学生排名</h1><p>{{ catalog?.teacherName || auth.user?.username }}名下学员积分排名</p></div>
      <div class="ranking-heading-actions">
        <button class="button button-quiet" type="button" :disabled="loading || !campId" @click="loadBoard"><RefreshCw :class="{ 'spin-icon': loading }" :size="15"/>刷新</button>
        <button class="button button-primary" type="button" :disabled="!campId || (scope === 'class' && !classId)" @click="openShare"><Share2 :size="15"/>分享</button>
      </div>
    </div>

    <div v-if="error" class="notice notice-error">{{ error }}</div>

    <section class="admin-panel ranking-filter-panel" aria-label="排名筛选">
      <label><span>训练营</span><select v-model="campId" :disabled="loading || !camps.length" @change="changeCamp"><option v-for="camp in camps" :key="camp.id" :value="String(camp.id)">{{ camp.name }}</option></select></label>
      <label v-if="scope === 'class'"><span>班级</span><select v-model="classId" :disabled="loading || !classes.length" @change="loadBoard"><option v-for="item in classes" :key="item.id" :value="String(item.id)">{{ item.name }}</option></select></label>
      <div class="ranking-filter-field"><span>榜单范围</span><div class="ranking-scope-switch" role="group" aria-label="榜单范围"><button type="button" :class="{ active: scope === 'class' }" @click="changeScope('class')">班级榜</button><button type="button" :class="{ active: scope === 'camp' }" @click="changeScope('camp')">训练营榜</button></div></div>
      <div class="ranking-filter-meta"><span>{{ catalog?.teacherId || auth.user?.teacherId }}</span><small>{{ board?.updatedAt ? `更新于 ${formatDateTime(board.updatedAt)}` : "尚未同步" }}</small></div>
    </section>

    <section v-if="board" class="ranking-admin-summary" aria-label="排名概览">
      <article><span class="ranking-summary-icon ranking-summary-gold"><Trophy :size="20"/></span><div><strong>{{ boardLabel }}</strong><small>{{ board.campName }}</small></div></article>
      <article><span class="ranking-summary-icon ranking-summary-blue"><UsersRound :size="20"/></span><div><strong>{{ summary.studentCount }}</strong><small>上榜学员</small></div></article>
      <article><span class="ranking-summary-icon ranking-summary-teal">Σ</span><div><strong>{{ numberText(summary.totalPoints) }}</strong><small>累计积分</small></div></article>
      <article><span class="ranking-summary-icon ranking-summary-gray">Ø</span><div><strong>{{ numberText(summary.averagePoints) }}</strong><small>平均积分</small></div></article>
    </section>

    <section class="admin-panel ranking-list-panel">
      <div class="panel-heading ranking-list-heading"><div><h2>{{ board ? `${board.campName} · ${boardLabel}` : "学员排名" }}</h2><small v-if="board">共 {{ summary.studentCount }} 名学员</small></div></div>
      <div v-if="loading && !board" class="ranking-admin-state"><RefreshCw class="spin-icon" :size="22"/><span>正在加载排名</span></div>
      <div v-else-if="!catalog?.camps?.length" class="ranking-admin-state"><Trophy :size="23"/><span>暂无可展示的排行榜数据</span></div>
      <div v-else-if="board && !rows.length" class="ranking-admin-state"><UsersRound :size="23"/><span>当前范围暂无学员积分</span></div>
      <div v-else-if="board" class="ranking-admin-table-wrap">
        <table class="ranking-admin-table">
          <thead><tr><th>名次</th><th>学员</th><th>所属班级</th><th>总积分</th><th>积分构成</th><th>正确率</th><th>等级</th><th>趋势</th></tr></thead>
          <tbody>
            <tr v-for="row in rows" :key="row.studentId">
              <td><span class="ranking-number" :class="`rank-${Math.min(row.rank, 4)}`">{{ row.rank }}</span></td>
              <td><div class="ranking-student"><span class="ranking-student-avatar">{{ rankingAvatarText(row.studentName) }}</span><span><strong>{{ row.studentName }}</strong><small>ID {{ row.studentId }}</small></span></div></td>
              <td><span class="ranking-class-name">{{ row.className || board.className }}</span></td>
              <td><strong class="ranking-total-points">{{ numberText(row.totalPoints) }}</strong></td>
              <td><div class="ranking-score-parts"><span>完课 {{ row.completionPoints }}</span><span>课上 {{ row.inclassPoints }}</span><span>课后 {{ row.homeworkPoints }}</span></div></td>
              <td>{{ Number(row.accuracyRate || 0).toFixed(1) }}%</td>
              <td><span class="ranking-level" :class="`ranking-level-${row.level}`">{{ row.levelName }}</span></td>
              <td><span class="ranking-trend" :class="`ranking-trend-${rankingTrendView(row).direction}`" :title="rankingTrendView(row).title"><ArrowUp v-if="rankingTrendView(row).direction === 'up'" :size="14"/><ArrowDown v-else-if="rankingTrendView(row).direction === 'down'" :size="14"/><Minus v-else :size="14"/>{{ rankingTrendView(row).direction === 'same' ? '' : Math.abs(row.rankChange) }}</span></td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>

    <div v-if="shareOpen" class="ranking-share-backdrop" role="presentation" @click.self="shareOpen = false">
      <section class="ranking-share-dialog" role="dialog" aria-modal="true" aria-labelledby="ranking-share-title">
        <header><div><span><Share2 :size="18"/></span><div><h2 id="ranking-share-title">分享学生排行榜</h2><p>{{ selectedCamp?.name }} · {{ scope === 'camp' ? '训练营榜' : classes.find(item => String(item.id) === classId)?.name }}</p></div></div><button class="icon-button" type="button" title="关闭" aria-label="关闭分享" @click="shareOpen = false"><X :size="17"/></button></header>
        <div class="ranking-share-content"><label for="rankingShareUrl">分享链接</label><input id="rankingShareUrl" :value="shareUrl" readonly @focus="$event.target.select()"></div>
        <footer><a class="button button-quiet" :href="shareUrl" target="_blank" rel="noopener"><ExternalLink :size="15"/>打开预览</a><button class="button button-primary" type="button" @click="copyShareLink"><Copy :size="15"/>复制链接</button></footer>
      </section>
    </div>
  </AdminLayout>
</template>
