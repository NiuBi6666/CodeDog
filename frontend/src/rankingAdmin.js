export function rankingVisibleRows(rows = [], selectedStudentId = "", limit = 10) {
  const values = Array.isArray(rows) ? rows : [];
  const leaders = values.slice(0, Math.max(0, limit));
  const selected = values.find((row) => String(row.studentId) === String(selectedStudentId));
  if (!selected || leaders.some((row) => String(row.studentId) === String(selected.studentId))) {
    return leaders;
  }
  return [...leaders, selected];
}

export function rankingAvatarText(name) {
  const characters = Array.from(String(name || "").trim());
  return characters.slice(-2).join("") || "?";
}

export function rankingPointsToPass(rows = [], index = -1) {
  if (index <= 0) return 0;
  const currentPoints = Number(rows[index]?.totalPoints || 0);
  for (let previous = index - 1; previous >= 0; previous -= 1) {
    const previousPoints = Number(rows[previous]?.totalPoints || 0);
    if (previousPoints > currentPoints) return previousPoints - currentPoints + 1;
  }
  return 0;
}

export function rankingTrendView(row = {}) {
  if (row.previousRank == null) return { direction: "same", label: "-", title: "暂无历史排名" };
  const change = Number(row.rankChange || 0);
  if (change > 0) return { direction: "up", label: `↑ ${change}`, title: `上升 ${change} 名` };
  if (change < 0) return { direction: "down", label: `↓ ${Math.abs(change)}`, title: `下降 ${Math.abs(change)} 名` };
  return { direction: "same", label: "-", title: "排名持平" };
}

export function rankingRedemptionStatus(status) {
  return status === "FULFILLED"
    ? { label: "已发放", tone: "fulfilled" }
    : { label: "待老师发放", tone: "pending" };
}

export function rankingRedemptionTime(value) {
  if (!value) return "时间未知";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return "时间未知";
  const parts = new Intl.DateTimeFormat("zh-CN", {
    timeZone: "Asia/Shanghai",
    year: "numeric", month: "2-digit", day: "2-digit",
    hour: "2-digit", minute: "2-digit", hour12: false
  }).formatToParts(date).reduce((values, part) => {
    values[part.type] = part.value;
    return values;
  }, {});
  return `${parts.year}-${parts.month}-${parts.day} ${parts.hour}:${parts.minute}`;
}

export function rankingShareUrl({ origin }) {
  const url = new URL("/ranking-board", origin);
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
