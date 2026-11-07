package cn.codedog.news;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class NewsEventMatchingTest {
 @Test void similarHeadlinesAreSuggestionsButDifferentModelsAreNotMatched(){
  assertThat(NewsEventService.similarity("Company Alpha announces satellite launch mission today","Company Alpha announces satellite launch mission tomorrow")).isGreaterThanOrEqualTo(.65);
  assertThat(NewsEventService.similarity("OpenAI launches GPT 5 artificial intelligence model for developers","OpenAI launches GPT 6 artificial intelligence model for developers")).isZero();
  assertThat(NewsEventService.similarity("Iran foreign minister announces peace negotiations","Iran football team wins international tournament")).isZero();
 }
}
