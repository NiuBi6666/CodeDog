let csrfToken = "";

export class ApiError extends Error {
  constructor(message, status, payload = {}) {
    super(message);
    this.status = status;
    this.payload = payload;
  }
}

async function loadCsrf() {
  const response = await fetch("/api/auth/csrf", { credentials: "same-origin" });
  const payload = await response.json();
  csrfToken = payload.token;
}

export async function api(path, options = {}) {
  const method = (options.method || "GET").toUpperCase();
  const csrfProtected = !["GET", "HEAD", "OPTIONS"].includes(method);
  if (csrfProtected && !csrfToken) await loadCsrf();

  const createHeaders = () => {
    const headers = new Headers(options.headers || {});
    const multipart = typeof FormData !== "undefined" && options.body instanceof FormData;
    if (options.body && !multipart && !headers.has("Content-Type")) headers.set("Content-Type", "application/json");
    if (csrfProtected) headers.set("X-XSRF-TOKEN", csrfToken);
    return headers;
  };
  const send = () => fetch(`/api${path}`, { ...options, method, headers: createHeaders(), credentials: "same-origin" });

  let response = await send();
  if (csrfProtected && response.status === 403 && ["/auth/login", "/auth/logout", "/auth/register"].includes(path)) {
    csrfToken = "";
    await loadCsrf();
    response = await send();
  }
  if (path === "/auth/logout") csrfToken = "";

  const contentType = response.headers.get("content-type") || "";
  const payload = response.status === 204 ? null : contentType.includes("application/json") ? await response.json() : {};
  if (!response.ok) throw new ApiError(payload?.error || "请求失败", response.status, payload);
  return payload;
}

export function jsonBody(value) { return JSON.stringify(value); }
export function notify(message) { window.dispatchEvent(new CustomEvent("codedog-toast", { detail: message })); }

export async function writeClipboard(text) {
  try {
    await navigator.clipboard.writeText(text);
  } catch (_error) {
    const textarea = document.createElement("textarea");
    textarea.value = text;
    textarea.setAttribute("readonly", "");
    textarea.style.position = "fixed";
    textarea.style.opacity = "0";
    document.body.appendChild(textarea);
    textarea.select();
    const copied = document.execCommand("copy");
    textarea.remove();
    if (!copied) throw new Error("clipboard unavailable");
  }
}
