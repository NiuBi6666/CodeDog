<script setup>
import { reactive, ref } from "vue";
import { useRouter } from "vue-router";
import AdminLayout from "../components/AdminLayout.vue";
import { api, jsonBody, notify } from "../api";

const form = reactive({ currentPassword: "", newPassword: "", confirmation: "" }); const error = ref(""); const busy = ref(false); const router = useRouter();
async function submit() { error.value = ""; busy.value = true; try { await api("/auth/password", { method: "POST", body: jsonBody(form) }); notify("密码已更新"); await router.push("/index"); } catch (failure) { error.value = failure.message; } finally { busy.value = false; } }
</script>

<template><AdminLayout page-title="修改密码" active-page="account"><div class="settings-shell"><section class="auth-panel" aria-labelledby="password-title"><h1 id="password-title">修改密码</h1><div v-if="error" class="notice notice-error">{{ error }}</div><form class="form-stack" @submit.prevent="submit"><label><span>当前密码</span><input v-model="form.currentPassword" type="password" autocomplete="current-password" required autofocus></label><label><span>新密码</span><input v-model="form.newPassword" type="password" autocomplete="new-password" minlength="12" required></label><label><span>确认新密码</span><input v-model="form.confirmation" type="password" autocomplete="new-password" minlength="12" required></label><button class="button button-primary button-full" type="submit" :disabled="busy">{{ busy ? "更新中..." : "更新密码" }}</button></form></section></div></AdminLayout></template>
