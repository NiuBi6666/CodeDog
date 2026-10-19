package cn.codedog.service;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class HonorLevelsTest {
 @Test void allThirtyFiveStarBoundariesAreExact(){
  for(var tier:HonorLevels.TIERS)for(int star=1;star<=5;star++){
   int boundary=tier.minimum()+(star-1)*tier.starStep();var honor=HonorLevels.of(boundary);
   assertThat(honor.level()).isEqualTo(tier.level());assertThat(honor.stars()).isEqualTo(star);
   if(boundary>0){var before=HonorLevels.of(boundary-1);assertThat(before.level()*5+before.stars()).isLessThan(honor.level()*5+honor.stars());}
  }
 }
 @Test void negativeAndUpperLimitAreSafe(){assertThat(HonorLevels.of(-1).experience()).isZero();var max=HonorLevels.of(999999999);assertThat(max.level()).isEqualTo(7);assertThat(max.stars()).isEqualTo(5);assertThat(max.nextExperience()).isNull();assertThat(max.progress()).isEqualTo(100);}
 @Test void progressAndNextTierAreConsistent(){assertThat(HonorLevels.of(99).nextExperience()).isEqualTo(100);assertThat(HonorLevels.of(99).nextName()).isEqualTo("白银 1 星");assertThat(HonorLevels.of(110).progress()).isEqualTo(25);}
}
