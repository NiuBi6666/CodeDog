<script setup>
import { nextTick, onBeforeUnmount, ref, watch } from "vue";
import { Info, ShieldAlert, X } from "@lucide/vue";
import { activeDialog, settleDialog } from "../uiDialogs.js";
const panel = ref(null);
let previousFocus;
let previousOverflow;
function keydown(event) {
  if (!activeDialog.value) return;
  if (event.key === "Escape") { event.preventDefault(); event.stopImmediatePropagation(); settleDialog(false); }
  if (event.key !== "Tab") return;
  const targets = [...(panel.value?.querySelectorAll("button:not(:disabled)") || [])];
  if (!targets.length) return;
  const first = targets[0], last = targets[targets.length-1];
  if (event.shiftKey && (document.activeElement === first || !panel.value.contains(document.activeElement))) { event.preventDefault(); last.focus(); }
  else if (!event.shiftKey && (document.activeElement === last || !panel.value.contains(document.activeElement))) { event.preventDefault(); first.focus(); }
}
watch(activeDialog, async (value, before) => {
  if (value && !before) {
    previousFocus = document.activeElement;
    previousOverflow = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    document.addEventListener("keydown", keydown, true);
  }
  if (value) {
    await nextTick();
    // Focus cancel first for destructive confirmations.
    panel.value?.querySelector(value.confirm ? "[data-cancel]" : "[data-accept]")?.focus();
  } else if (before) {
    document.body.style.overflow = previousOverflow || "";
    document.removeEventListener("keydown", keydown, true);
    if (previousFocus?.isConnected) previousFocus.focus();
  }
}, { flush: "post" });
onBeforeUnmount(() => {
  document.removeEventListener("keydown", keydown, true);
  if (activeDialog.value) document.body.style.overflow = previousOverflow || "";
});
</script>

<template>
  <Teleport to="body">
    <div v-if="activeDialog" class="ui-dialog-backdrop ui-feedback-backdrop" @click.self="settleDialog(false)">
      <section ref="panel" class="ui-modal ui-feedback-dialog" role="alertdialog" aria-modal="true" aria-labelledby="ui-feedback-title" aria-describedby="ui-feedback-message">
        <header><div class="ui-feedback-heading"><span class="ui-dialog-symbol" :class="{ 'is-danger': activeDialog.danger }"><ShieldAlert v-if="activeDialog.danger" :size="20"/><Info v-else :size="20"/></span><h2 id="ui-feedback-title">{{ activeDialog.title }}</h2></div><button class="icon-button" type="button" aria-label="关闭提示" @click="settleDialog(false)"><X :size="18"/></button></header>
        <div class="ui-modal-body"><p id="ui-feedback-message">{{ activeDialog.message }}</p></div>
        <footer><button v-if="activeDialog.confirm" data-cancel class="button button-quiet" type="button" @click="settleDialog(false)">取消</button><button data-accept class="button" :class="activeDialog.danger ? 'button-danger' : 'button-primary'" type="button" @click="settleDialog(true)">{{ activeDialog.confirmText }}</button></footer>
      </section>
    </div>
  </Teleport>
</template>
