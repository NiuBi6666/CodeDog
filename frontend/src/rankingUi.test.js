import{describe,expect,it}from"vitest";
import{avatarText,pointsToPass,trendView}from"../public/ranking-utils.js";

describe("ranking UI helpers",()=>{
  it("uses the final two name characters for avatars",()=>{
    expect(avatarText("欧阳修")).toBe("阳修");
    expect(avatarText("林")).toBe("林");
  });

  it("formats upward downward and unchanged trends",()=>{
    expect(trendView(2,5)).toMatchObject({className:"trend-up",label:"↑ 2"});
    expect(trendView(-1,3)).toMatchObject({className:"trend-down",label:"↓ 1"});
    expect(trendView(0,2)).toMatchObject({className:"trend-same",label:"-"});
    expect(trendView(0,null).title).toBe("暂无历史排名");
  });

  it("calculates the points needed to move past the previous student",()=>{
    const rows=[{totalPoints:320},{totalPoints:300},{totalPoints:300}];
    expect(pointsToPass(rows,1)).toBe(21);
    expect(pointsToPass(rows,2)).toBe(1);
    expect(pointsToPass(rows,0)).toBe(0);
  });
});
