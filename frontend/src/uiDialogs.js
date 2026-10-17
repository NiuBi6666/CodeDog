import { shallowRef } from "vue";

// One queued host for the entire application, including retained routes.
export const activeDialog = shallowRef(null);
const pending = [];
let sequence = 0;
function next() {
  if (!activeDialog.value && pending.length) activeDialog.value = pending.shift();
}
function open(message, options, confirm) {
  return new Promise((resolve) => {
    pending.push({ id: ++sequence, message: String(message), title: options.title || (confirm ? "操作确认" : "温馨提示"),
      confirm, danger: Boolean(options.danger), confirmText: options.confirmText || (confirm ? "确认" : "知道了"), resolve });
    next();
  });
}
export function showAlert(message, options = {}) { return open(message, options, false); }
export function showConfirm(message, options = {}) { return open(message, options, true); }
export function settleDialog(accepted) {
  const dialog = activeDialog.value;
  if (!dialog) return;
  activeDialog.value = null;
  dialog.resolve(Boolean(accepted));
  next();
}
