package cn.codedog.ranking;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class RankingScoreTest {
  @Test void calculatesThreeHundredPointLesson() {
    var score=RankingScore.calculate(100.0,new RankingPayload.Counts(5,5,5),new RankingPayload.Counts(4,4,4));
    assertThat(score.totalPoints()).isEqualTo(300);
  }
  @Test void unassignedHomeworkGetsFullCredit() {
    var score=RankingScore.calculate(80.0,new RankingPayload.Counts(0,0,0),new RankingPayload.Counts(0,0,0));
    assertThat(score.completionPoints()).isEqualTo(80);
    assertThat(score.inclassPoints()).isEqualTo(100);
    assertThat(score.homeworkPoints()).isEqualTo(100);
  }
  @Test void assignedButUnsubmittedHomeworkGetsZero() {
    var score=RankingScore.calculate(50.0,new RankingPayload.Counts(4,0,0),new RankingPayload.Counts(8,4,3));
    assertThat(score.inclassPoints()).isZero();
    assertThat(score.homeworkPoints()).isEqualTo(75);
    assertThat(score.totalPoints()).isEqualTo(125);
  }
  @Test void rejectsImpossibleCountsAndRates() {
    assertThatThrownBy(()->RankingScore.calculate(101.0,null,null)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(()->RankingScore.calculate(100.0,new RankingPayload.Counts(2,1,2),null)).isInstanceOf(IllegalArgumentException.class);
  }
}
