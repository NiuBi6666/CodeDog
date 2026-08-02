<script setup>
import { reactive, ref } from "vue";
import { RouterLink, useRouter } from "vue-router";
import { UserPlus } from "@lucide/vue";
import { register } from "../auth";
import { notify } from "../api";

const form = reactive({ username: "", password: "", confirmation: "" });
const error = ref("");
const busy = ref(false);
const router = useRouter();

async function submit() {
  error.value = "";
  busy.value = true;
  try {
    const result = await register(form.username, form.password, form.confirmation);
    notify("注册成功，请使用新账号登录");
    await router.replace({ path: "/login", query: { username: result.username } });
  } catch (failure) {
    error.value = failure.message;
  } finally {
    busy.value = false;
  }
}
</script>

<template>
  <header class="login-topbar">
    <RouterLink class="login-brand" to="/login" aria-label="CodeDog 登录">
      <span class="login-brand-mark" aria-hidden="true">CD</span>
      <span>CodeDog</span>
    </RouterLink>
  </header>
  <main class="login-shell">
    <section class="login-panel" aria-labelledby="register-title">
      <div class="login-heading">
        <h1 id="register-title">注册 CodeDog</h1>
        <p>新账号默认仅可访问后台首页</p>
      </div>
      <div v-if="error" class="notice notice-error login-notice">{{ error }}</div>
      <form class="login-form" @submit.prevent="submit">
        <label><span>用户名</span><input v-model.trim="form.username" autocomplete="username" minlength="3" maxlength="32" pattern="[A-Za-z0-9_.-]+" placeholder="3-32 位字母、数字或 _ . -" required></label>
        <label><span>密码</span><input v-model="form.password" type="password" autocomplete="new-password" minlength="8" maxlength="72" placeholder="请输入 8-72 位密码" required></label>
        <label><span>确认密码</span><input v-model="form.confirmation" type="password" autocomplete="new-password" minlength="8" maxlength="72" placeholder="请再次输入密码" required></label>
        <button class="login-submit" type="submit" :disabled="busy"><UserPlus v-if="!busy" :size="17"/><span>{{ busy ? "注册中..." : "创建账号" }}</span></button>
        <RouterLink class="login-secondary-action" to="/login">已有账号，返回登录</RouterLink>
      </form>
    </section>
  </main>
</template>
