<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import AdminLayout from "../components/AdminLayout.vue";
import { api, jsonBody } from "../api";

const route = useRoute(); const router = useRouter(); const editor = ref(null); const title = ref(""); const version = ref(0); const loading = ref(true);
const saveMessage = ref("未创建"); const saveState = ref(""); const conflict = ref(false); const dirty = ref(false); const saving = ref(false);
const isNew = computed(() => route.path === "/doc/create"); let timer;
function setStatus(message, state = "") { saveMessage.value = message; saveState.value = state; }
async function load() {
  loading.value = true; conflict.value = false; dirty.value = false;
  if (isNew.value) { title.value = ""; version.value = 0; await nextTick(); editor.value.innerHTML = "<p><br></p>"; setStatus("未创建"); }
  else {
    try { const document = await api(`/documents/${route.params.id}`); if (route.params.id !== document.id) { await router.replace(`/doc/edit/${document.id}`); return; } title.value = document.title; version.value = document.version; await nextTick(); editor.value.innerHTML = document.content; setStatus("已保存", "saved"); }
    catch (failure) { setStatus(failure.message, "error"); }
  }
  loading.value = false;
}
function markDirty() { if (loading.value) return; dirty.value = true; setStatus(isNew.value ? "未创建" : "未保存", "dirty"); window.clearTimeout(timer); if (!isNew.value) timer = window.setTimeout(save, 900); }
async function save() {
  window.clearTimeout(timer); if (saving.value || !dirty.value) return;
  if (!title.value.trim()) { setStatus("标题不能为空", "error"); return; }
  saving.value = true; dirty.value = false; setStatus("正在保存...", "saving");
  try {
    const payload = { title: title.value.trim(), content: editor.value.innerHTML, version: version.value };
    const document = await api(isNew.value ? "/documents" : `/documents/${route.params.id}`, { method: isNew.value ? "POST" : "PUT", body: jsonBody(payload) });
    if (isNew.value) { setStatus("已创建", "saved"); window.location.replace(`/doc/edit/${document.id}`); return; }
    version.value = document.version; setStatus("已保存", "saved");
  } catch (failure) {
    dirty.value = true; setStatus(failure.message, "error"); if (failure.status === 409) conflict.value = true;
  } finally { saving.value = false; }
}
function command(name) { editor.value.focus(); document.execCommand(name, false, null); markDirty(); }
function format(event) { editor.value.focus(); document.execCommand("formatBlock", false, event.target.value); markDirty(); }
function insertLink() { const href = window.prompt("输入链接地址", "https://"); if (!href) return; editor.value.focus(); document.execCommand("createLink", false, href); markDirty(); }
function saveNow() { dirty.value = true; save(); }
function keyboard(event) { if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === "s") { event.preventDefault(); saveNow(); } }
function beforeUnload(event) { if (!dirty.value) return; event.preventDefault(); event.returnValue = ""; }
function reloadPage() { window.location.reload(); }
watch(() => route.fullPath, load);
onMounted(() => { load(); document.addEventListener("keydown", keyboard); window.addEventListener("beforeunload", beforeUnload); });
onBeforeUnmount(() => { window.clearTimeout(timer); document.removeEventListener("keydown", keyboard); window.removeEventListener("beforeunload", beforeUnload); });
</script>

<template>
  <AdminLayout :page-title="isNew ? '新建文档' : '编辑文档'" active-page="documents">
    <div class="editor-titlebar"><input v-model="title" class="title-input" maxlength="200" aria-label="文档标题" placeholder="输入文档标题" @input="markDirty"><div class="save-status" :data-state="saveState" role="status">{{ saveMessage }}</div><nav class="editor-actions" aria-label="文档操作"><RouterLink class="button button-quiet" to="/doc/list">文档列表</RouterLink><a v-if="!isNew" class="button button-quiet" :href="`/doc/show/${route.params.id}`">查看</a></nav></div>
    <div class="editor-toolbar" role="toolbar" aria-label="文本格式"><select aria-label="段落格式" @change="format"><option value="p">正文</option><option value="h1">一级标题</option><option value="h2">二级标题</option><option value="h3">三级标题</option><option value="blockquote">引用</option></select><span class="toolbar-divider"></span><button type="button" title="加粗" aria-label="加粗" @click="command('bold')"><strong>B</strong></button><button type="button" title="斜体" aria-label="斜体" @click="command('italic')"><em>I</em></button><button type="button" title="下划线" aria-label="下划线" @click="command('underline')"><u>U</u></button><span class="toolbar-divider"></span><button type="button" title="项目符号列表" aria-label="项目符号列表" @click="command('insertUnorderedList')">•</button><button type="button" title="编号列表" aria-label="编号列表" @click="command('insertOrderedList')">1.</button><button type="button" title="插入链接" aria-label="插入链接" @click="insertLink">链接</button><span class="toolbar-divider"></span><button type="button" title="撤销" aria-label="撤销" @click="command('undo')">↶</button><button type="button" title="重做" aria-label="重做" @click="command('redo')">↷</button><button type="button" title="清除格式" aria-label="清除格式" @click="command('removeFormat')">清除</button><button class="toolbar-save" type="button" @click="saveNow">{{ isNew ? "创建" : "保存" }}</button></div>
    <div class="editor-shell admin-editor-shell"><div ref="editor" class="document editor-document" contenteditable="true" role="textbox" aria-multiline="true" @input="markDirty"></div></div>
    <div v-if="conflict" class="conflict-banner"><span>文档已在其他页面更新</span><button class="button button-primary" type="button" @click="reloadPage">载入最新版本</button></div>
  </AdminLayout>
</template>
