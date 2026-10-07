export function buildAuditQuery(filters, page = 0) {
  const params = new URLSearchParams();
  for (const [key, value] of Object.entries(filters)) {
    if (value !== "" && value !== null && value !== undefined) params.set(key, String(value));
  }
  if (page > 0) params.set("page", String(page));
  return params;
}

export function resultLabel(value) {
  return { success: "成功", failed: "失败", rejected: "已拒绝" }[value] || value || "未知";
}

export function actorLabel(value) {
  return { TEACHER: "教师", STUDENT: "学生", EXTENSION_DEVICE: "扩展设备", ANONYMOUS: "匿名",
    SYSTEM: "系统任务", UNKNOWN: "未知" }[value] || value || "未知";
}

export function formatAuditValue(value) {
  if (value === null || value === undefined || value === "") return "-";
  if (typeof value === "string") return value;
  return JSON.stringify(value, null, 2);
}
