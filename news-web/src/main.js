import { createApp } from "vue";
import { createRouter, createWebHistory } from "vue-router";
import App from "./App.vue";
import "./style.css";

const router = createRouter({
  history: createWebHistory("/news/"),
  routes: [{ path: "/", component: App }, { path: "/article/:id", component: App }, {path:"/events",component:App}, {path:"/event/:eventId",component:App}],
  scrollBehavior: (to, from, savedPosition) => savedPosition || { top: 0 },
});
createApp(App).use(router).mount("#app");
