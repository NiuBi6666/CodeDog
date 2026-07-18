import { describe, expect, it } from "vitest";
import { documentShareUrl, splitQueryValues, statusLabel } from "./utils";

describe("frontend utilities", () => {
  it("deduplicates mixed student query input", () => {
    expect(splitQueryValues("张三\n张三， 李四")).toEqual(["张三", "李四"]);
  });
  it("renders document status labels", () => {
    expect(statusLabel("normal")).toBe("正常");
    expect(statusLabel("offline")).toBe("下线");
  });
  it("builds a public document share URL", () => {
    expect(documentShareUrl("a1b2c3d4", "https://codedog.online"))
      .toBe("https://codedog.online/doc/show/a1b2c3d4");
  });
});
