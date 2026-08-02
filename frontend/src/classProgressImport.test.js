import { describe, expect, it } from "vitest";
import { assignmentCompletion, buildImportMatrix, filterImportedRows, matrixToCsv, uniqueColumnValues } from "./classProgressImport";

const columns = [
  { key: "A", label: "学员ID" },
  { key: "B", label: "学员姓名" },
  { key: "M", label: "课程名称" }
];
const rows = [
  { values: { A: "1001", B: "张三", M: "二分算法", U: "5", W: "5", AC: "3", AE: "3" } },
  { values: { A: "1002", B: "李四", M: "动态规划", U: "5", W: "3", AC: "0", AE: "0" } },
  { values: { A: "1003", B: "王五", M: "二分算法", U: "0", W: "0", AC: "2", AE: "1" } }
];

describe("class progress Excel helpers", () => {
  it("filters by student id, name, class or course", () => {
    expect(filterImportedRows(rows, "张三")).toEqual([rows[0]]);
    expect(filterImportedRows(rows, "1002")).toEqual([rows[1]]);
    expect(filterImportedRows(rows, "动态")).toEqual([rows[1]]);
  });

  it("filters course and derived assignment completion together", () => {
    expect(assignmentCompletion(rows[0], "U", "W")).toBe("complete");
    expect(assignmentCompletion(rows[1], "U", "W")).toBe("incomplete");
    expect(filterImportedRows(rows, "", { M: "二分算法", afterClassCompletion: "incomplete" }))
      .toEqual([rows[2]]);
    expect(filterImportedRows(rows, "李四", { inClassCompletion: "incomplete", afterClassCompletion: "complete" }))
      .toEqual([rows[1]]);
    expect(uniqueColumnValues(rows, "M")).toEqual(["二分算法", "动态规划"]);
  });

  it("builds a copyable matrix and escaped CSV", () => {
    const matrix = buildImportMatrix(columns, rows);
    expect(matrix).toContain("A 学员ID\tB 学员姓名\tM 课程名称");
    expect(matrixToCsv(matrix)).toContain('"1001","张三","二分算法"');
  });
});
