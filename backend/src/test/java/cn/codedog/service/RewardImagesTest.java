package cn.codedog.service;

import cn.codedog.model.RankingRewardPayload;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Random;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class RewardImagesTest {
  private RankingRewardPayload.RewardImage largeImage(boolean alpha) throws Exception {
    var image=new BufferedImage(1000,800,alpha?BufferedImage.TYPE_INT_ARGB:BufferedImage.TYPE_INT_RGB);
    var random=new Random(12);
    for(int y=0;y<800;y++)for(int x=0;x<1000;x++)
      image.setRGB(x,y,(alpha?0x80000000:0xff000000)|random.nextInt(0x1000000));
    var out=new ByteArrayOutputStream();ImageIO.write(image,"png",out);
    return new RankingRewardPayload.RewardImage(out.toByteArray(),"image/png");
  }
  @Test void largeOpaqueImageBecomesSmallThumbnailWithoutChangingOriginal() throws Exception {
    var original=largeImage(false);
    byte[] before=original.data().clone();
    var small=RewardImages.thumbnail(original);
    var image=ImageIO.read(new ByteArrayInputStream(small.data()));
    assertThat(image.getWidth()).isEqualTo(256);assertThat(image.getHeight()).isEqualTo(205);
    assertThat(small.data().length).isLessThan(original.data().length/10);
    assertThat(small.contentType()).isEqualTo("image/jpeg");
    assertThat(original.data()).isEqualTo(before);
    assertThat(RewardImages.thumbnail(original)).isSameAs(small);
    System.out.println("Thumbnail bytes: "+original.data().length+" -> "+small.data().length);
  }
  @Test void transparentImagesKeepAlphaAndUnknownFormatsHaveSafeFallback() throws Exception {
    var original=largeImage(true);var small=RewardImages.thumbnail(original);
    assertThat(small.contentType()).isEqualTo("image/png");
    assertThat(ImageIO.read(new ByteArrayInputStream(small.data())).getColorModel().hasAlpha()).isTrue();
    var webp=new RankingRewardPayload.RewardImage(new byte[40000],"image/webp");
    assertThat(RewardImages.thumbnail(webp)).isSameAs(webp);
    var gif=new RankingRewardPayload.RewardImage(new byte[40000],"image/gif");
    assertThat(RewardImages.thumbnail(gif)).isSameAs(gif);
  }
  @Test void etagsProducePrivateConditionalResponsesAndInvalidateWhenContentChanges() {
    var image=new RankingRewardPayload.RewardImage(new byte[]{1,2,3},"image/png");
    var first=RewardImages.response(image,true,null);
    assertThat(first.getStatusCode().value()).isEqualTo(200);
    assertThat(first.getHeaders().getCacheControl()).contains("private","no-cache");
    assertThat(first.getHeaders().getVary()).contains("Cookie");
    var second=RewardImages.response(image,true,first.getHeaders().getETag());
    assertThat(second.getStatusCode().value()).isEqualTo(304);assertThat(second.getBody()).isNull();
    assertThat(RewardImages.response(image,true,"W/"+first.getHeaders().getETag()).getStatusCode().value()).isEqualTo(304);
    var changed=new RankingRewardPayload.RewardImage(new byte[]{4,5,6},"image/png");
    assertThat(RewardImages.response(changed,true,first.getHeaders().getETag()).getStatusCode().value()).isEqualTo(200);
    assertThat(RewardImages.response(image,false,null).getBody()).containsExactly(1,2,3);
  }
}
