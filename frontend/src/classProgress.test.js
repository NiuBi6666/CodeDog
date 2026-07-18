import { describe, expect, it } from "vitest";
import { buildStudentProgressRows, formatLessonState, progressResult, progressStatusClass } from "./classProgress";

describe("class progress utilities", () => {
  const questions = [
    { stepId: "q1", students: [{ id: "2", name: "李四", result: "AC", resultLabel: "通过" }] },
    { stepId: "q2", students: [
      { id: "1", name: "张三", result: "WA", resultLabel: "答案错误" },
      { id: "2", name: "李四", result: "", resultLabel: "未提交" },
    ] },
  ];

  it("builds a stable student by question matrix", () => {
    const rows = buildStudentProgressRows(questions);
    expect(rows.map((row) => row.name)).toEqual(["李四", "张三"]);
    expect(progressResult(rows[0], "q1").resultLabel).toBe("通过");
    expect(progressResult(rows[1], "q1").resultLabel).toBe("未提交");
  });

  it("maps result and lesson states", () => {
    expect(progressStatusClass("AC")).toBe("status-normal");
    expect(progressStatusClass("WA")).toBe("status-warning");
    expect(progressStatusClass("")).toBe("status-offline");
    expect(formatLessonState("END")).toBe("已结束");
  });
});
