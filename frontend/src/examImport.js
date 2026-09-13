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
export const EXAM_URL_PREFIX = "https://codedog.online/exam/";
export function validExamSuffix(suffix) {
  return typeof suffix === "string" && /^(?=.*[A-Z])(?=.*[a-z])(?=.*[0-9])[A-Za-z0-9]{8}$/.test(suffix);
}
export function examUrl(path) {
  const match = /^\/exam\/([A-Za-z0-9]{8}|[0-9a-f]{32})$/.exec(path);
  if (!match) throw new Error("查询路径无效");
  return EXAM_URL_PREFIX + match[1];
}
