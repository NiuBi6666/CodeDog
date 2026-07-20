<script setup>
import { ref } from "vue";
import { RefreshCw } from "@lucide/vue";
import AdminLayout from "../components/AdminLayout.vue";

const loading = ref(true);
const formUrl = "/api/questionnaire/sso";
function handleFrameLoad() { loading.value = false; }
</script>

<template>
  <AdminLayout page-title="问卷与作业" active-page="questionnaire" content-class="admin-main--questionnaire">
    <section class="questionnaire-page">
      <div class="questionnaire-frame-shell">
        <div v-if="loading" class="questionnaire-loading" role="status">
          <RefreshCw :size="22" class="questionnaire-loading-icon"/>
          <span>正在进入问卷与作业...</span>
        </div>
        <iframe class="questionnaire-frame" :src="formUrl" title="问卷与作业管理" @load="handleFrameLoad"></iframe>
      </div>
    </section>
  </AdminLayout>
</template>
