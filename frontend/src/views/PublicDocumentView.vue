<script setup>
import { nextTick, onMounted, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { api, notify, writeClipboard } from "../api";
import { formatDateTime } from "../utils";

const props = defineProps({ notFound: Boolean });
const route = useRoute(); const router = useRouter(); const document = ref(null); const state = ref("loading");
async function enhanceCodeBlocks() {
  await nextTick();
  document.value && window.document.querySelectorAll(".document-content pre").forEach((pre) => {
    if (pre.parentElement?.classList.contains("code-block")) return;
    const wrapper = window.document.createElement("div"); wrapper.className = "code-block"; pre.parentNode.insertBefore(wrapper, pre); wrapper.appendChild(pre);
    const button = window.document.createElement("button"); button.className = "code-copy-button"; button.type = "button"; button.textContent = "复制"; button.setAttribute("aria-label", "复制代码");
    button.addEventListener("click", async () => { try { await writeClipboard(pre.innerText); button.textContent = "已复制"; button.dataset.copied = "true"; notify("代码已复制"); window.setTimeout(() => { button.textContent = "复制"; delete button.dataset.copied; }, 1600); } catch { notify("复制失败，请手动选择代码"); } });
    wrapper.appendChild(button);
  });
}
async function load() {
  if (props.notFound) { state.value = "not-found"; return; }
  state.value = "loading";
  const id = route.params.id;
  try {
    const loaded = await api(id ? `/public/documents/${id}` : "/public/documents/latest");
    if (id && loaded?.id && id !== loaded.id) { await router.replace(`/doc/show/${loaded.id}`); return; }
    document.value = loaded;
    state.value = document.value ? "ready" : "empty"; if (document.value) { window.document.title = document.value.title; await enhanceCodeBlocks(); }
  } catch (failure) {
    if (failure.status === 410) {
      if (id && failure.payload?.id && id !== failure.payload.id) { await router.replace(`/doc/show/${failure.payload.id}`); return; }
      document.value = failure.payload; state.value = "offline";
    }
    else if (failure.status === 404) state.value = "not-found"; else state.value = "error";
  }
}
watch(() => route.fullPath, load); onMounted(load);
</script>

<template>
  <header class="topbar"><a class="brand" href="/">CodeDog</a></header>
  <main v-if="state === 'ready'" class="reader-shell"><article class="document" data-public-document><header class="document-header"><h1>{{ document.title }}</h1><p class="document-meta">创建于 {{ formatDateTime(document.createdAt) }} · 更新于 {{ formatDateTime(document.updatedAt) }}</p></header><div class="document-content" v-html="document.content"></div></article></main>
  <main v-else class="state-shell"><div class="state-content"><template v-if="state === 'offline'"><p class="state-code">410</p><h1>文档已下线</h1></template><template v-else-if="state === 'not-found'"><p class="state-code">404</p><h1>文档不存在</h1></template><h1 v-else-if="state === 'empty'">暂无已发布文档</h1><h1 v-else-if="state === 'error'">文档暂时无法访问</h1><h1 v-else>正在加载...</h1></div></main>
</template>
