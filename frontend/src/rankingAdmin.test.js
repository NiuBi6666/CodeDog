import { describe, expect, it } from "vitest";
import { rankingAvatarText, rankingShareUrl, rankingSummary, rankingTrendView } from "./rankingAdmin.js";

describe("ranking admin helpers", () => {
  it("builds a class share URL for the public student board", () => {
    expect(rankingShareUrl({
      origin: "https://codedog.online",
      teacherId: "CD-55E19DCA",
      campId: "172",
      classId: "2792",
      scope: "class"
    })).toBe("https://codedog.online/rankings.html?teacher=CD-55E19DCA&camp=172&class=2792&scope=class");
  });

  it("omits the class from a camp share URL", () => {
    expect(rankingShareUrl({
      origin: "https://codedog.online",
      teacherId: "CD-55E19DCA",
      campId: "172",
      classId: "2792",
      scope: "camp"
    })).toBe("https://codedog.online/rankings.html?teacher=CD-55E19DCA&camp=172&scope=camp");
  });

  it("formats avatars, trends and ranking totals", () => {
    expect(rankingAvatarText("欧阳修")).toBe("阳修");
    expect(rankingTrendView({ previousRank: 5, rankChange: 2 })).toMatchObject({ direction: "up", label: "↑ 2" });
    expect(rankingTrendView({ previousRank: 2, rankChange: -1 })).toMatchObject({ direction: "down", label: "↓ 1" });
    expect(rankingTrendView({ previousRank: null, rankChange: 0 }).title).toBe("暂无历史排名");
    expect(rankingSummary([{ totalPoints: 300 }, { totalPoints: 240 }])).toEqual({
      studentCount: 2,
      totalPoints: 540,
      averagePoints: 270
    });
  });
});
