<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import {
  AlignCenter, AlignLeft, AlignRight, Bold, Code2, ImagePlus, Italic, Link2, List,
  ListOrdered, Minus, Quote, Redo2, RemoveFormatting, Save, Strikethrough,
  Sigma, Table2, Underline, Undo2, Unlink,
} from "@lucide/vue";
import AdminLayout from "../components/AdminLayout.vue";
import { api, jsonBody, notify } from "../api";
import { createMathElement, enhanceDocumentMath, renderMathElement, serializeDocumentContent } from "../documentMath";
import { validateDocumentImage } from "../utils";

const route = useRoute(); const router = useRouter(); const editor = ref(null); const imageInput = ref(null);
const title = ref(""); const version = ref(0); const loading = ref(true);
const saveMessage = ref("未创建"); const saveState = ref(""); const conflict = ref(false); const dirty = ref(false); const saving = ref(false);
const isNew = computed(() => route.path === "/doc/create"); let timer; let savedRange = null;
function setStatus(message, state = "") { saveMessage.value = message; saveState.value = state; }
async function load() {
  loading.value = true; conflict.value = false; dirty.value = false; savedRange = null;
  if (isNew.value) { title.value = ""; version.value = 0; await nextTick(); editor.value.innerHTML = "<p><br></p>"; setStatus("未创建"); }
  else {
    try { const document = await api(`/documents/${route.params.id}`); if (route.params.id !== document.id) { await router.replace(`/doc/edit/${document.id}`); return; } title.value = document.title; version.value = document.version; await nextTick(); editor.value.innerHTML = document.content; enhanceDocumentMath(editor.value); setStatus("已保存", "saved"); }
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
    const payload = { title: title.value.trim(), content: serializeDocumentContent(editor.value), version: version.value };
    const document = await api(isNew.value ? "/documents" : `/documents/${route.params.id}`, { method: isNew.value ? "POST" : "PUT", body: jsonBody(payload) });
    if (isNew.value) { setStatus("已创建", "saved"); window.location.replace(`/doc/edit/${document.id}`); return; }
    version.value = document.version; setStatus("已保存", "saved");
  } catch (failure) {
    dirty.value = true; setStatus(failure.message, "error"); if (failure.status === 409) conflict.value = true;
  } finally { saving.value = false; }
}
function captureSelection() {
  const selection = window.getSelection();
  if (!selection?.rangeCount || !editor.value) return;
  const range = selection.getRangeAt(0); const node = range.commonAncestorContainer;
  const element = node.nodeType === Node.ELEMENT_NODE ? node : node.parentElement;
  if (element && editor.value.contains(element)) savedRange = range.cloneRange();
}
function restoreSelection() {
  if (!editor.value) return null;
  editor.value.focus(); const selection = window.getSelection();
  if (!selection) return null;
  selection.removeAllRanges();
  try {
    if (savedRange && savedRange.commonAncestorContainer.isConnected) selection.addRange(savedRange);
    else {
      const range = document.createRange(); range.selectNodeContents(editor.value); range.collapse(false); selection.addRange(range);
    }
  } catch (_error) {
    const range = document.createRange(); range.selectNodeContents(editor.value); range.collapse(false); selection.addRange(range);
  }
  return selection;
}
function command(name, value = null) {
  restoreSelection(); document.execCommand(name, false, value); captureSelection(); markDirty();
}
function format(event) { command("formatBlock", event.target.value); }
function insertNodes(nodes) {
  const selection = restoreSelection(); if (!selection?.rangeCount) return;
  const range = selection.getRangeAt(0); range.deleteContents();
  for (const node of nodes) { range.insertNode(node); range.setStartAfter(node); range.collapse(true); }
  selection.removeAllRanges(); selection.addRange(range); savedRange = range.cloneRange();
}
function insertLink() {
  captureSelection(); const href = window.prompt("输入链接地址", "https://"); if (!href) return;
  const selection = restoreSelection();
  if (selection?.isCollapsed) {
    const link = document.createElement("a"); link.href = href; link.textContent = href; insertNodes([link]); markDirty();
  } else command("createLink", href);
}
function chooseImage() { captureSelection(); imageInput.value?.click(); }
function readImage(file) {
  return new Promise((resolve, reject) => {
    const reader = new FileReader(); reader.onload = () => resolve(reader.result); reader.onerror = reject; reader.readAsDataURL(file);
  });
}
async function insertImageFile(file) {
  const validation = validateDocumentImage(file); if (validation) { notify(validation); return; }
  try {
    const source = await readImage(file); const image = document.createElement("img");
    image.src = source; image.alt = file.name.replace(/\.[^.]+$/, "") || "文档图片"; image.title = image.alt;
    const paragraph = document.createElement("p"); paragraph.innerHTML = "<br>";
    insertNodes([image, paragraph]); markDirty(); notify("图片已插入");
  } catch (_error) { notify("图片读取失败"); }
}
async function handleImageInput(event) {
  const file = event.target.files?.[0]; event.target.value = ""; if (file) await insertImageFile(file);
}
async function handlePaste(event) {
  const file = [...(event.clipboardData?.files || [])].find((item) => item.type.startsWith("image/"));
  if (!file) {
    window.setTimeout(() => { enhanceDocumentMath(editor.value); captureSelection(); markDirty(); }, 0);
    return;
  }
  event.preventDefault(); captureSelection(); await insertImageFile(file);
}
function insertFormula() {
  captureSelection();
  const source = window.prompt("输入 LaTeX 公式", "\\frac{a}{b}");
  if (!source?.trim()) return;
  const formula = createMathElement(document, source);
  insertNodes([formula, document.createTextNode("\u00a0")]); renderMathElement(formula); markDirty();
}
function editFormula(event) {
  const target = event.target instanceof Element ? event.target.closest(".math-formula") : null;
  if (!target || !editor.value.contains(target)) return;
  const source = window.prompt("修改 LaTeX 公式", target.dataset.latex || "");
  if (!source?.trim()) return;
  target.dataset.latex = source.trim(); renderMathElement(target); markDirty();
}
function insertTable() {
  captureSelection();
  const rows = Number.parseInt(window.prompt("表格行数（1-10）", "3") || "", 10);
  const columns = Number.parseInt(window.prompt("表格列数（1-10）", "3") || "", 10);
  if (!Number.isInteger(rows) || !Number.isInteger(columns) || rows < 1 || rows > 10 || columns < 1 || columns > 10) { notify("表格行列数必须在 1 到 10 之间"); return; }
  const table = document.createElement("table"); const body = document.createElement("tbody"); table.appendChild(body);
  for (let rowIndex = 0; rowIndex < rows; rowIndex += 1) {
    const row = document.createElement("tr");
    for (let columnIndex = 0; columnIndex < columns; columnIndex += 1) { const cell = document.createElement("td"); cell.innerHTML = "<br>"; row.appendChild(cell); }
    body.appendChild(row);
  }
  const paragraph = document.createElement("p"); paragraph.innerHTML = "<br>"; insertNodes([table, paragraph]); markDirty();
}
function align(value) {
  const selection = restoreSelection(); const anchor = selection?.anchorNode;
  const element = anchor?.nodeType === Node.ELEMENT_NODE ? anchor : anchor?.parentElement;
  const block = element?.closest("p,div,h1,h2,h3,h4,blockquote,pre,td,th");
  if (!block || !editor.value.contains(block)) return;
  block.setAttribute("align", value); captureSelection(); markDirty();
}
function editorInput() { captureSelection(); markDirty(); }
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
    <div class="editor-toolbar" role="toolbar" aria-label="文档编辑工具">
      <div class="toolbar-tools">
        <select aria-label="段落格式" title="段落格式" @mousedown="captureSelection" @change="format"><option value="p">正文</option><option value="h1">一级标题</option><option value="h2">二级标题</option><option value="h3">三级标题</option></select>
        <span class="toolbar-divider"></span>
        <button type="button" title="加粗" aria-label="加粗" @mousedown.prevent @click="command('bold')"><Bold :size="17"/></button>
        <button type="button" title="斜体" aria-label="斜体" @mousedown.prevent @click="command('italic')"><Italic :size="17"/></button>
        <button type="button" title="下划线" aria-label="下划线" @mousedown.prevent @click="command('underline')"><Underline :size="17"/></button>
        <button type="button" title="删除线" aria-label="删除线" @mousedown.prevent @click="command('strikeThrough')"><Strikethrough :size="17"/></button>
        <span class="toolbar-divider"></span>
        <button type="button" title="项目符号列表" aria-label="项目符号列表" @mousedown.prevent @click="command('insertUnorderedList')"><List :size="17"/></button>
        <button type="button" title="编号列表" aria-label="编号列表" @mousedown.prevent @click="command('insertOrderedList')"><ListOrdered :size="17"/></button>
        <button type="button" title="引用" aria-label="引用" @mousedown.prevent @click="command('formatBlock', 'blockquote')"><Quote :size="17"/></button>
        <button type="button" title="代码块" aria-label="代码块" @mousedown.prevent @click="command('formatBlock', 'pre')"><Code2 :size="17"/></button>
        <span class="toolbar-divider"></span>
        <button type="button" title="插入链接" aria-label="插入链接" @mousedown.prevent @click="insertLink"><Link2 :size="17"/></button>
        <button type="button" title="取消链接" aria-label="取消链接" @mousedown.prevent @click="command('unlink')"><Unlink :size="17"/></button>
        <button type="button" title="插入图片" aria-label="插入图片" @mousedown.prevent @click="chooseImage"><ImagePlus :size="17"/></button>
        <button type="button" title="插入数学公式" aria-label="插入数学公式" @mousedown.prevent @click="insertFormula"><Sigma :size="17"/></button>
        <button type="button" title="插入表格" aria-label="插入表格" @mousedown.prevent @click="insertTable"><Table2 :size="17"/></button>
        <button type="button" title="插入分隔线" aria-label="插入分隔线" @mousedown.prevent @click="command('insertHorizontalRule')"><Minus :size="17"/></button>
        <span class="toolbar-divider"></span>
        <button type="button" title="左对齐" aria-label="左对齐" @mousedown.prevent @click="align('left')"><AlignLeft :size="17"/></button>
        <button type="button" title="居中" aria-label="居中" @mousedown.prevent @click="align('center')"><AlignCenter :size="17"/></button>
        <button type="button" title="右对齐" aria-label="右对齐" @mousedown.prevent @click="align('right')"><AlignRight :size="17"/></button>
        <span class="toolbar-divider"></span>
        <button type="button" title="撤销" aria-label="撤销" @mousedown.prevent @click="command('undo')"><Undo2 :size="17"/></button>
        <button type="button" title="重做" aria-label="重做" @mousedown.prevent @click="command('redo')"><Redo2 :size="17"/></button>
        <button type="button" title="清除格式" aria-label="清除格式" @mousedown.prevent @click="command('removeFormat')"><RemoveFormatting :size="17"/></button>
      </div>
      <button class="toolbar-save" type="button" @mousedown.prevent @click="saveNow"><Save :size="16"/>{{ isNew ? "创建" : "保存" }}</button>
      <input ref="imageInput" class="editor-image-input" type="file" accept="image/png,image/jpeg,image/gif,image/webp" @change="handleImageInput">
    </div>
    <div class="editor-shell admin-editor-shell"><div ref="editor" class="document editor-document" contenteditable="true" role="textbox" aria-multiline="true" spellcheck="true" @input="editorInput" @mouseup="captureSelection" @keyup="captureSelection" @focus="captureSelection" @paste="handlePaste" @dblclick="editFormula"></div></div>
    <div v-if="conflict" class="conflict-banner"><span>文档已在其他页面更新</span><button class="button button-primary" type="button" @click="reloadPage">载入最新版本</button></div>
  </AdminLayout>
</template>
