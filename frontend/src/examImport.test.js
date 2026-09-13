import { describe, it, expect } from "vitest";
import { displayExamScore, suggestExamColumns, examUrl } from "./examImport";
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
    expect(examUrl("/exam/abc", "https://codedog.online")).toBe("https://codedog.online/exam/abc");
    expect(examUrl("/exam/def", "https://codedog.online")).not.toBe(examUrl("/exam/abc", "https://codedog.online"));
  });
});
