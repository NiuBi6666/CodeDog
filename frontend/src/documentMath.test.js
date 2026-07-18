import { describe, expect, it } from "vitest";
import { parseMathText } from "./documentMath";

describe("document math compatibility", () => {
  it("recognizes raw DingTalk LaTeX fractions without delimiters", () => {
    expect(parseMathText(" \\frac{3^{30}-1}{3-1}=\\frac{3^{30}-1}{2} ")).toEqual([
      { type: "text", value: " " },
      { type: "math", value: "\\frac{3^{30}-1}{3-1}=\\frac{3^{30}-1}{2}", display: false },
      { type: "text", value: " " },
    ]);
  });

  it("recognizes inline and display delimiters while preserving prose", () => {
    expect(parseMathText("结果为 \\(x^2+1\\)，以及 $$\\sum_{i=1}^n i$$。"))
      .toEqual([
        { type: "text", value: "结果为 " },
        { type: "math", value: "x^2+1", display: false },
        { type: "text", value: "，以及 " },
        { type: "math", value: "\\sum_{i=1}^n i", display: true },
        { type: "text", value: "。" },
      ]);
  });

  it("recognizes DingTalk exponent expressions without commands", () => {
    expect(parseMathText("3^{29}+3^{28}+\\cdots+3+1"))
      .toEqual([{ type: "math", value: "3^{29}+3^{28}+\\cdots+3+1", display: false }]);
  });

  it("extracts an undelimited formula following Chinese prose", () => {
    expect(parseMathText("结果为：\\frac{3^{30}-1}{2}。"))
      .toEqual([
        { type: "text", value: "结果为：" },
        { type: "math", value: "\\frac{3^{30}-1}{2}", display: false },
        { type: "text", value: "。" },
      ]);
  });

  it("does not treat ordinary Chinese text as a formula", () => {
    expect(parseMathText("第 6 题：计算总进位次数。"))
      .toEqual([{ type: "text", value: "第 6 题：计算总进位次数。" }]);
  });
});
