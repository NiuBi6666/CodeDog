package cn.codedog.service;
import java.util.List;

/** Honor uses normal course earnings; spending and balance adjustments do not alter experience. */
public final class HonorLevels {
  private HonorLevels() {}
  public record Tier(int level,String name,int minimum,int starStep,String privilege) {}
  public record Honor(int level,String name,int stars,int experience,Integer nextExperience,String nextName,int progress) {}
  public static final List<Tier> TIERS=List.of(
    new Tier(1,"青铜",0,20,"基础光环 · 代码新星"),
    new Tier(2,"白银",100,40,"电光轨道头像框"),
    new Tier(3,"黄金",300,60,"解题先锋称号 · 极光终端背景"),
    new Tier(4,"铂金",600,80,"棱彩星环头像框"),
    new Tier(5,"钻石",1000,100,"算法探索者称号 · 星际轨道背景 · 流光昵称"),
    new Tier(6,"星耀",1500,120,"星耀星芒头像框"),
    new Tier(7,"王者",2100,100,"王者冠冕头像框 · 巅峰王者称号"));
  public static Honor of(int value) {
    int xp=Math.max(0,value),index=0;
    while(index<6 && xp>=TIERS.get(index+1).minimum()) index++;
    Tier tier=TIERS.get(index);
    int stars=Math.min(5,1+(xp-tier.minimum())/tier.starStep());
    Integer next=null;
    if(stars<5) next=tier.minimum()+stars*tier.starStep();
    else if(index<6) next=TIERS.get(index+1).minimum();
    String nextName=next==null?"最高荣耀":stars<5?tier.name()+" "+(stars+1)+" 星":TIERS.get(index+1).name()+" 1 星";
    int base=tier.minimum()+(stars-1)*tier.starStep();
    int progress=next==null?100:(int)Math.min(100,((long)(xp-base)*100)/(next-base));
    return new Honor(index+1,tier.name(),stars,xp,next,nextName,progress);
  }
}
