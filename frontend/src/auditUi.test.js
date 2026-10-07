import { describe, expect, it } from "vitest";
import { actorLabel, buildAuditQuery, formatAuditValue, resultLabel } from "./auditUi";

describe("audit UI helpers", () => {
  it("keeps active filters and omits empty values", () => {
    const query = buildAuditQuery({ module: "documents", requestId: "", statusCode: 403 }, 2);
    expect(query.toString()).toBe("module=documents&statusCode=403&page=2");
  });

  it("formats structured values without losing long text", () => {
    const value = { content: "完整正文", nested: { before: 1, after: 2 } };
    expect(formatAuditValue(value)).toContain("完整正文");
    expect(formatAuditValue(value)).toContain('"after": 2');
  });

  it("provides stable Chinese labels", () => {
    expect(resultLabel("rejected")).toBe("已拒绝");
    expect(actorLabel("EXTENSION_DEVICE")).toBe("扩展设备");
  });
});
