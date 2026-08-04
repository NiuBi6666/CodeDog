import { createRouter, createWebHistory } from "vue-router";
import { ensureUser, hasPermission } from "./auth";
import ClassProgressView from "./views/ClassProgressImportView.vue";
import DashboardView from "./views/DashboardView.vue";
import DocumentEditView from "./views/DocumentEditView.vue";
import DocumentListView from "./views/DocumentListView.vue";
import ForbiddenView from "./views/ForbiddenView.vue";
import LoginView from "./views/LoginView.vue";
import LogListView from "./views/LogListView.vue";
import PasswordView from "./views/PasswordView.vue";
import PublicDocumentView from "./views/PublicDocumentView.vue";
import QuestionnaireView from "./views/QuestionnaireView.vue";
import RegisterView from "./views/RegisterView.vue";
import RankingView from "./views/RankingView.vue";
import StudentQueryView from "./views/StudentQueryView.vue";
import UserPermissionsView from "./views/UserPermissionsView.vue";

const routes = [
  { path: "/", redirect: "/index" },
  { path: "/doc/show/:id", component: PublicDocumentView, meta: { bodyClass: "reader-page", title: "CodeDog" } },
  { path: "/documents/:id", redirect: (to) => `/doc/show/${to.params.id}` },
  { path: "/login", component: LoginView, meta: { bodyClass: "auth-page", title: "登录 - CodeDog" } },
  { path: "/register", component: RegisterView, meta: { bodyClass: "auth-page", title: "注册 - CodeDog" } },
  { path: "/index", component: DashboardView, meta: { auth: true, permission: "dashboard.view", bodyClass: "admin-layout", title: "首页 - CodeDog" } },
  { path: "/student/query", component: StudentQueryView, meta: { auth: true, permission: "students.view", bodyClass: "admin-layout", title: "查询学生 - CodeDog" } },
  { path: "/class/progress", component: ClassProgressView, meta: { auth: true, permission: "class_progress.view", bodyClass: "admin-layout", title: "课堂完成情况 - CodeDog" } },
  { path: "/questionnaire", component: QuestionnaireView, meta: { auth: true, permission: "questionnaire.view", bodyClass: "admin-layout", title: "问卷与作业 - CodeDog" } },
  { path: "/rankings", component: RankingView, meta: { auth: true, bodyClass: "admin-layout", title: "学生排名 - CodeDog" } },
  { path: "/doc/list", component: DocumentListView, meta: { auth: true, permission: "documents.view", bodyClass: "admin-layout", title: "文档管理 - CodeDog" } },
  { path: "/logs", component: LogListView, meta: { auth: true, permission: "logs.view", bodyClass: "admin-layout", title: "操作日志 - CodeDog" } },
  { path: "/users", component: UserPermissionsView, meta: { auth: true, admin: true, bodyClass: "admin-layout", title: "用户与权限 - CodeDog" } },
  { path: "/forbidden", component: ForbiddenView, meta: { auth: true, bodyClass: "admin-layout", title: "无访问权限 - CodeDog" } },
  { path: "/system/logs", redirect: "/logs" },
  { path: "/admin/documents", redirect: "/doc/list" },
  { path: "/doc/create", component: DocumentEditView, meta: { auth: true, permission: "documents.create", bodyClass: "admin-layout", title: "新建文档 - CodeDog" } },
  { path: "/admin/documents/new", redirect: "/doc/create" },
  { path: "/doc/edit/:id", component: DocumentEditView, meta: { auth: true, permission: "documents.edit", bodyClass: "admin-layout", title: "编辑文档 - CodeDog" } },
  { path: "/admin/documents/:id/edit", redirect: (to) => `/doc/edit/${to.params.id}` },
  { path: "/password", component: PasswordView, meta: { auth: true, bodyClass: "admin-layout", title: "修改密码 - CodeDog" } },
  { path: "/edit", redirect: "/doc/list" },
  { path: "/:pathMatch(.*)*", component: PublicDocumentView, props: { notFound: true }, meta: { bodyClass: "state-page", title: "页面不存在" } },
];

const router = createRouter({ history: createWebHistory(), routes });
router.beforeEach(async (to) => {
  if (!to.meta.auth) return true;
  const user = await ensureUser(true);
  if (!user) return { path: "/login", query: { redirect: to.fullPath } };
  if (to.meta.admin && !user.admin) return { path: "/forbidden" };
  if (to.meta.permission && !hasPermission(to.meta.permission)) return { path: "/forbidden" };
  return true;
});
export default router;
