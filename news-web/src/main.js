import { createApp } from "vue";
import { createRouter, createWebHistory } from "vue-router";
import App from "./App.vue";
import "./style.css";

async function bootstrap() {
const newspaper = /^\/newspaper(?:\/|$)/.test(location.pathname);
const Root = newspaper ? (await import("./Newspaper.vue")).default : App;
if (newspaper) document.body.classList.add("newspaper-page");
const router = createRouter({
  history: createWebHistory(newspaper ? "/newspaper" : "/news/"),
  routes: [{ path: "/", component: App }, { path: "/article/:id", component: App }],
  scrollBehavior: (to, from, savedPosition) => newspaper ? false : savedPosition || { top: 0 },
});
createApp(Root).use(router).mount("#app");
}
bootstrap();
