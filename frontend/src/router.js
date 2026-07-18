import { createRouter, createWebHistory } from "vue-router";
import { ensureUser } from "./auth";
import DashboardView from "./views/DashboardView.vue";
import DocumentEditView from "./views/DocumentEditView.vue";
import DocumentListView from "./views/DocumentListView.vue";
import LoginView from "./views/LoginView.vue";
import LogListView from "./views/LogListView.vue";
import PasswordView from "./views/PasswordView.vue";
import PublicDocumentView from "./views/PublicDocumentView.vue";
import StudentQueryView from "./views/StudentQueryView.vue";

const routes = [
  { path: "/", redirect: "/index" },
  { path: "/doc/show/:id", component: PublicDocumentView, meta: { bodyClass: "reader-page", title: "CodeDog" } },
  { path: "/documents/:id", redirect: (to) => `/doc/show/${to.params.id}` },
  { path: "/login", component: LoginView, meta: { bodyClass: "auth-page", title: "登录 - CodeDog" } },
  { path: "/index", component: DashboardView, meta: { auth: true, bodyClass: "admin-layout", title: "首页 - CodeDog" } },
  { path: "/student/query", component: StudentQueryView, meta: { auth: true, bodyClass: "admin-layout", title: "查询学生 - CodeDog" } },
  { path: "/doc/list", component: DocumentListView, meta: { auth: true, bodyClass: "admin-layout", title: "文档管理 - CodeDog" } },
  { path: "/logs", component: LogListView, meta: { auth: true, bodyClass: "admin-layout", title: "操作日志 - CodeDog" } },
  { path: "/system/logs", redirect: "/logs" },
  { path: "/admin/documents", redirect: "/doc/list" },
  { path: "/doc/create", component: DocumentEditView, meta: { auth: true, bodyClass: "admin-layout", title: "新建文档 - CodeDog" } },
  { path: "/admin/documents/new", redirect: "/doc/create" },
  { path: "/doc/edit/:id", component: DocumentEditView, meta: { auth: true, bodyClass: "admin-layout", title: "编辑文档 - CodeDog" } },
  { path: "/admin/documents/:id/edit", redirect: (to) => `/doc/edit/${to.params.id}` },
  { path: "/password", component: PasswordView, meta: { auth: true, bodyClass: "admin-layout", title: "修改密码 - CodeDog" } },
  { path: "/edit", redirect: "/doc/list" },
  { path: "/:pathMatch(.*)*", component: PublicDocumentView, props: { notFound: true }, meta: { bodyClass: "state-page", title: "页面不存在" } },
];

const router = createRouter({ history: createWebHistory(), routes });
router.beforeEach(async (to) => {
  if (!to.meta.auth) return true;
  const user = await ensureUser();
  return user ? true : { path: "/login", query: { redirect: to.fullPath } };
});
export default router;
