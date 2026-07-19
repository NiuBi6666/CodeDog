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
  <header class="login-topbar">
    <a class="login-brand" href="/index" aria-label="CodeDog 首页">
      <span class="login-brand-mark" aria-hidden="true">CD</span>
      <span>CodeDog</span>
    </a>
  </header>
  <main class="login-shell">
    <section class="login-panel" aria-labelledby="login-title">
      <div class="login-heading">
        <h1 id="login-title">登录 CodeDog</h1>
        <p>进入管理后台</p>
      </div>
      <div v-if="error" class="notice notice-error login-notice">{{ error }}</div>
      <form class="login-form" @submit.prevent="submit">
        <label>
          <span>用户名</span>
          <input v-model.trim="username" autocomplete="username" placeholder="请输入用户名" required>
        </label>
        <label>
          <span>密码</span>
          <input v-model="password" type="password" autocomplete="current-password" placeholder="请输入密码" required>
        </label>
        <button class="login-submit" type="submit" :disabled="busy">
          <span>{{ busy ? "登录中..." : "登录" }}</span>
          <span v-if="!busy" class="login-submit-arrow" aria-hidden="true">→</span>
        </button>
      </form>
    </section>
  </main>
</template>
