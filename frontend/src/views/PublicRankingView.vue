<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from "vue";
import {
  ArrowRight,
  CircleCheckBig,
  EllipsisVertical,
  Gift,
  History,
  Code2,
  Megaphone,
  RefreshCw,
  LogOut,
  Sparkles,
  Trophy,
  X
} from "@lucide/vue";
import { api, jsonBody } from "../api";
import {
  rankingAvatarText, rankingRedemptionStatus, rankingRedemptionTime, rankingTrendView
} from "../rankingAdmin.js";

const board = ref(null);
const announcement = ref("");
const rewards = ref([]);
const redemptions = ref([]);
const balance = ref({ earnedPoints: 0, spentPoints: 0, availablePoints: 0 });
const redeemOpen = ref(false);
const selectedReward = ref(null);
const redeemSaving = ref(false);
const redeemError = ref("");
const redeemNotice = ref("");
const opportunities = ref([]);
const opportunityLoading = ref(false);
const opportunityError = ref("");
const loading = ref(false);
const error = ref("");
const selectedStudentId = ref("");
const detailRow = ref(null);
const pinnedDetailId = ref("");
const popover = ref(null);
const popoverStyle = ref({});
const studentSession = ref(null);
const authReady = ref(false);
const authLoading = ref(false);
const loginPhone = ref("");
const loginPassword = ref("");
const loginError = ref("");
const passwordPromptOpen = ref(false);
const newPassword = ref("");
const confirmPassword = ref("");
const passwordChangeError = ref("");
const passwordChangeLoading = ref(false);
let refreshTimer;
let announcementStream;

const rows = computed(() => board.value?.rankings || []);
const selectedIndex = computed(() => rows.value.findIndex((row) => String(row.studentId) === String(selectedStudentId.value)));
const visibleRows = computed(() => rows.value);
const visibleOpportunities = computed(() => opportunities.value.slice(0, 3));
const opportunityComplete = computed(() => visibleOpportunities.value[0]?.type === "COMPLETE");
const showsSelectedSeparately = computed(() => visibleRows.value.length > 10 && Boolean(selectedStudentId.value));
const selectedStudent = computed(() => selectedIndex.value >= 0 ? rows.value[selectedIndex.value] : null);
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

let opportunityRequest = 0;
async function loadOpportunities(studentId = selectedStudentId.value) {
  if (!studentId) return;
  const request = ++opportunityRequest;
  opportunityLoading.value = true;
  opportunityError.value = "";
  try {
    const result = await api(`/public/rankings/student-opportunities?studentId=${encodeURIComponent(studentId)}`);
    if (request === opportunityRequest) opportunities.value = result?.opportunities || [];
  } catch (failure) {
    if (request === opportunityRequest) { opportunities.value = []; opportunityError.value = failure.message || "提分任务加载失败"; }
  } finally {
    if (request === opportunityRequest) opportunityLoading.value = false;
  }
}

async function loadBoard() {
  loading.value = true;
  error.value = "";
  try {
    const [boardValue, rewardValues, balanceValue, redemptionValues] = await Promise.all([
      api("/public/rankings/student-board"), api("/public/rankings/rewards"),
      api("/public/rankings/student-balance"), api("/public/rankings/student-redemptions")
    ]);
    board.value = boardValue;
    rewards.value = rewardValues || [];
    balance.value = balanceValue || { earnedPoints: 0, spentPoints: 0, availablePoints: 0 };
    redemptions.value = redemptionValues || [];
    selectedStudentId.value = String(studentSession.value?.studentId || "");
    loadOpportunities(selectedStudentId.value);
  } catch (failure) {
    error.value = failure.message || "排行榜加载失败";
    if (failure.status === 401) return handleSessionExpired();
  } finally {
    loading.value = false;
  }
}

async function loadAnnouncement() {
  if (!studentSession.value) return;
  try {
    const value = await api("/public/rankings/announcement");
    announcement.value = value?.text || "";
  } catch (failure) {
    if (failure.status === 401) handleSessionExpired();
  }
}

function closeAnnouncementStream() {
  if (announcementStream) announcementStream.close();
  announcementStream = null;
}

function connectAnnouncementStream() {
  closeAnnouncementStream();
  if (!studentSession.value) return;
  loadAnnouncement();
  if (typeof EventSource === "undefined") return;
  const stream = new EventSource("/api/public/rankings/announcement/events");
  announcementStream = stream;
  stream.addEventListener("announcement", (event) => {
    try {
      const value = JSON.parse(event.data);
      announcement.value = value?.text || "";
    } catch (_error) {
      // Ignore malformed event data and keep the current announcement visible.
    }
  });
  stream.onerror = () => {
    if (announcementStream !== stream) stream.close();
  };
}


function handleSessionExpired() {
  studentSession.value = null;
  board.value = null;
  redemptions.value = [];
  selectedStudentId.value = "";
  authReady.value = true;
  loginError.value = "登录已失效，请重新登录";
  window.clearInterval(refreshTimer);
  closeAnnouncementStream();
}

function startRefreshTimers() {
  window.clearInterval(refreshTimer);
  refreshTimer = window.setInterval(loadBoard, 60_000);
  connectAnnouncementStream();
}

async function loadSession() {
  try {
    studentSession.value = await api("/public/rankings/student-auth/session");
    selectedStudentId.value = String(studentSession.value.studentId);
    authReady.value = true;
    await loadBoard();
    startRefreshTimers();
  } catch (failure) {
    if (failure.status !== 401) loginError.value = failure.message || "登录状态加载失败";
    authReady.value = true;
  }
}

async function login() {
  authLoading.value = true;
  loginError.value = "";
  try {
    studentSession.value = await api("/public/rankings/student-auth/login", {
      method: "POST",
      body: jsonBody({ phone: loginPhone.value.trim(), password: loginPassword.value })
    });
    selectedStudentId.value = String(studentSession.value.studentId);
    loginPassword.value = "";
    await loadBoard();
    openPasswordPrompt(studentSession.value);
    startRefreshTimers();
  } catch (failure) {
    loginError.value = failure.message || "登录失败，请检查手机号和密码";
  } finally {
    authLoading.value = false;
  }
}

function openPasswordPrompt(session) {
  passwordPromptOpen.value = Boolean(session?.mustChangePassword);
  passwordChangeError.value = "";
  newPassword.value = "";
  confirmPassword.value = "";
}

function deferPasswordChange() {
  passwordPromptOpen.value = false;
  passwordChangeError.value = "";
}

async function submitPasswordChange() {
  passwordChangeError.value = "";
  if (newPassword.value.length < 6 || newPassword.value.length > 72) {
    passwordChangeError.value = "新密码长度应为 6-72 个字符";
    return;
  }
  if (newPassword.value !== confirmPassword.value) {
    passwordChangeError.value = "两次输入的新密码不一致";
    return;
  }
  passwordChangeLoading.value = true;
  try {
    studentSession.value = await api("/public/rankings/student-auth/change-password", {
      method: "POST",
      body: jsonBody({ password: newPassword.value })
    });
    passwordPromptOpen.value = false;
    newPassword.value = "";
    confirmPassword.value = "";
  } catch (failure) {
    passwordChangeError.value = failure.message || "密码修改失败，请稍后重试";
  } finally {
    passwordChangeLoading.value = false;
  }
}

async function logout() {
  try { await api("/public/rankings/student-auth/logout", { method: "POST" }); }
  catch (_failure) { /* 清理本地学生会话仍然安全 */ }
  handleSessionExpired();
}

function validateLogin(event) {
  if (!loginPhone.value.trim() || !loginPassword.value) {
    event.preventDefault();
    loginError.value = "请输入手机号和密码";
  }
}
function positionPopover(anchor) {
  if (!anchor) return;
  const rect = anchor.getBoundingClientRect();
  const width = Math.min(360, window.innerWidth - 20);
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

function hideHover() {
  if (!pinnedDetailId.value) detailRow.value = null;
}

function closeDetails() {
  detailRow.value = null;
  pinnedDetailId.value = "";
}

async function openRewardRedeem(reward) {
  selectedReward.value = reward;
  redeemError.value = "";
  redeemNotice.value = "";
  redeemOpen.value = true;
  try {
    balance.value = await api("/public/rankings/student-balance");
  } catch (failure) {
    redeemError.value = failure.message || "积分余额加载失败，请重试";
  }
}

function closeRewardRedeem(force = false) {
  if (redeemSaving.value && !force) return;
  redeemOpen.value = false;
  selectedReward.value = null;
  redeemError.value = "";
}

async function redeemReward() {
  if (!selectedReward.value || redeemSaving.value) return;
  redeemSaving.value = true;
  redeemError.value = "";
  try {
    const created = await api(`/public/rankings/rewards/${selectedReward.value.id}/redeem`, { method: "POST" });
    redemptions.value = [created, ...redemptions.value.filter((item) => item.id !== created.id)];
    balance.value = {
      ...balance.value,
      spentPoints: Number(balance.value.spentPoints || 0) + Number(created.pointsSpent || 0),
      availablePoints: Math.max(0, Number(balance.value.availablePoints || 0) - Number(created.pointsSpent || 0))
    };
    redeemNotice.value = `已兑换“${selectedReward.value.name}”，请联系老师领取。`;
    closeRewardRedeem(true);
    try {
      const [balanceValue, redemptionValues, boardValue] = await Promise.all([
        api("/public/rankings/student-balance"), api("/public/rankings/student-redemptions"), api("/public/rankings/student-board")
      ]);
      balance.value = balanceValue;
      board.value = boardValue;
      redemptions.value = redemptionValues || [];
    } catch (refreshFailure) {
      if (refreshFailure.status === 401) handleSessionExpired();
    }
  } catch (failure) {
    redeemError.value = failure.message || "兑换失败，请刷新后重试";
  } finally {
    redeemSaving.value = false;
  }
}

function toggleRowDetails(row, event) {
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
  if (event.key === "Escape") { closeDetails(); closeRewardRedeem(); }
}

function handleResize() {
  if (!pinnedDetailId.value) {
    closeDetails();
    return;
  }
  const anchor = document.querySelector(`[data-student-id="${CSS.escape(pinnedDetailId.value)}"]`);
  if (anchor) positionPopover(anchor);
}

onMounted(() => {
  loadSession();
  document.addEventListener("click", handleOutsideClick);
  document.addEventListener("keydown", handleEscape);
  window.addEventListener("resize", handleResize);
});

onBeforeUnmount(() => {
  window.clearInterval(refreshTimer);
  closeAnnouncementStream();
  document.removeEventListener("click", handleOutsideClick);
  document.removeEventListener("keydown", handleEscape);
  window.removeEventListener("resize", handleResize);
});
</script>

<template>
  <div class="ranking-public">
    <div class="adventure-shell">
      <header class="adventure-header">
        <div class="adventure-brand">
          <Code2 aria-hidden="true" />
          <span>C++积分中心</span>
        </div>

        <div v-if="studentSession" class="player-console">
          <span class="player-avatar">{{ rankingAvatarText(selectedStudent?.studentName) }}</span>
          <div class="player-identity">
            <span>当前学员</span>
            <strong>{{ studentSession.studentName }}</strong><small>ID {{ studentSession.studentId }}</small>
          </div>
          <div class="player-score">
            <small>{{ selectedStudent ? `第 ${selectedStudent.rank} 名 · ${selectedStudent.levelName}` : "等待数据" }}</small>
            <strong>{{ balance.availablePoints }} 可用积分</strong>
          </div>
          <button class="logout-button" type="button" title="退出学生登录" aria-label="退出学生登录" @click="logout"><LogOut aria-hidden="true" /></button>
        </div>
      </header>

      <div v-if="!authReady" class="auth-status" role="status">正在检查学生登录状态…</div>
      <form v-else-if="!studentSession" class="student-login-panel" @submit.prevent="login">
        <div class="login-mark"><Code2 aria-hidden="true" /></div>
        <div class="login-copy"><h1>登录查看你的排名</h1><p>使用老师登记的手机号进入专属积分榜。</p></div>
        <label class="login-field"><span>手机号</span><input v-model="loginPhone" inputmode="numeric" autocomplete="username" maxlength="11" placeholder="请输入编程猫注册手机号" /></label>
        <label class="login-field"><span>密码</span><input v-model="loginPassword" type="password" autocomplete="current-password" maxlength="72" placeholder="请输入密码" /></label>
        <p v-if="loginError" class="login-error" role="alert">{{ loginError }}</p>
        <button class="login-button" type="submit" :disabled="authLoading"><RefreshCw v-if="authLoading" class="spin" aria-hidden="true" /><span>{{ authLoading ? "正在登录…" : "进入积分榜" }}</span></button>
      </form>
      <div v-if="redeemOpen && selectedReward" class="reward-redeem-backdrop" @click.self="closeRewardRedeem">
        <section class="reward-redeem-modal" role="dialog" aria-modal="true" aria-labelledby="rewardRedeemTitle" @click.stop>
          <header><div><span class="reward-redeem-mark"><Gift aria-hidden="true" /></span><div><p class="password-modal-kicker">积分兑换</p><h2 id="rewardRedeemTitle">确认兑换奖品</h2></div></div><button class="icon-button" type="button" title="关闭" @click="closeRewardRedeem"><X :size="17" /></button></header>
          <div class="reward-redeem-body"><div class="reward-redeem-item"><div class="reward-image"><img v-if="selectedReward.imageUrl" :src="selectedReward.imageUrl" :alt="selectedReward.name"><Gift v-else aria-hidden="true" /></div><div><strong>{{ selectedReward.name }}</strong><small>需要 {{ selectedReward.requiredPoints }} 积分</small></div></div><p>当前可用积分：<b>{{ balance.availablePoints }}</b>；兑换后剩余：<b>{{ Math.max(0, balance.availablePoints - selectedReward.requiredPoints) }}</b></p><p v-if="redeemError" class="redeem-error" role="alert">{{ redeemError }}</p></div>
          <footer><button class="password-later-button" type="button" @click="closeRewardRedeem">取消</button><button class="login-button" type="button" :disabled="redeemSaving || balance.availablePoints < selectedReward.requiredPoints" @click="redeemReward"><RefreshCw v-if="redeemSaving" class="spin" aria-hidden="true" /><span>{{ redeemSaving ? "兑换中…" : "确认兑换" }}</span></button></footer>
        </section>
      </div>
      <div v-if="passwordPromptOpen" class="password-modal-backdrop" @click.self="deferPasswordChange">
        <section class="password-modal" role="dialog" aria-modal="true" aria-labelledby="passwordModalTitle" @click.stop>
          <div class="password-modal-mark"><Code2 aria-hidden="true" /></div>
          <div class="password-modal-copy">
            <p class="password-modal-kicker">账号安全提醒</p>
            <h2 id="passwordModalTitle">请修改初始密码</h2>
            <p>请修改初始密码，建议与上课密码一致</p>
          </div>
          <form class="password-change-form" @submit.prevent="submitPasswordChange">
            <label class="login-field"><span>新密码</span><input v-model="newPassword" type="password" autocomplete="new-password" maxlength="72" placeholder="请输入新密码" /></label>
            <label class="login-field"><span>确认新密码</span><input v-model="confirmPassword" type="password" autocomplete="new-password" maxlength="72" placeholder="请再次输入新密码" /></label>
            <p v-if="passwordChangeError" class="login-error" role="alert">{{ passwordChangeError }}</p>
            <div class="password-modal-actions">
              <button class="password-later-button" type="button" @click="deferPasswordChange">暂不修改</button>
              <button class="login-button" type="submit" :disabled="passwordChangeLoading"><RefreshCw v-if="passwordChangeLoading" class="spin" aria-hidden="true" /><span>{{ passwordChangeLoading ? "保存中…" : "保存新密码" }}</span></button>
            </div>
          </form>
        </section>
      </div>
      <div v-if="studentSession && announcement" class="board-announcement"><Megaphone aria-hidden="true"/><p>{{ announcement }}</p></div>

      <main v-if="studentSession" class="ranking-layout" @click.stop>
        <section class="game-panel ladder-panel" aria-labelledby="ladderTitle">
          <div class="game-panel-heading">
            <div><Trophy class="panel-icon" aria-hidden="true" /><div><h1 id="ladderTitle">学员积分天梯榜</h1><p>展示前 10 名与我的排名 · 共 {{ board ? board.studentCount : 0 }} 名学员</p></div></div>
            <button class="ghost-button" type="button" :disabled="loading" @click="loadBoard"><RefreshCw :class="{ spin: loading }" aria-hidden="true" />刷新</button>
          </div>

          <div v-if="loading && !board" class="status" role="status">正在加载排行榜…</div>
          <div v-else-if="error" class="status error" role="alert">{{ error }}</div>
          <div v-else-if="!rows.length" class="status" role="status">暂无学员积分数据</div>
          <div v-else class="ladder-list" aria-label="前十名与我的排名">
            <template v-for="(row, index) in visibleRows" :key="row.studentId">
              <div v-if="showsSelectedSeparately && index === 10" class="omitted-ranks" aria-label="中间名次已省略"><EllipsisVertical aria-hidden="true" /></div>
              <article
              class="ladder-row"
              :class="[`level-${row.level}`, row.rank <= 3 ? `place-${row.rank}` : '', { selected: String(row.studentId) === selectedStudentId }]"
              :data-student-id="String(row.studentId)"
              tabindex="0"
              role="button"
              :aria-label="String(row.studentId) === selectedStudentId ? `查看我的积分构成和提分任务` : `查看${row.studentName}的积分构成`"
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
              <strong class="ladder-points">{{ row.availablePoints ?? row.totalPoints }}<small>积分</small></strong>
            </article>
              <div v-if="showsSelectedSeparately && index === 10" class="omitted-ranks" aria-label="后续名次已省略"><EllipsisVertical aria-hidden="true" /></div>
            </template>
          </div>
        </section>

        <section class="game-panel supply-panel" aria-labelledby="rewardVaultTitle">
          <div class="game-panel-heading reward-panel-heading">
            <div><Gift class="panel-icon" aria-hidden="true" /><div><h2 id="rewardVaultTitle">冒险奖品库</h2><p>点击奖品查看兑换提示</p></div></div>
            <small>{{ rewards.length }} 件奖品</small>
          </div>
          <p v-if="redeemNotice" class="redeem-notice" role="status">{{ redeemNotice }}</p>
            <div v-if="rewards.length" class="reward-grid">
              <article v-for="reward in rewards" :key="reward.id" class="reward-card" role="button" tabindex="0" @click="openRewardRedeem(reward)" @keydown.enter.prevent="openRewardRedeem(reward)">
                <div class="reward-image">
                  <img v-if="reward.imageUrl" :src="reward.imageUrl" :alt="reward.name">
                  <Gift v-else aria-hidden="true" />
                </div>
                <div class="reward-copy"><h4>{{ reward.name }}</h4><strong>{{ reward.requiredPoints }}<small> 积分</small></strong></div>
              </article>
            </div>
            <div v-else class="reward-empty"><Gift aria-hidden="true" /><span><strong>奖品正在补货</strong><small>老师添加奖品后会显示在这里</small></span></div>

          <section class="redemption-history" aria-labelledby="redemptionHistoryTitle">
            <div class="redemption-history-heading">
              <div><History aria-hidden="true" /><div><h3 id="redemptionHistoryTitle">我的兑换</h3><p>历史商品按兑换时间倒序</p></div></div>
              <small>{{ redemptions.length }} 条记录</small>
            </div>
            <ol v-if="redemptions.length" class="redemption-list">
              <li v-for="item in redemptions" :key="item.id">
                <span class="redemption-mark" :class="rankingRedemptionStatus(item.status).tone">
                  <CircleCheckBig v-if="item.status === 'FULFILLED'" aria-hidden="true" />
                  <Gift v-else aria-hidden="true" />
                </span>
                <div class="redemption-copy">
                  <strong>{{ item.rewardName }}</strong>
                  <time :datetime="item.redeemedAt">{{ rankingRedemptionTime(item.redeemedAt) }}</time>
                  <div class="redemption-balance" :aria-label="`兑换前 ${item.balanceBefore} 积分，兑换后 ${item.balanceAfter} 积分`">
                    <span>兑换前 <b>{{ item.balanceBefore }}</b></span>
                    <ArrowRight aria-hidden="true" />
                    <span>兑换后 <b>{{ item.balanceAfter }}</b></span>
                  </div>
                </div>
                <div class="redemption-meta">
                  <strong>-{{ item.pointsSpent }} 积分</strong>
                  <span :class="rankingRedemptionStatus(item.status).tone">{{ rankingRedemptionStatus(item.status).label }}</span>
                </div>
              </li>
            </ol>
            <div v-else class="redemption-empty"><History aria-hidden="true" /><span>还没有兑换记录</span></div>
          </section>
        </section>
      </main>

      <footer v-if="studentSession"><span>{{ updatedText }}</span><span>前 10 名与我的排名 · 每 60 秒自动刷新</span></footer>
    </div>

    <aside v-if="detailRow" ref="popover" class="score-popover" :style="popoverStyle" role="tooltip" @click.stop>
      <h3>{{ detailRow.studentName }} · {{ detailRow.availablePoints ?? detailRow.totalPoints }} 积分</h3>
      <dl>
        <div><dt>完课</dt><dd>{{ detailRow.completionPoints }}</dd></div>
        <div><dt>课上作业</dt><dd>{{ detailRow.inclassPoints }}</dd></div>
        <div><dt>课后作业</dt><dd>{{ detailRow.homeworkPoints }}</dd></div>
        <div><dt>综合正确率</dt><dd>{{ Number(detailRow.accuracyRate || 0).toFixed(1) }}%</dd></div>
        <div><dt>排名趋势</dt><dd>{{ rankingTrendView(detailRow).title }}</dd></div>
      </dl>
      <section v-if="String(detailRow.studentId) === selectedStudentId" class="score-opportunities" :class="{ complete: opportunityComplete }">
        <header><Sparkles aria-hidden="true" /><div><h4>还能这样赚积分</h4><p>根据最近同步的学习数据</p></div></header>
        <div v-if="opportunityLoading" class="score-opportunity-state"><RefreshCw class="spin" aria-hidden="true" />正在分析</div>
        <div v-else-if="opportunityError" class="score-opportunity-state error">{{ opportunityError }}</div>
        <div v-else class="score-opportunity-list">
          <article v-for="task in visibleOpportunities" :key="task.type">
            <CircleCheckBig v-if="task.type === 'COMPLETE'" aria-hidden="true" />
            <span v-else class="opportunity-dot" aria-hidden="true"></span>
            <div><strong>{{ task.title }}</strong><p>{{ task.description }}</p></div>
          </article>
        </div>
      </section>
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
.auth-status {
  display: grid;
  min-height: 280px;
  place-items: center;
  color: #9aa9c7;
}

.student-login-panel {
  display: grid;
  grid-template-columns: 86px minmax(0, 1fr) minmax(310px, 360px);
  align-items: center;
  gap: 14px 22px;
  max-width: 980px;
  min-height: 360px;
  margin: 76px auto 112px;
  padding: 42px 46px 46px;
  position: relative;
  overflow: hidden;
  border: 1px solid rgba(67, 215, 255, 0.55);
  border-radius: 22px;
  background: linear-gradient(135deg, rgba(22, 37, 79, 0.96), rgba(10, 19, 47, 0.98) 62%);
  box-shadow: 0 30px 90px rgba(2, 8, 29, 0.58), inset 0 0 0 1px rgba(255, 255, 255, 0.045), inset 0 0 52px rgba(44, 117, 255, 0.13);
}

.student-login-panel::before {
  position: absolute;
  inset: 10px;
  border: 1px solid rgba(133, 158, 255, 0.18);
  border-radius: 15px;
  content: "";
  pointer-events: none;
}

.student-login-panel::after {
  position: absolute;
  right: -90px;
  top: -120px;
  width: 330px;
  height: 330px;
  border: 1px solid rgba(67, 215, 255, 0.2);
  border-radius: 50%;
  box-shadow: 0 0 0 24px rgba(67, 215, 255, 0.035), 0 0 0 48px rgba(67, 215, 255, 0.025);
  content: "";
  pointer-events: none;
}

.login-mark {
  display: grid;
  width: 78px;
  height: 78px;
  place-items: center;
  z-index: 1;
  border: 1px solid rgba(89, 224, 255, 0.9);
  border-radius: 21px;
  color: #43d7ff;
  background: linear-gradient(145deg, #173d67, #112449);
  box-shadow: 0 0 0 5px rgba(67, 215, 255, 0.08), 0 0 32px rgba(67, 215, 255, 0.3);
}

.login-mark svg { width: 38px; height: 38px; }
.login-copy { z-index: 1; min-width: 0; padding-right: 18px; }
.login-copy h1 { max-width: 440px; margin: 0; color: #f4f8ff; font-size: clamp(28px, 2.6vw, 38px); font-weight: 900; letter-spacing: -0.03em; line-height: 1.12; text-shadow: 0 0 25px rgba(116, 181, 255, 0.22); }
.login-copy > p:last-child { max-width: 370px; margin: 17px 0 0; color: #91a6cd; font-size: 14px; line-height: 1.7; }
.login-field { display: grid; grid-column: 3; gap: 7px; min-width: 0; z-index: 1; }
.login-field span { color: #a9bbda; font-size: 11px; font-weight: 800; letter-spacing: 0.04em; }
.login-field input { box-sizing: border-box; width: 100%; height: 48px; padding: 0 15px; border: 1px solid #3f5e91; border-radius: 10px; outline: none; color: #f4f7ff; background: rgba(7, 17, 42, 0.82); font-size: 14px; transition: border-color 160ms ease, box-shadow 160ms ease, background 160ms ease; }
.login-field input::placeholder { color: #5f7096; }
.login-field input:focus { border-color: #43d7ff; background: rgba(8, 23, 52, 0.96); box-shadow: 0 0 0 3px rgba(67, 215, 255, 0.14), 0 0 20px rgba(67, 215, 255, 0.12); }
.login-error { grid-column: 3; z-index: 1; margin: 0; color: #ff91a5; font-size: 11px; line-height: 1.45; }
.login-button { grid-column: 3; z-index: 1; display: inline-flex; align-items: center; justify-content: center; gap: 8px; height: 48px; border: 0; border-radius: 10px; color: #07152f; background: linear-gradient(110deg, #5ae5ff, #78c9ff 55%, #9c9aff); box-shadow: 0 8px 24px rgba(62, 186, 255, 0.25); font-weight: 900; transition: transform 160ms ease, box-shadow 160ms ease, filter 160ms ease; }
.login-button:hover, .login-button:focus-visible { filter: brightness(1.08); outline: 2px solid rgba(114, 227, 255, 0.34); outline-offset: 3px; box-shadow: 0 11px 30px rgba(62, 186, 255, 0.36); transform: translateY(-1px); }
.login-button:disabled { cursor: wait; opacity: 0.7; transform: none; }

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

.player-identity { display: grid; min-width: 0; gap: 2px; }
.player-identity > span { color: #aab2d1; font-size: 10px; }
.player-identity strong { overflow: hidden; color: #f4f6ff; font-size: 14px; text-overflow: ellipsis; white-space: nowrap; }
.player-identity small { color: #7f91b9; font-size: 9px; }

.logout-button {
  display: grid;
  width: 34px;
  height: 34px;
  place-items: center;
  border: 1px solid #455680;
  border-radius: 6px;
  color: #b9c7ef;
  background: #1d2648;
}
.logout-button:hover, .logout-button:focus-visible { border-color: #ff7891; outline: none; color: #ff9aad; }
.logout-button svg { width: 16px; height: 16px; }
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

.board-announcement {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  margin-top: 14px;
  padding: 11px 14px;
  border: 1px solid rgba(255, 202, 37, 0.32);
  border-radius: 6px;
  color: #d8e1f4;
  background: rgba(255, 202, 37, 0.07);
}

.board-announcement svg { width: 18px; height: 18px; flex: 0 0 auto; color: #ffca25; }
.board-announcement p { margin: 0; font-size: 12px; line-height: 1.55; white-space: pre-wrap; overflow-wrap: anywhere; }

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

.supply-panel {
  align-self: start;
  min-height: 0;
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
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 28px;
  color: #66789d;
}

.omitted-ranks svg {
  width: 22px;
  height: 22px;
  stroke-width: 3;
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

.earning-guide {
  margin-top: 18px;
  padding-top: 16px;
  border-top: 1px dashed #3e5477;
}

.earning-guide-heading,
.earning-guide-heading > div,
.earning-task,
.earning-state {
  display: flex;
  align-items: center;
}

.earning-guide-heading { justify-content: space-between; gap: 12px; margin-bottom: 11px; }
.earning-guide-heading > div { gap: 9px; }
.earning-guide-heading svg { width: 20px; height: 20px; color: #39d9ff; }
.earning-guide-heading h3 { margin: 0; color: #f3f6ff; font-size: 15px; }
.earning-guide-heading p { margin: 3px 0 0; color: #7483a9; font-size: 10px; }
.earning-guide-heading small { max-width: 120px; overflow: hidden; color: #9aabca; font-size: 10px; text-overflow: ellipsis; white-space: nowrap; }

.earning-list { display: grid; gap: 8px; }
.earning-task {
  min-height: 66px;
  gap: 11px;
  padding: 10px 12px;
  border: 1px solid #344e70;
  border-left: 3px solid #39d9ff;
  border-radius: 6px;
  background: #142139;
}

.earning-task-mark {
  display: grid;
  flex: 0 0 auto;
  width: 29px;
  height: 29px;
  place-items: center;
  border: 1px solid #3a7290;
  border-radius: 50%;
  color: #49dcff;
  background: #172f49;
  font-size: 12px;
}

.earning-task-mark svg { width: 17px; height: 17px; }
.earning-task > div { min-width: 0; }
.earning-task h4 { margin: 0 0 4px; color: #f5f7ff; font-size: 12px; }
.earning-task p { margin: 0; color: #9baac7; font-size: 10px; line-height: 1.5; overflow-wrap: anywhere; }
.earning-list.complete .earning-task { border-color: #2b715f; border-left-color: #35d69b; background: #122d2c; }
.earning-list.complete .earning-task-mark { border-color: #2c8c70; color: #54e8b3; background: #173d36; }

.earning-state {
  min-height: 66px;
  justify-content: center;
  gap: 8px;
  border: 1px dashed #354b6e;
  border-radius: 6px;
  color: #8191b2;
  background: #111c33;
  font-size: 11px;
}

.earning-state svg { width: 16px; height: 16px; }
.earning-error { color: #ff8da2; }

.reward-vault {
  margin-top: 18px;
  padding-top: 16px;
  border-top: 1px dashed #3e5477;
}

.reward-vault-heading,
.reward-vault-heading > div,
.reward-card,
.reward-empty {
  display: flex;
  align-items: center;
}

.reward-vault-heading {
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.reward-vault-heading > div { gap: 9px; }
.reward-vault-heading svg { width: 20px; height: 20px; color: #ffca25; }
.reward-vault-heading h3 { margin: 0; color: #f3f6ff; font-size: 15px; }
.reward-vault-heading p { margin: 3px 0 0; color: #7483a9; font-size: 10px; }
.reward-vault-heading small { color: #8fa0c2; font-size: 10px; white-space: nowrap; }

.reward-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.reward-card {
  min-width: 0;
  min-height: 86px;
  gap: 12px;
  padding: 8px;
  border: 1px solid #354c70;
  border-radius: 7px;
  background: #16233b;
}

.reward-image {
  display: grid;
  flex: 0 0 auto;
  width: 68px;
  height: 68px;
  overflow: hidden;
  place-items: center;
  border: 1px solid #465d82;
  border-radius: 6px;
  color: #ffca25;
  background: #0f1a31;
}

.reward-image img { width: 100%; height: 100%; object-fit: cover; }
.reward-image svg { width: 26px; height: 26px; }
.reward-copy { min-width: 0; }
.reward-copy h4 { margin: 0 0 7px; color: #f5f7ff; font-size: 13px; line-height: 1.35; overflow-wrap: anywhere; }
.reward-copy strong { color: #39d9ff; font-family: Consolas, "SFMono-Regular", monospace; font-size: 17px; font-variant-numeric: tabular-nums; }
.reward-copy strong small { color: #91a2c4; font-family: inherit; font-size: 9px; }

.reward-empty {
  min-height: 84px;
  justify-content: center;
  gap: 11px;
  border: 1px dashed #354b6e;
  border-radius: 7px;
  color: #7082a7;
  background: #111c33;
}

.reward-empty > svg { width: 24px; height: 24px; }
.reward-empty strong,
.reward-empty small { display: block; }
.reward-empty strong { color: #a9b5ce; font-size: 12px; }
.reward-empty small { margin-top: 3px; font-size: 10px; }

.redemption-history {
  margin-top: 18px;
  padding-top: 16px;
  border-top: 1px dashed #3e5477;
}

.redemption-history-heading,
.redemption-history-heading > div,
.redemption-list li,
.redemption-mark,
.redemption-empty {
  display: flex;
  align-items: center;
}

.redemption-history-heading {
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 10px;
}

.redemption-history-heading > div { min-width: 0; gap: 9px; }
.redemption-history-heading svg { width: 19px; height: 19px; flex: 0 0 auto; color: #55ddff; }
.redemption-history-heading h3 { margin: 0; color: #f3f6ff; font-size: 15px; }
.redemption-history-heading p { margin: 3px 0 0; color: #7483a9; font-size: 10px; }
.redemption-history-heading > small { color: #8fa0c2; font-size: 10px; white-space: nowrap; }

.redemption-list {
  max-height: 330px;
  margin: 0;
  padding: 0;
  overflow-y: auto;
  border-top: 1px solid #263a59;
  list-style: none;
  scrollbar-color: #3e5d85 transparent;
  scrollbar-width: thin;
}

.redemption-list li {
  min-width: 0;
  min-height: 72px;
  gap: 10px;
  padding: 9px 2px;
  border-bottom: 1px solid #263a59;
}

.redemption-mark {
  justify-content: center;
  width: 34px;
  height: 34px;
  flex: 0 0 auto;
  border: 1px solid rgba(255, 202, 37, 0.34);
  border-radius: 50%;
  color: #ffca25;
  background: rgba(255, 202, 37, 0.08);
}

.redemption-mark.fulfilled {
  border-color: rgba(85, 221, 180, 0.38);
  color: #55ddb4;
  background: rgba(85, 221, 180, 0.09);
}

.redemption-mark svg { width: 16px; height: 16px; }
.redemption-copy { display: grid; min-width: 0; flex: 1; gap: 4px; }
.redemption-balance {
  display: flex;
  align-items: center;
  gap: 5px;
  color: #899bbb;
  font-size: 9px;
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}
.redemption-balance svg { width: 10px; height: 10px; color: #5c7198; }
.redemption-balance b { color: #c9d7ed; font-family: Consolas, "SFMono-Regular", monospace; font-weight: 800; }
.redemption-balance span:last-child b { color: #55ddff; }
.redemption-copy strong { overflow: hidden; color: #edf3ff; font-size: 12px; text-overflow: ellipsis; white-space: nowrap; }
.redemption-copy time { color: #7689ad; font-family: Consolas, "SFMono-Regular", monospace; font-size: 9px; font-variant-numeric: tabular-nums; }
.redemption-meta { display: grid; flex: 0 0 auto; gap: 5px; text-align: right; }
.redemption-meta > strong { color: #b7c7e4; font-family: Consolas, "SFMono-Regular", monospace; font-size: 10px; font-variant-numeric: tabular-nums; }
.redemption-meta > span { color: #ffca25; font-size: 10px; font-weight: 800; }
.redemption-meta > span.fulfilled { color: #55ddb4; }

.redemption-empty {
  min-height: 70px;
  justify-content: center;
  gap: 8px;
  border: 1px dashed #354b6e;
  border-radius: 7px;
  color: #7082a7;
  background: #111c33;
  font-size: 11px;
}

.redemption-empty svg { width: 18px; height: 18px; }

.score-popover {
  position: fixed;
  z-index: 30;
  box-sizing: border-box;
  width: min(360px, calc(100vw - 20px));
  max-height: calc(100vh - 20px);
  overflow-y: auto;
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

.score-opportunities {
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px dashed #385070;
}

.score-opportunities header,
.score-opportunity-state,
.score-opportunity-list article {
  display: flex;
  align-items: flex-start;
}

.score-opportunities header {
  gap: 8px;
  margin-bottom: 9px;
}

.score-opportunities header > svg {
  flex: 0 0 auto;
  width: 17px;
  height: 17px;
  color: #39d9ff;
}

.score-opportunities h4,
.score-opportunities p {
  margin: 0;
}

.score-opportunities h4 { color: #f4f7ff; font-size: 12px; }
.score-opportunities header p { margin-top: 2px; color: #7f90b1; font-size: 9px; }
.score-opportunity-list { display: grid; gap: 7px; }
.score-opportunity-list article { gap: 8px; }
.score-opportunity-list article > svg { flex: 0 0 auto; width: 13px; height: 13px; color: #54e8b3; }
.score-opportunity-list article > div { min-width: 0; }
.score-opportunity-list strong { display: block; color: #dfe7fb; font-size: 10px; }
.score-opportunity-list p { margin-top: 2px; color: #9aa9c5; font-size: 9px; line-height: 1.45; overflow-wrap: anywhere; }

.opportunity-dot {
  flex: 0 0 auto;
  width: 7px;
  height: 7px;
  margin: 4px 3px 0;
  border-radius: 50%;
  background: #39d9ff;
  box-shadow: 0 0 7px rgba(57, 217, 255, 0.62);
}

.score-opportunity-state {
  min-height: 34px;
  align-items: center;
  justify-content: center;
  gap: 6px;
  color: #8191b2;
  font-size: 10px;
}

.score-opportunity-state svg { width: 13px; height: 13px; }
.score-opportunity-state.error { color: #ff8da2; }
.score-opportunities.complete { border-top-color: #2b715f; }

.password-modal-backdrop {
  position: fixed;
  z-index: 40;
  inset: 0;
  display: grid;
  place-items: center;
  padding: 20px;
  background: rgba(3, 8, 27, 0.72);
  backdrop-filter: blur(7px);
}

.password-modal {
  position: relative;
  display: grid;
  grid-template-columns: 58px minmax(0, 1fr);
  gap: 4px 16px;
  width: min(520px, 100%);
  padding: 28px;
  border: 1px solid rgba(86, 219, 255, 0.68);
  border-radius: 18px;
  background: linear-gradient(145deg, #172954, #0b1536);
  box-shadow: 0 24px 80px rgba(0, 0, 0, 0.48), inset 0 0 35px rgba(65, 143, 255, 0.14);
}

.password-modal-mark {
  display: grid;
  grid-row: span 2;
  width: 54px;
  height: 54px;
  place-items: center;
  border: 1px solid #55ddff;
  border-radius: 15px;
  color: #55ddff;
  background: #112b54;
  box-shadow: 0 0 22px rgba(67, 215, 255, 0.22);
}

.password-modal-mark svg { width: 26px; height: 26px; }
.password-modal-copy { min-width: 0; }
.password-modal-kicker { margin: 0 0 5px; color: #55ddff; font-size: 11px; font-weight: 800; letter-spacing: 0.08em; }
.password-modal-copy h2 { margin: 0; color: #f4f8ff; font-size: 22px; }
.password-modal-copy > p:last-child { margin: 7px 0 0; color: #a4b7d9; font-size: 12px; line-height: 1.55; }

.password-change-form {
  display: grid;
  grid-column: 1 / -1;
  gap: 12px;
  margin-top: 18px;
}

.password-change-form .login-field { grid-column: auto; }
.password-change-form .login-error { grid-column: auto; }
.password-modal-actions { display: flex; justify-content: flex-end; gap: 10px; margin-top: 3px; }
.password-modal-actions .login-button { grid-column: auto; min-width: 130px; }
.password-later-button { height: 48px; padding: 0 15px; border: 1px solid #405989; border-radius: 10px; color: #aab9d7; background: rgba(11, 24, 53, 0.72); font-size: 12px; font-weight: 800; }
.password-later-button:hover, .password-later-button:focus-visible { border-color: #6b8dc9; outline: 2px solid rgba(107, 141, 201, 0.2); outline-offset: 2px; color: #f0f5ff; }

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

  .student-login-panel { grid-template-columns: 1fr; min-height: 0; gap: 18px; margin: 32px auto 48px; padding: 34px 23px 30px; border-radius: 18px; }
  .student-login-panel::before { inset: 8px; }
  .login-mark { grid-column: 1; margin: 0 auto; }
  .login-copy { grid-column: 1; padding-right: 0; text-align: center; }
  .login-copy h1 { margin-inline: auto; font-size: 26px; }
  .login-copy > p:last-child { margin-inline: auto; font-size: 12px; }
  .login-field, .login-error, .login-button { grid-column: 1; }
  .login-field { width: 100%; }

  .password-modal { grid-template-columns: 48px minmax(0, 1fr); padding: 22px 18px; }
  .password-modal-mark { width: 44px; height: 44px; }
  .password-modal-copy h2 { font-size: 19px; }
  .password-modal-actions { flex-direction: column-reverse; }
  .password-modal-actions button { width: 100%; }

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

  .supply-panel {
    order: 0;
  }

  .reward-grid { grid-template-columns: 1fr; }
  .reward-card { min-height: 76px; }
  .reward-image { width: 58px; height: 58px; }

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
.reward-card { cursor: pointer; transition: border-color .18s ease, transform .18s ease, box-shadow .18s ease; }
.reward-card:hover, .reward-card:focus-visible { border-color: #55ddff; outline: none; transform: translateY(-2px); box-shadow: 0 8px 24px rgba(42, 189, 255, .16); }
.redeem-notice { margin: -2px 0 10px; padding: 8px 10px; border: 1px solid #2b715f; border-radius: 6px; color: #9ff5c8; background: rgba(31, 103, 78, .2); font-size: 11px; }
.reward-redeem-backdrop { position: fixed; z-index: 45; inset: 0; display: grid; place-items: center; padding: 20px; background: rgba(3, 8, 27, .74); backdrop-filter: blur(7px); }
.reward-redeem-modal { width: min(460px, 100%); padding: 22px; border: 1px solid rgba(86, 219, 255, .68); border-radius: 16px; color: #f4f8ff; background: linear-gradient(145deg, #172954, #0b1536); box-shadow: 0 24px 80px rgba(0, 0, 0, .48), inset 0 0 35px rgba(65, 143, 255, .14); }
.reward-redeem-modal > header, .reward-redeem-modal > header > div { display: flex; align-items: center; }
.reward-redeem-modal > header { justify-content: space-between; gap: 12px; }
.reward-redeem-modal > header > div { gap: 10px; }
.reward-redeem-modal h2 { margin: 0; font-size: 20px; }
.reward-redeem-mark { display: grid; width: 42px; height: 42px; place-items: center; border: 1px solid #55ddff; border-radius: 12px; color: #ffca25; background: #112b54; }
.reward-redeem-mark svg { width: 22px; height: 22px; }
.reward-redeem-body { display: grid; gap: 14px; margin-top: 20px; }
.reward-redeem-item { display: flex; align-items: center; gap: 12px; padding: 10px; border: 1px solid #354c70; border-radius: 8px; background: #16233b; }
.reward-redeem-item .reward-image { width: 60px; height: 60px; }
.reward-redeem-item strong, .reward-redeem-item small { display: block; }
.reward-redeem-item strong { color: #f5f7ff; font-size: 14px; }
.reward-redeem-item small { margin-top: 5px; color: #55ddff; font-size: 11px; }
.reward-redeem-body > p { margin: 0; color: #a4b7d9; font-size: 12px; line-height: 1.6; }
.reward-redeem-body b { color: #55ddff; }
.redeem-error { color: #ff8da2 !important; }
.reward-redeem-modal > footer { display: flex; justify-content: flex-end; gap: 10px; margin-top: 20px; }
.reward-redeem-modal > footer .login-button { min-width: 130px; }
@media (max-width: 560px) {
  .reward-redeem-backdrop { padding: 12px; }
  .reward-redeem-modal { padding: 18px; }
  .reward-redeem-modal > footer { flex-direction: column-reverse; }
  .reward-redeem-modal > footer button { width: 100%; }
}

</style>