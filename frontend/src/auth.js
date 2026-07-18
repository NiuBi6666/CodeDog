import { reactive } from "vue";
import { api, jsonBody } from "./api";

export const auth = reactive({ user: null, checked: false });

export async function ensureUser(force = false) {
  if (auth.checked && !force) return auth.user;
  try {
    auth.user = await api("/auth/me");
  } catch (error) {
    if (error.status !== 401) throw error;
    auth.user = null;
  } finally {
    auth.checked = true;
  }
  return auth.user;
}

export async function login(username, password) {
  auth.user = await api("/auth/login", { method: "POST", body: jsonBody({ username, password }) });
  auth.checked = true;
}

export async function logout() {
  await api("/auth/logout", { method: "POST" });
  auth.user = null;
  auth.checked = true;
}
