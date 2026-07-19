<script setup>
import { ref } from "vue";
import { RouterLink, useRouter } from "vue-router";
import { ClipboardCheck, ExternalLink, FileText, Home, ListChecks, Menu, ScrollText, Search } from "@lucide/vue";
import { auth, logout } from "../auth";

defineProps({ pageTitle: { type: String, required: true }, activePage: { type: String, required: true } });
const sidebarOpen = ref(false);
const router = useRouter();
async function signOut() { await logout(); await router.push("/"); }
</script>

<template>
  <div class="admin-app" :class="{ 'sidebar-open': sidebarOpen }" data-admin-shell>
    <aside class="admin-sidebar" aria-label="后台导航">
      <RouterLink class="admin-logo" to="/index"><span class="admin-logo-mark">C</span><span>CodeDog</span></RouterLink>
      <div class="admin-profile"><span class="admin-avatar">A</span><div><strong>{{ auth.user?.username }}</strong><span><i></i> 在线</span></div></div>
      <nav class="admin-nav">
        <p>功能导航</p>
        <RouterLink :class="{ active: activePage === 'dashboard' }" to="/index"><Home class="nav-icon" :size="17"/><span>首页</span></RouterLink>
        <RouterLink :class="{ active: activePage === 'students' }" to="/student/query"><Search class="nav-icon" :size="17"/><span>查询学生</span></RouterLink>
        <RouterLink :class="{ active: activePage === 'class-progress' }" to="/class/progress"><ClipboardCheck class="nav-icon" :size="17"/><span>课堂完成情况</span></RouterLink>
        <RouterLink :class="{ active: activePage === 'questionnaire' }" to="/questionnaire"><ListChecks class="nav-icon" :size="17"/><span>问卷与作业</span></RouterLink>
        <RouterLink :class="{ active: activePage === 'documents' }" to="/doc/list"><FileText class="nav-icon" :size="17"/><span>文档管理</span></RouterLink>
        <RouterLink :class="{ active: activePage === 'logs' }" to="/logs"><ScrollText class="nav-icon" :size="17"/><span>操作日志</span></RouterLink>
      </nav>
      <div class="admin-sidebar-footer">CodeDog Admin</div>
    </aside>
    <button class="sidebar-overlay" type="button" @click="sidebarOpen = false" aria-label="关闭导航"></button>
    <section class="admin-workspace">
      <header class="admin-header">
        <button class="icon-button sidebar-toggle" type="button" @click="sidebarOpen = true" aria-label="打开导航" title="打开导航"><Menu :size="19"/></button>
        <div class="admin-breadcrumb"><span>CodeDog</span><b>/</b><strong>{{ pageTitle }}</strong></div>
        <nav class="admin-header-actions" aria-label="账户操作">
          <a class="icon-button" href="/" target="_blank" title="公开首页" aria-label="公开首页"><ExternalLink :size="17"/></a>
          <RouterLink class="header-action" :class="{ active: activePage === 'account' }" to="/password">修改密码</RouterLink>
          <button class="header-action" type="button" @click="signOut">退出</button>
        </nav>
      </header>
      <main class="admin-main"><slot /></main>
    </section>
  </div>
</template>
