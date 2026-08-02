import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

function jsonResponse(body, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" }
  });
}

describe("CSRF recovery", () => {
  beforeEach(() => {
    vi.resetModules();
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("refreshes a stale token and retries registration once", async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(jsonResponse({ token: "stale-token" }))
      .mockResolvedValueOnce(jsonResponse({ error: "安全令牌已失效，请重试" }, 403))
      .mockResolvedValueOnce(jsonResponse({ token: "fresh-token" }))
      .mockResolvedValueOnce(jsonResponse({ ok: true, username: "member" }, 201));
    vi.stubGlobal("fetch", fetchMock);

    const { api, jsonBody } = await import("./api");
    const result = await api("/auth/register", {
      method: "POST",
      body: jsonBody({ username: "member", password: "password123", confirmation: "password123" })
    });

    expect(result.username).toBe("member");
    expect(fetchMock).toHaveBeenCalledTimes(4);
    expect(fetchMock.mock.calls[1][1].headers.get("X-XSRF-TOKEN")).toBe("stale-token");
    expect(fetchMock.mock.calls[3][1].headers.get("X-XSRF-TOKEN")).toBe("fresh-token");
  });

  it("clears the cached token after logout", async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(jsonResponse({ token: "logout-token" }))
      .mockResolvedValueOnce(jsonResponse({ ok: true }))
      .mockResolvedValueOnce(jsonResponse({ token: "next-token" }))
      .mockResolvedValueOnce(jsonResponse({ ok: true, username: "member" }, 201));
    vi.stubGlobal("fetch", fetchMock);

    const { api, jsonBody } = await import("./api");
    await api("/auth/logout", { method: "POST" });
    await api("/auth/register", {
      method: "POST",
      body: jsonBody({ username: "member", password: "password123", confirmation: "password123" })
    });

    expect(fetchMock).toHaveBeenCalledTimes(4);
    expect(fetchMock.mock.calls[1][1].headers.get("X-XSRF-TOKEN")).toBe("logout-token");
    expect(fetchMock.mock.calls[3][1].headers.get("X-XSRF-TOKEN")).toBe("next-token");
  });

  it("lets the browser set the multipart boundary for Excel uploads", async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(jsonResponse({ token: "upload-token" }))
      .mockResolvedValueOnce(jsonResponse({ fileCount: 1, rowCount: 2, classes: [] }));
    vi.stubGlobal("fetch", fetchMock);

    const { api } = await import("./api");
    const body = new FormData();
    body.append("files", new Blob(["xlsx"]), "class.xlsx");
    await api("/class-progress/import", { method: "POST", body });

    const request = fetchMock.mock.calls[1][1];
    expect(request.body).toBe(body);
    expect(request.headers.has("Content-Type")).toBe(false);
    expect(request.headers.get("X-XSRF-TOKEN")).toBe("upload-token");
  });
});
