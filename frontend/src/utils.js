export function formatDateTime(value) {
  if (!value) return "";
  return new Intl.DateTimeFormat("zh-CN", {
    timeZone: "Asia/Shanghai", year: "numeric", month: "2-digit", day: "2-digit",
    hour: "2-digit", minute: "2-digit", hour12: false,
  }).format(new Date(value)).replaceAll("/", "-");
}

export function splitQueryValues(raw) {
  return [...new Set((raw || "").split(/[\n\r,，;；]+/).map((value) => value.replaceAll(/\s/g, "")).filter(Boolean))];
}

export function statusLabel(status) { return status === "normal" ? "正常" : "下线"; }

export function documentShareUrl(id, origin) {
  return new URL(`/doc/show/${encodeURIComponent(id)}`, origin).href;
}
