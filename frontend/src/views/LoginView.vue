<script setup>
import { ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { login } from "../auth";

const username = ref("");
const password = ref("");
const error = ref("");
const busy = ref(false);
const route = useRoute();
const router = useRouter();
async function submit() {
  error.value = ""; busy.value = true;
  try {
    await login(username.value, password.value);
    await router.replace(typeof route.query.redirect === "string" ? route.query.redirect : "/index");
  } catch (failure) { error.value = failure.message; }
  finally { busy.value = false; }
}
</script>

<template>
  <header class="topbar"><a class="brand" href="/">CodeDog</a><a class="button button-quiet" href="/">返回文档</a></header>
  <main class="auth-shell">
    <section class="auth-panel" aria-labelledby="login-title">
      <h1 id="login-title">管理员登录</h1>
      <div v-if="error" class="notice notice-error">{{ error }}</div>
      <form class="form-stack" @submit.prevent="submit">
        <label><span>用户名</span><input v-model.trim="username" autocomplete="username" required autofocus></label>
        <label><span>密码</span><input v-model="password" type="password" autocomplete="current-password" required></label>
        <button class="button button-primary button-full" type="submit" :disabled="busy">{{ busy ? "登录中..." : "登录" }}</button>
      </form>
    </section>
  </main>
</template>
