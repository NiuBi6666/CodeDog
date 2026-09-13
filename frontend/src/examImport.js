export function displayExamScore(value) {
  const text = value == null ? "" : String(value).trim();
  if (!text) return "暂无成绩";
  return /^[-+]?0(?:\.0+)?%?$/.test(text) ? "未参考" : text;
}
export function suggestExamColumns(columns) {
  const name = columns.find(c => /姓名/.test(c.label));
  return {
    nameColumn: name?.index ?? columns[0]?.index ?? 0,
    scoreColumns: columns.filter(c => c.index !== name?.index && /成绩|分数|得分/.test(c.label)).slice(0, 20).map(c => c.index)
  };
}
export function examUrl(path, origin) { return new URL(path, origin).href; }
