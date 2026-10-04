<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from "vue";
import { RouterLink } from "vue-router";
import {
  Backpack,
  Check,
  Gamepad2,
  Info,
  Keyboard,
  Maximize2,
  Minimize2,
  RefreshCw,
  Star,
  Trophy
} from "@lucide/vue";
import { api } from "../api";
import {
  rankingAvatarText,
  rankingPointsToPass,
  rankingTrendView,
  rankingVisibleRows
} from "../rankingAdmin.js";

const levelMinimums = [0, 600, 1500, 2700, 4200, 5400];
const levelNames = ["石墨", "青铜", "白银", "黄金", "蓝宝石", "钻石"];

const board = ref(null);
const loading = ref(false);
const error = ref("");
const selectedStudentId = ref("");
const detailRow = ref(null);
const pinnedDetailId = ref("");
const popover = ref(null);
const popoverStyle = ref({});
const isFullscreen = ref(false);
let refreshTimer;

const rows = computed(() => board.value?.rankings || []);
const selectedIndex = computed(() => rows.value.findIndex((row) => String(row.studentId) === String(selectedStudentId.value)));
const visibleRows = computed(() => rankingVisibleRows(rows.value, selectedStudentId.value));
const showsSelectedSeparately = computed(() => visibleRows.value.length > 10);
const selectedStudent = computed(() => selectedIndex.value >= 0 ? rows.value[selectedIndex.value] : null);
const nextLevelIndex = computed(() => selectedStudent.value ? Math.min(selectedStudent.value.level, levelMinimums.length - 1) : 0);
const isMaxLevel = computed(() => Number(selectedStudent.value?.level || 0) >= 6);
const nextLevelName = computed(() => isMaxLevel.value ? "最高等级" : `下一等级 · ${levelNames[nextLevelIndex.value]}`);
const nextLevelPoints = computed(() => {
  if (!selectedStudent.value) return "等待数据";
  if (isMaxLevel.value) return "已达钻石";
  return `还差 ${Math.max(0, levelMinimums[nextLevelIndex.value] - Number(selectedStudent.value.totalPoints || 0))} 积分`;
});
const motivationText = computed(() => {
  if (!selectedStudent.value) return "选择姓名后查看升级目标";
  if (selectedStudent.value.rank === 1) return "当前已是全员榜第 1 名，继续保持！";
  return `距离超越上一名还差 ${rankingPointsToPass(rows.value, selectedIndex.value)} 分`;
});
const updatedText = computed(() => {
  if (!board.value?.updatedAt) return "尚未同步";
  return `更新于 ${new Intl.DateTimeFormat("zh-CN", {
    timeZone: "Asia/Shanghai",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    hour12: false
  }).format(new Date(board.value.updatedAt))}`;
});

function rememberSelection() {
  if (selectedStudentId.value) localStorage.setItem("codedog-ranking-student:all", selectedStudentId.value);
}

function initializeSelection() {
  const saved = selectedStudentId.value || localStorage.getItem("codedog-ranking-student:all") || "";
  const match = rows.value.find((row) => String(row.studentId) === String(saved));
  selectedStudentId.value = String(match?.studentId || rows.value[0]?.studentId || "");
  rememberSelection();
}

async function loadBoard() {
  loading.value = true;
  error.value = "";
  try {
    board.value = await api("/public/rankings/all");
    initializeSelection();
  } catch (failure) {
    error.value = failure.message || "排行榜加载失败";
  } finally {
    loading.value = false;
  }
}

function selectStudent(row) {
  selectedStudentId.value = String(row.studentId);
  rememberSelection();
}

function positionPopover(anchor) {
  if (!anchor) return;
  const rect = anchor.getBoundingClientRect();
  const width = Math.min(290, window.innerWidth - 20);
  const left = Math.max(10, Math.min(window.innerWidth - width - 10, rect.left + (rect.width - width) / 2));
  popoverStyle.value = { left: `${Math.round(left)}px`, top: `${Math.round(Math.max(10, rect.bottom + 10))}px` };
  nextTick(() => {
    const box = popover.value?.getBoundingClientRect();
    if (!box) return;
    const above = rect.top - box.height - 10;
    const top = above >= 10 ? above : Math.min(window.innerHeight - box.height - 10, rect.bottom + 10);
    popoverStyle.value = { left: `${Math.round(left)}px`, top: `${Math.round(Math.max(10, top))}px` };
  });
}

function showDetails(row, event, pin = false) {
  detailRow.value = row;
  pinnedDetailId.value = pin ? String(row.studentId) : "";
  positionPopover(event.currentTarget);
}

function showSelectedDetails(event) {
  if (!selectedStudent.value) return;
  showDetails(selectedStudent.value, event, true);
}

function hideHover() {
  if (!pinnedDetailId.value) detailRow.value = null;
}

function closeDetails() {
  detailRow.value = null;
  pinnedDetailId.value = "";
}

function toggleRowDetails(row, event) {
  selectStudent(row);
  if (pinnedDetailId.value === String(row.studentId)) {
    closeDetails();
    return;
  }
  showDetails(row, event, true);
}

function handleOutsideClick() {
  closeDetails();
}

function handleEscape(event) {
  if (event.key === "Escape") closeDetails();
}

function handleResize() {
  if (!pinnedDetailId.value) {
    closeDetails();
    return;
  }
  const anchor = document.querySelector(`[data-student-id="${CSS.escape(pinnedDetailId.value)}"]`);
  if (anchor) positionPopover(anchor);
}

async function toggleFullscreen() {
  if (document.fullscreenElement) await document.exitFullscreen();
  else await document.documentElement.requestFullscreen();
}

function handleFullscreenChange() {
  isFullscreen.value = Boolean(document.fullscreenElement);
}

onMounted(() => {
  loadBoard();
  refreshTimer = window.setInterval(loadBoard, 60_000);
  document.addEventListener("click", handleOutsideClick);
  document.addEventListener("keydown", handleEscape);
  document.addEventListener("fullscreenchange", handleFullscreenChange);
  window.addEventListener("resize", handleResize);
});

onBeforeUnmount(() => {
  window.clearInterval(refreshTimer);
  document.removeEventListener("click", handleOutsideClick);
  document.removeEventListener("keydown", handleEscape);
  document.removeEventListener("fullscreenchange", handleFullscreenChange);
  window.removeEventListener("resize", handleResize);
});
</script>

<template>
  <div class="ranking-public" :class="{ 'is-fullscreen': isFullscreen }">
    <div class="adventure-shell">
      <header class="adventure-header">
        <RouterLink class="adventure-brand" to="/index" aria-label="返回 CodeDog">
          <Gamepad2 aria-hidden="true" />
          <span>C++ 冒险者积分中心</span>
        </RouterLink>

        <div class="player-console">
          <span class="player-avatar">{{ rankingAvatarText(selectedStudent?.studentName) }}</span>
          <label>
            <span>当前学员</span>
            <select v-model="selectedStudentId" aria-label="选择我的姓名" @change="rememberSelection">
              <option v-for="row in rows" :key="row.studentId" :value="String(row.studentId)">{{ row.studentName }} · 第 {{ row.rank }} 名</option>
            </select>
          </label>
          <div class="player-score">
            <small>{{ selectedStudent ? `第 ${selectedStudent.rank} 名 · ${selectedStudent.levelName}` : "等待数据" }}</small>
            <strong>{{ selectedStudent ? `${selectedStudent.totalPoints} 积分` : "0 积分" }}</strong>
          </div>
          <button class="fullscreen-button" type="button" :title="isFullscreen ? '退出全屏' : '全屏显示'" :aria-label="isFullscreen ? '退出全屏' : '全屏显示'" @click="toggleFullscreen">
            <Minimize2 v-if="isFullscreen" aria-hidden="true" />
            <Maximize2 v-else aria-hidden="true" />
          </button>
        </div>
      </header>

      <main class="ranking-layout" @click.stop>
        <section class="game-panel ladder-panel" aria-labelledby="ladderTitle">
          <div class="game-panel-heading">
            <div><Trophy class="panel-icon" aria-hidden="true" /><div><h1 id="ladderTitle">学员积分天梯榜</h1><p>展示前 10 名与我的排名 · 共 {{ rows.length }} 名学员</p></div></div>
            <button class="ghost-button" type="button" :disabled="loading" @click="loadBoard"><RefreshCw :class="{ spin: loading }" aria-hidden="true" />刷新</button>
          </div>

          <div v-if="loading && !board" class="status" role="status">正在加载排行榜…</div>
          <div v-else-if="error" class="status error" role="alert">{{ error }}</div>
          <div v-else-if="!rows.length" class="status" role="status">暂无学员积分数据</div>
          <div v-else class="ladder-list" aria-label="前十名与我的排名">
            <template v-for="(row, index) in visibleRows" :key="row.studentId">
              <div v-if="showsSelectedSeparately && index === 10" class="omitted-ranks" aria-label="中间名次已省略">
                <span aria-hidden="true">•••</span>
                <strong>中间名次已省略</strong>
                <span aria-hidden="true">•••</span>
              </div>
              <article
              class="ladder-row"
              :class="[`level-${row.level}`, row.rank <= 3 ? `place-${row.rank}` : '', { selected: String(row.studentId) === selectedStudentId }]"
              :data-student-id="String(row.studentId)"
              tabindex="0"
              role="button"
              :aria-label="`查看${row.studentName}的积分构成`"
              @mouseenter="showDetails(row, $event)"
              @mouseleave="hideHover"
              @focusin="showDetails(row, $event)"
              @focusout="hideHover"
              @click="toggleRowDetails(row, $event)"
              @keydown.enter.prevent="toggleRowDetails(row, $event)"
              @keydown.space.prevent="toggleRowDetails(row, $event)"
            >
              <span class="ladder-rank">{{ row.rank }}</span>
              <span class="avatar">{{ rankingAvatarText(row.studentName) }}</span>
              <div class="student-copy"><div class="student-name">{{ row.studentName }}</div><span class="level-badge">{{ row.levelName }}</span></div>
              <strong class="ladder-points">{{ row.totalPoints }}<small>积分</small></strong>
            </article>
            </template>
          </div>
        </section>

        <section class="game-panel supply-panel" aria-labelledby="supplyTitle">
          <div class="game-panel-heading"><div><Backpack class="panel-icon" aria-hidden="true" /><div><h2 id="supplyTitle">积分补给站</h2><p>查看当前学员的积分构成与升级进度</p></div></div></div>
          <div class="supply-grid">
            <article class="supply-card"><Check class="supply-icon supply-cyan" aria-hidden="true" /><h3>课程完课积分</h3><strong>{{ selectedStudent?.completionPoints || 0 }} 积分</strong><button class="supply-action" type="button" @click="showSelectedDetails">查看明细</button></article>
            <article class="supply-card"><Keyboard class="supply-icon supply-pink" aria-hidden="true" /><h3>课上作业积分</h3><strong>{{ selectedStudent?.inclassPoints || 0 }} 积分</strong><button class="supply-action" type="button" @click="showSelectedDetails">查看明细</button></article>
            <article class="supply-card"><Gamepad2 class="supply-icon supply-green" aria-hidden="true" /><h3>课后作业积分</h3><strong>{{ selectedStudent?.homeworkPoints || 0 }} 积分</strong><button class="supply-action" type="button" @click="showSelectedDetails">查看明细</button></article>
            <article class="supply-card"><Star class="supply-icon supply-gold" aria-hidden="true" /><h3>{{ nextLevelName }}</h3><strong>{{ nextLevelPoints }}</strong><button class="supply-action" type="button" @click="showSelectedDetails">查看明细</button></article>
          </div>
          <div class="adventure-tip"><Info aria-hidden="true" /><p>{{ motivationText }}</p></div>
        </section>
      </main>

      <footer><span>{{ updatedText }}</span><span>前 10 名与我的排名 · 每 60 秒自动刷新</span></footer>
    </div>

    <aside v-if="detailRow" ref="popover" class="score-popover" :style="popoverStyle" role="tooltip" @click.stop>
      <h3>{{ detailRow.studentName }} · {{ detailRow.totalPoints }} 积分</h3>
      <dl>
        <div><dt>完课</dt><dd>{{ detailRow.completionPoints }}</dd></div>
        <div><dt>课上作业</dt><dd>{{ detailRow.inclassPoints }}</dd></div>
        <div><dt>课后作业</dt><dd>{{ detailRow.homeworkPoints }}</dd></div>
        <div><dt>综合正确率</dt><dd>{{ Number(detailRow.accuracyRate || 0).toFixed(1) }}%</dd></div>
        <div><dt>排名趋势</dt><dd>{{ rankingTrendView(detailRow).title }}</dd></div>
      </dl>
    </aside>
  </div>
</template>

<style scoped>
:global(body.ranking-public-page) {
  color: #f4f6ff;
  background: #101531;
}

.ranking-public {
  min-height: 100vh;
  color: #f4f6ff;
  background-color: #101531;
  background-image:
    linear-gradient(rgba(101, 91, 206, 0.09) 1px, transparent 1px),
    linear-gradient(90deg, rgba(101, 91, 206, 0.07) 1px, transparent 1px);
  background-size: 48px 48px;
  font-family: "Trebuchet MS", "PingFang SC", "Microsoft YaHei", sans-serif;
}

.ranking-public::before {
  position: fixed;
  z-index: 0;
  inset: 0;
  background: linear-gradient(135deg, rgba(42, 221, 255, 0.04), transparent 35%, rgba(121, 74, 255, 0.06));
  content: "";
  pointer-events: none;
}

button,
select {
  font: inherit;
  letter-spacing: 0;
}

button {
  cursor: pointer;
}

.adventure-shell {
  position: relative;
  z-index: 1;
  width: min(1480px, calc(100% - 40px));
  margin: 0 auto;
  padding: 20px 0 26px;
}

.adventure-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 76px;
  gap: 24px;
  padding-bottom: 14px;
  border-bottom: 1px dashed rgba(129, 106, 255, 0.56);
}

.adventure-brand {
  display: flex;
  align-items: center;
  gap: 12px;
  color: #39d9ff;
  font-size: 26px;
  font-weight: 900;
  text-decoration: none;
  text-shadow: 0 0 15px rgba(57, 217, 255, 0.58);
}

.adventure-brand svg {
  width: 32px;
  height: 32px;
}

.player-console {
  display: grid;
  grid-template-columns: 38px minmax(118px, 1fr) auto 36px;
  align-items: center;
  min-width: 430px;
  min-height: 54px;
  gap: 10px;
  padding: 7px 9px;
  border: 1px solid #6688ff;
  border-radius: 8px;
  background: #151b3c;
  box-shadow: 0 0 18px rgba(64, 132, 255, 0.18);
}

.player-avatar {
  display: grid;
  place-items: center;
  width: 36px;
  height: 36px;
  border: 2px solid #ffe494;
  border-radius: 50%;
  color: #fff;
  background: #f3a514;
  font-size: 12px;
  font-weight: 900;
}

.player-console label {
  display: grid;
  min-width: 0;
  gap: 2px;
}

.player-console label > span {
  color: #aab2d1;
  font-size: 10px;
}

.player-console select {
  min-width: 0;
  border: 0;
  outline: none;
  color: #fff;
  background: transparent;
  font-weight: 800;
}

.player-console select option {
  color: #fff;
  background: #151b3c;
}

.player-score {
  display: grid;
  gap: 2px;
  text-align: right;
}

.player-score small {
  color: #bc9bff;
  font-size: 10px;
}

.player-score strong {
  padding: 5px 12px;
  border-radius: 18px;
  color: #15172c;
  background: #ffcb24;
  box-shadow: 0 0 13px rgba(255, 203, 36, 0.6);
  font-size: 15px;
  white-space: nowrap;
}

.fullscreen-button {
  display: grid;
  place-items: center;
  width: 34px;
  height: 34px;
  border: 1px solid #455680;
  border-radius: 6px;
  color: #b9c7ef;
  background: #1d2648;
}

.fullscreen-button:hover,
.fullscreen-button:focus-visible {
  border-color: #35d8ff;
  outline: none;
  color: #35d8ff;
}

.fullscreen-button svg {
  width: 17px;
  height: 17px;
}

.ranking-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(360px, 0.95fr);
  width: 100%;
  gap: 24px;
  padding: 16px 0 22px;
}

.game-panel {
  min-width: 0;
  min-height: 610px;
  padding: 15px;
  border: 1px solid #3a506d;
  border-radius: 8px;
  background: #0d152b;
  box-shadow: inset 0 0 28px rgba(0, 0, 0, 0.18);
}

.game-panel-heading,
.game-panel-heading > div {
  display: flex;
  align-items: flex-start;
}

.game-panel-heading {
  justify-content: space-between;
  min-height: 42px;
  gap: 16px;
  margin-bottom: 12px;
}

.game-panel-heading > div {
  gap: 9px;
}

.game-panel-heading h1,
.game-panel-heading h2 {
  margin: 0;
  color: #b99bff;
  font-size: 18px;
  line-height: 1.25;
}

.game-panel-heading p {
  margin: 3px 0 0;
  color: #7483a9;
  font-size: 11px;
}

.panel-icon {
  width: 19px;
  height: 19px;
  color: #ffca25;
}

.ghost-button {
  display: inline-flex;
  align-items: center;
  height: 32px;
  gap: 6px;
  padding: 0 12px;
  border: 1px solid #40537a;
  border-radius: 5px;
  color: #aebce2;
  background: #192440;
  font-weight: 800;
}

.ghost-button:hover,
.ghost-button:focus-visible {
  border-color: #35d8ff;
  outline: none;
  color: #35d8ff;
}

.ghost-button:disabled {
  cursor: wait;
  opacity: 0.7;
}

.ghost-button svg {
  width: 15px;
  height: 15px;
}

.spin {
  animation: spin 0.8s linear infinite;
}

.status {
  display: grid;
  place-items: center;
  min-height: 92px;
  color: #7c8aac;
}

.status.error {
  color: #ff7891;
}

.ladder-list {
  display: grid;
  gap: 10px;
}

.omitted-ranks {
  display: grid;
  grid-template-columns: 1fr auto 1fr;
  align-items: center;
  min-height: 42px;
  gap: 12px;
  color: #66789d;
  font-size: 11px;
  text-align: center;
}

.omitted-ranks::before,
.omitted-ranks::after {
  height: 1px;
  background: linear-gradient(90deg, transparent, rgba(101, 122, 164, 0.55));
  content: "";
}

.omitted-ranks::after {
  background: linear-gradient(90deg, rgba(101, 122, 164, 0.55), transparent);
}

.omitted-ranks span {
  letter-spacing: 3px;
}

.ladder-row {
  position: relative;
  display: grid;
  grid-template-columns: 30px 42px minmax(0, 1fr) auto;
  align-items: center;
  min-height: 64px;
  gap: 11px;
  padding: 9px 13px;
  border-left: 4px solid #60708a;
  border-radius: 7px;
  outline: none;
  background: #1a2740;
  cursor: pointer;
  transition: transform 160ms ease, border-color 160ms ease, background 160ms ease;
}

.ladder-row:hover,
.ladder-row:focus-visible,
.ladder-row.selected {
  border-color: #32d9ff;
  background: #213351;
  transform: translateX(3px);
}

.ladder-row.place-1 {
  border-color: #ffc928;
  background: #202b46;
}

.ladder-row.place-2 {
  border-color: #c7d2e5;
}

.ladder-row.place-3 {
  border-color: #e87926;
}

.ladder-rank {
  color: #aeb9d1;
  font-size: 20px;
  font-weight: 900;
  text-align: center;
}

.place-1 .ladder-rank { color: #ffc928; }
.place-2 .ladder-rank { color: #d8e1f2; }
.place-3 .ladder-rank { color: #e87926; }

.avatar {
  display: grid;
  place-items: center;
  width: 40px;
  height: 40px;
  border: 1px solid #475b80;
  border-radius: 50%;
  color: #d7e3ff;
  background: #263756;
  font-size: 11px;
  font-weight: 900;
}

.student-copy {
  min-width: 0;
}

.student-name {
  margin: 0;
  color: #f3f6ff;
  font-family: "PingFang SC", "Noto Sans CJK SC", "Microsoft YaHei", sans-serif;
  font-size: 16px;
  font-weight: 700;
  line-height: 1.35;
  overflow-wrap: anywhere;
  text-shadow: none;
}

.level-badge {
  display: inline-block;
  margin-top: 4px;
  padding: 2px 7px;
  border-radius: 4px;
  color: #cfc2ff;
  background: #4b3f91;
  font-size: 9px;
}

.level-1 .student-name { color: #cbd1dc; }
.level-2 .student-name { color: #dfb08d; }
.level-3 .student-name { color: #f3f6ff; }
.level-4 .student-name { color: #ffe08a; text-shadow: 0 0 7px rgba(255, 202, 37, 0.18); }
.level-5 .student-name { color: #86c4ff; text-shadow: 0 0 7px rgba(76, 159, 255, 0.2); }
.level-6 .student-name { color: #9df5ff; text-shadow: 0 0 8px rgba(75, 221, 255, 0.28); }

.ladder-points {
  color: #39d9ff;
  font-family: Consolas, "SFMono-Regular", monospace;
  font-size: 21px;
  font-style: italic;
  text-shadow: 0 0 10px rgba(57, 217, 255, 0.35);
  white-space: nowrap;
}

.ladder-points small {
  margin-left: 2px;
  font-size: 10px;
  font-style: normal;
}

.supply-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.supply-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 178px;
  padding: 16px 12px;
  border: 1px solid #435776;
  border-radius: 8px;
  background: #1a263a;
  text-align: center;
}

.supply-card h3 {
  margin: 9px 0 6px;
  color: #f7f8ff;
  font-size: 13px;
}

.supply-card > strong {
  color: #ffca25;
  font-size: 14px;
}

.supply-icon {
  width: 30px;
  height: 30px;
}

.supply-cyan { color: #38d4ff; }
.supply-pink { color: #ff3978; }
.supply-green { color: #32c98a; }
.supply-gold { color: #ffd338; }

.supply-action {
  height: 30px;
  margin-top: 11px;
  padding: 0 14px;
  border: 0;
  border-radius: 5px;
  color: #fff;
  background: #2dc58d;
  box-shadow: 0 0 13px rgba(45, 197, 141, 0.42);
  font-size: 11px;
  font-weight: 900;
}

.supply-action:hover,
.supply-action:focus-visible {
  outline: 2px solid rgba(93, 255, 199, 0.38);
  outline-offset: 2px;
  background: #3cdaa0;
}

.adventure-tip {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 13px;
  padding: 11px 13px;
  border: 1px dashed #3e5477;
  color: #98a9c8;
  background: #111c33;
}

.adventure-tip svg {
  flex: 0 0 auto;
  width: 24px;
  height: 24px;
  padding: 5px;
  border-radius: 50%;
  color: #42dbff;
  background: #263b5d;
}

.adventure-tip p {
  margin: 0;
  font-size: 12px;
}

.score-popover {
  position: fixed;
  z-index: 30;
  width: min(290px, calc(100vw - 20px));
  padding: 14px 16px;
  border: 1px solid #43cdeb;
  border-radius: 7px;
  color: #fff;
  background: #111a33;
  box-shadow: 0 12px 30px rgba(0, 0, 0, 0.42);
  pointer-events: none;
}

.score-popover h3 {
  margin: 0 0 10px;
  color: #42dfff;
  font-size: 14px;
}

.score-popover dl {
  display: grid;
  gap: 7px;
  margin: 0;
}

.score-popover dl div {
  display: flex;
  justify-content: space-between;
  gap: 20px;
}

.score-popover dt { color: #aab5d0; }
.score-popover dd { margin: 0; color: #ffce3b; font-weight: 900; font-variant-numeric: tabular-nums; }

.adventure-shell footer {
  display: flex;
  justify-content: center;
  gap: 24px;
  padding: 14px;
  border-top: 1px dashed rgba(129, 106, 255, 0.42);
  color: #69799c;
  background: transparent;
  font-size: 11px;
}

.is-fullscreen .adventure-shell {
  width: calc(100% - 28px);
}

.is-fullscreen .adventure-header {
  min-height: 62px;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

@media (max-width: 900px) {
  .adventure-header {
    align-items: stretch;
    flex-direction: column;
  }

  .player-console {
    width: 100%;
    min-width: 0;
  }

  .ranking-layout {
    grid-template-columns: 1fr;
  }

  .supply-panel {
    order: -1;
  }

  .game-panel {
    min-height: 0;
  }
}

@media (max-width: 560px) {
  .adventure-shell {
    width: calc(100% - 20px);
    padding-top: 12px;
  }

  .adventure-brand {
    font-size: 20px;
  }

  .adventure-brand svg {
    width: 27px;
    height: 27px;
  }

  .player-console {
    grid-template-columns: 34px minmax(0, 1fr) 34px;
  }

  .player-score {
    grid-row: 2;
    grid-column: 1 / -1;
    display: flex;
    align-items: center;
    justify-content: space-between;
    text-align: left;
  }

  .ranking-layout {
    gap: 14px;
  }

  .game-panel {
    padding: 12px;
  }

  .supply-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 8px;
  }

  .supply-card {
    min-height: 150px;
    padding: 12px 8px;
  }

  .ladder-row {
    grid-template-columns: 27px 38px minmax(0, 1fr) auto;
    padding-inline: 9px;
  }

  .avatar {
    width: 36px;
    height: 36px;
  }

  .student-name {
    font-size: 14px;
  }

  .ladder-points {
    font-size: 17px;
  }

  .adventure-shell footer {
    align-items: center;
    flex-direction: column;
    gap: 4px;
  }
}

@media (prefers-reduced-motion: reduce) {
  .ladder-row {
    transition: none;
  }

  .ladder-row:hover,
  .ladder-row:focus-visible,
  .ladder-row.selected {
    transform: none;
  }

  .spin {
    animation: none;
  }
}
</style>
