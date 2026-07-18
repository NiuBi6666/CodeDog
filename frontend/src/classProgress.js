export function buildStudentProgressRows(questions = []) {
  const students = new Map();
  for (const question of questions) {
    for (const result of question.students || []) {
      const current = students.get(result.id) || { id: result.id, name: result.name, results: {} };
      if (!current.name && result.name) current.name = result.name;
      current.results[question.stepId] = result;
      students.set(result.id, current);
    }
  }
  return [...students.values()].sort((left, right) =>
    (left.name || "").localeCompare(right.name || "", "zh-CN") || left.id.localeCompare(right.id));
}

export function progressResult(student, stepId) {
  return student?.results?.[stepId] || { result: "", resultLabel: "未提交", submitTime: 0 };
}

export function progressStatusClass(result) {
  if (result === "AC") return "status-normal";
  if (!result) return "status-offline";
  return "status-warning";
}

export function formatLessonState(state) {
  return ({ END: "已结束", STARTING: "进行中", NOT_START: "未开始" })[state] || state || "未知";
}
