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
