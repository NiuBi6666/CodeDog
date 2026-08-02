<script setup>
import { ShieldX } from "@lucide/vue";
import AdminLayout from "../components/AdminLayout.vue";
</script>

<template>
  <AdminLayout page-title="无访问权限" active-page="">
    <section class="permission-denied-state">
      <ShieldX :size="38"/>
      <h1>无访问权限</h1>
      <p>当前账号尚未获得此页面或操作的权限，请联系系统管理员设置。</p>
      <RouterLink class="button button-primary" to="/index">返回首页</RouterLink>
    </section>
  </AdminLayout>
</template>
