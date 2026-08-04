export function rankingAvatarText(name) {
  const characters = Array.from(String(name || "").trim());
  return characters.slice(-2).join("") || "?";
}

export function rankingTrendView(row = {}) {
  if (row.previousRank == null) return { direction: "same", label: "-", title: "暂无历史排名" };
  const change = Number(row.rankChange || 0);
  if (change > 0) return { direction: "up", label: `↑ ${change}`, title: `上升 ${change} 名` };
  if (change < 0) return { direction: "down", label: `↓ ${Math.abs(change)}`, title: `下降 ${Math.abs(change)} 名` };
  return { direction: "same", label: "-", title: "排名持平" };
}

export function rankingShareUrl({ origin, teacherId, campId, classId = "", scope = "class" }) {
  const normalizedScope = scope === "camp" ? "camp" : "class";
  const url = new URL("/rankings.html", origin);
  if (teacherId) url.searchParams.set("teacher", String(teacherId));
  if (campId) url.searchParams.set("camp", String(campId));
  if (normalizedScope === "class" && classId) url.searchParams.set("class", String(classId));
  url.searchParams.set("scope", normalizedScope);
  return url.href;
}

export function rankingSummary(rows = []) {
  const values = Array.isArray(rows) ? rows : [];
  const totalPoints = values.reduce((sum, row) => sum + Number(row.totalPoints || 0), 0);
  return {
    studentCount: values.length,
    totalPoints,
    averagePoints: values.length ? Math.round(totalPoints / values.length) : 0
  };
}
