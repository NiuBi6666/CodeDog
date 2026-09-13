<script setup>
import { ref, watch, onBeforeUnmount } from "vue";
import { useRoute } from "vue-router";
import { api, jsonBody } from "../api";
import { displayExamScore } from "../examImport";
const route = useRoute();
const exam = ref(null);
const loading = ref(true);
const error = ref("");
const name = ref("");
const scores = ref(null);
const busy = ref(false);
const message = ref("");
let revision = 0;
let controller;
function clearQuery() { revision++; controller?.abort(); scores.value = null; message.value = ""; busy.value = false; }
async function load() {
  clearQuery(); name.value = ""; exam.value = null; error.value = ""; loading.value = true;
  const current = revision;
  try { const data = await api("/public/exams/" + encodeURIComponent(route.params.token)); if (current === revision) exam.value = data; }
  catch (failure) { if (current === revision) error.value = failure.message; }
  finally { if (current === revision) loading.value = false; }
}
async function query() {
  clearQuery();
  if (!name.value.trim()) { message.value = "请输入完整姓名。"; return; }
  const current = revision;
  const activeController = new AbortController();
  controller = activeController;
  const timer = window.setTimeout(() => activeController.abort(), 12000);
  busy.value = true;
  try {
    const data = await api("/public/exams/" + encodeURIComponent(route.params.token) + "/query", {
      method: "POST", body: jsonBody({ name: name.value.trim() }), signal: activeController.signal, cache: "no-store"
    });
    if (current === revision) scores.value = data.scores;
  } catch (failure) {
    if (current === revision) message.value = failure.status ? failure.message : "连接未成功，请检查网络后重试。";
  } finally { window.clearTimeout(timer); if (current === revision) busy.value = false; }
}
watch(() => route.params.token, load, { immediate: true });
onBeforeUnmount(() => { revision++; controller?.abort(); });
</script>
<template>
  <main class="exam-public">
    <section class="exam-query-sheet">
      <p v-if="loading" role="status">正在加载考试信息…</p>
      <template v-else-if="exam">
        <h1>{{ exam.title }}</h1>
        <p class="exam-query-intro">输入学员姓名，查询本次考试成绩。</p>
        <form @submit.prevent="query" autocomplete="off">
          <label for="exam-name">学员姓名</label>
          <div class="exam-query-row"><input id="exam-name" v-model="name" maxlength="100" required placeholder="请输入完整姓名" autocomplete="off" spellcheck="false" aria-describedby="exam-query-message" @input="clearQuery"><button type="submit" :disabled="busy">{{ busy ? '查询中…' : '查询成绩 →' }}</button></div>
        </form>
        <p id="exam-query-message" class="exam-query-message" role="status" aria-live="polite">{{ message }}</p>
        <dl v-if="scores" class="exam-result" aria-label="考试成绩" aria-live="polite">
          <div v-for="(score,i) in scores" :key="i"><dt>{{ exam.scoreLabels[i] }}</dt><dd :class="{ 'exam-text-score': !/^\d+(\.\d+)?$/.test(displayExamScore(score)) }">{{ displayExamScore(score) }}</dd></div>
        </dl>
      </template>
      <p v-else class="exam-unavailable" role="alert">{{ error || '查询链接不存在。' }}</p>
    </section>
  </main>
</template>
<style scoped>
.exam-public{min-height:100vh;min-height:100svh;display:grid;place-items:center;padding:28px 18px;background:#eef4fa;color:#16324f;font-family:"PingFang SC","Microsoft YaHei",sans-serif}
.exam-query-sheet{width:100%;max-width:720px;background:#fff;border-top:5px solid #245cc5;border-radius:18px;padding:42px;box-shadow:0 16px 64px #16324f0b}
h1{font-family:"Songti SC","STSong","SimSun",serif;font-size:30px;line-height:1.5;letter-spacing:.02em;margin:0 0 10px;overflow-wrap:anywhere}
.exam-query-intro{color:#63758a;font-size:14px;margin:0;line-height:1.8}form{margin-top:30px}label{display:block;font-size:14px;font-weight:600;margin-bottom:10px}
.exam-query-row{display:flex;gap:12px}input,button{font:inherit;border-radius:9px;min-height:52px}input{width:100%;min-width:0;border:1px solid #dbe5ef;padding:12px 16px;background:#fff;color:#16324f;font-size:16px}
button{border:0;padding:12px 22px;background:#245cc5;color:#fff;white-space:nowrap;font-size:15px;font-weight:600;cursor:pointer}button:disabled{opacity:.65;cursor:wait}input:focus-visible,button:focus-visible{outline:3px solid #245cc540;outline-offset:3px}
.exam-query-message{font-size:14px;color:#b33242;margin:12px 0 0;line-height:1.6}.exam-query-message:empty{margin:0}
.exam-result{margin:30px 0 0;border-top:1px solid #dbe5ef;display:grid;grid-template-columns:repeat(auto-fit,minmax(155px,1fr))}
.exam-result>div{padding:22px 16px 8px 0}dt{color:#63758a;font-size:14px;margin-bottom:12px;overflow-wrap:anywhere}
dd{font-family:"SFMono-Regular",Consolas,monospace;font-size:34px;line-height:1.3;margin:0;font-weight:600;font-variant-numeric:tabular-nums;overflow-wrap:anywhere}
dd.exam-text-score{font-family:inherit;font-size:21px;line-height:1.6}.exam-unavailable{margin:0;line-height:1.8}
@media(max-width:540px){.exam-query-sheet{padding:30px 24px}h1{font-size:26px}.exam-query-row{flex-direction:column}.exam-result{display:block}.exam-result>div{display:flex;justify-content:space-between;align-items:center;gap:18px;padding:20px 0;border-bottom:1px solid #dbe5ef}.exam-result>div:last-child{border:0;padding-bottom:0}dt{margin:0}dd{font-size:32px;text-align:right}}
</style>
