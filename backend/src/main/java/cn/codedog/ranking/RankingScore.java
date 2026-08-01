package cn.codedog.ranking;
import java.util.Objects;
final class RankingScore {
  private RankingScore() {}
  static Score calculate(Double completionPercent, RankingPayload.Counts inclass, RankingPayload.Counts homework) {
    if (completionPercent == null || !Double.isFinite(completionPercent) || completionPercent < 0 || completionPercent > 100)
      throw new IllegalArgumentException("完课率必须在 0 到 100 之间");
    Counts a = validate(inclass, "课上作业"); Counts b = validate(homework, "课后作业");
    int completion = (int) Math.round(completionPercent), ap = correctness(a), bp = correctness(b);
    return new Score(completionPercent / 100.0, a, b, completion, ap, bp, completion + ap + bp);
  }
  private static Counts validate(RankingPayload.Counts value, String label) {
    RankingPayload.Counts source = Objects.requireNonNullElseGet(value, RankingPayload.Counts::empty);
    int total = Objects.requireNonNullElse(source.total(), 0), submitted = Objects.requireNonNullElse(source.submitted(), 0), passed = Objects.requireNonNullElse(source.passed(), 0);
    if (total < 0 || submitted < 0 || passed < 0 || passed > submitted || submitted > total)
      throw new IllegalArgumentException(label + "数量必须满足 0 <= 通过数 <= 提交数 <= 总数");
    return new Counts(total, submitted, passed);
  }
  private static int correctness(Counts c) { if (c.total() == 0) return 100; if (c.submitted() == 0) return 0; return (int) Math.round(c.passed() * 100.0 / c.submitted()); }
  record Counts(int total, int submitted, int passed) {}
  record Score(double completionRate, Counts inclass, Counts homework, int completionPoints, int inclassPoints, int homeworkPoints, int totalPoints) {}
}
