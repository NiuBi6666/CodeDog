import{describe,expect,it}from"vitest";
import{rankingAvatarText,rankingPointsToPass,rankingTrendView}from"./rankingAdmin.js";

describe("ranking UI helpers",()=>{
  it("uses the final two name characters for avatars",()=>{
    expect(rankingAvatarText("欧阳修")).toBe("阳修");
    expect(rankingAvatarText("林")).toBe("林");
  });

  it("formats upward downward and unchanged trends",()=>{
    expect(rankingTrendView({rankChange:2,previousRank:5})).toMatchObject({direction:"up",label:"↑ 2"});
    expect(rankingTrendView({rankChange:-1,previousRank:3})).toMatchObject({direction:"down",label:"↓ 1"});
    expect(rankingTrendView({rankChange:0,previousRank:2})).toMatchObject({direction:"same",label:"-"});
    expect(rankingTrendView({rankChange:0,previousRank:null}).title).toBe("暂无历史排名");
  });

  it("calculates the points needed to move past the previous student",()=>{
    const rows=[{totalPoints:320},{totalPoints:300},{totalPoints:300}];
    expect(rankingPointsToPass(rows,1)).toBe(21);
    expect(rankingPointsToPass(rows,2)).toBe(21);
    expect(rankingPointsToPass(rows,0)).toBe(0);
    expect(rankingPointsToPass([{totalPoints:300},{totalPoints:300}],1)).toBe(0);
  });
});
