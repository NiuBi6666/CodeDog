import { describe, it, expect } from "vitest";
import { displayExamScore, suggestExamColumns, examUrl, displayResultScore, isAbsentSubmission, visibleExamScores, validExamSuffix, EXAM_URL_PREFIX } from "./examImport";
describe("exam upload mapping and score display", () => {
  it("finds B/W/X/Y in a mixed student export", () => {
    const columns = Array.from({length:25}, (_,index) => ({index,label:"其他信息"}));
    columns[1].label="用户姓名";columns[22].label="一模首次成绩";columns[23].label="二模首次成绩";columns[24].label="三模首次成绩";
    expect(suggestExamColumns(columns)).toEqual({nameColumn:1,scoreColumns:[22,23,24]});
  });
  it("distinguishes zero, missing values, absent exam and nonzero decimals", () => {
    expect([0,"0","0.0","0%"].map(displayExamScore)).toEqual(["未参考","未参考","未参考","未参考"]);
    expect([null,"","  "].map(displayExamScore)).toEqual(["暂无成绩","暂无成绩","暂无成绩"]);
    expect(displayExamScore("33.5")).toBe("33.5");
    expect(displayExamScore("未参考")).toBe("未参考");
  });
  it("keeps a distinct URL for each exam", () => {
    expect(examUrl("/exam/Abcd1234", "https://codedog.online")).toBe("https://codedog.online/exam/Abcd1234");
    expect(examUrl("/exam/Next123a", "https://codedog.online")).not.toBe(examUrl("/exam/Abcd1234", "https://codedog.online"));
  });
  it("requires exactly eight case-sensitive letters and digits of all three classes", () => {
    expect(validExamSuffix("Abcd1234")).toBe(true);
    expect(validExamSuffix("aBcd1234")).toBe(true);
    for (const value of ["12345678", "abcdefgh", "ABCDEFGH", "abcd1234", "ABCD1234", "AbCdEfGh", "Abc1234", "Abcd12345", "Abcd12/3", " Abcd123", null])
      expect(validExamSuffix(value)).toBe(false);
  });
  it("keeps the canonical prefix fixed and preserves case", () => {
    expect(EXAM_URL_PREFIX).toBe("https://codedog.online/exam/");
    expect(examUrl("/exam/Abcd1234", "https://another.example")).toBe(EXAM_URL_PREFIX + "Abcd1234");
    expect(examUrl("/exam/aBcd1234")).not.toBe(examUrl("/exam/Abcd1234"));
    expect(() => examUrl("https://another.example/exam/Abcd1234")).toThrow();
    expect(() => examUrl("/other/Abcd1234")).toThrow();
  });
  it("preserves real zero scores for templates and keeps legacy display", () => {
    expect(displayResultScore(0, "full")).toBe("0");
    expect(displayResultScore("0.0", "simple")).toBe("0.0");
    expect(displayResultScore(0, "legacy")).toBe("未参考");
    expect(displayResultScore(null, "full")).toBe("暂无成绩");
    expect(displayResultScore("6.5", "full")).toBe("6.5");
  });
  it("uses only the submission NaT marker to identify absence", () => {
    expect(isAbsentSubmission(" NaT ")).toBe(true);
    expect(isAbsentSubmission("nat")).toBe(true);
    for (const value of ["", null, 0, "2026-09-12 12:30:00"])
      expect(isAbsentSubmission(value)).toBe(false);
  });
  it("omits empty detail cells while preserving zero and each score label index", () => {
    expect(visibleExamScores(["80", "", 0, "0.0", null, "7.5"], "full")).toEqual([
      {score:"80", index:0}, {score:0, index:2}, {score:"0.0", index:3}, {score:"7.5", index:5}
    ]);
    expect(visibleExamScores(["", ""], "legacy")).toHaveLength(2);
    expect(visibleExamScores([""], "simple")).toEqual([{score:"", index:0}]);
  });
});
