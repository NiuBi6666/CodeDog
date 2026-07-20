<script setup>
import { ref } from "vue";
import { ExternalLink, RefreshCw } from "@lucide/vue";
import AdminLayout from "../components/AdminLayout.vue";

const frameKey = ref(0);
const loading = ref(true);
const formUrl = "/api/questionnaire/sso";
function reloadFrame() { loading.value = true; frameKey.value += 1; }
function handleFrameLoad() { loading.value = false; }
</script>

<template>
  <AdminLayout page-title="问卷与作业" active-page="questionnaire">
    <section class="questionnaire-page">
      <header class="admin-page-heading questionnaire-heading">
        <h1>问卷与作业</h1>
        <div class="questionnaire-toolbar">
          <button class="icon-button" type="button" title="刷新问卷页面" aria-label="刷新问卷页面" @click="reloadFrame"><RefreshCw :size="17"/></button>
          <a class="icon-button" :href="formUrl" target="_blank" rel="noopener noreferrer" title="在新窗口打开" aria-label="在新窗口打开"><ExternalLink :size="17"/></a>
        </div>
      </header>
      <div class="questionnaire-frame-shell">
        <div v-if="loading" class="questionnaire-loading" role="status">
          <RefreshCw :size="22" class="questionnaire-loading-icon"/>
          <span>正在进入问卷与作业...</span>
        </div>
        <iframe :key="frameKey" class="questionnaire-frame" :src="formUrl" title="问卷与作业管理" @load="handleFrameLoad"></iframe>
      </div>
    </section>
  </AdminLayout>
</template>
