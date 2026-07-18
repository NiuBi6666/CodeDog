<script setup>
import { onBeforeUnmount, onMounted, ref, watchEffect } from "vue";
import { RouterView, useRoute } from "vue-router";

const route = useRoute();
const toast = ref("");
let timer;
function showToast(event) {
  toast.value = event.detail;
  window.clearTimeout(timer);
  timer = window.setTimeout(() => (toast.value = ""), 1800);
}
onMounted(() => window.addEventListener("codedog-toast", showToast));
onBeforeUnmount(() => window.removeEventListener("codedog-toast", showToast));
watchEffect(() => {
  document.body.className = route.meta.bodyClass || "";
  document.title = route.meta.title || "CodeDog";
});
</script>

<template>
  <RouterView />
  <div id="toast" class="toast" :class="{ 'is-visible': toast }" role="status" aria-live="polite">{{ toast }}</div>
</template>
