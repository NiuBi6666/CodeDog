package cn.codedog.service;

import cn.codedog.model.RankingRewardPayload;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/** Small, bounded cache of derived thumbnails. Originals remain untouched. */
public final class RewardImages {
  private static final int EDGE = 256;
  private static final int CACHE_SIZE = 96;
  private static final LinkedHashMap<String,RankingRewardPayload.RewardImage> CACHE =
      new LinkedHashMap<>(16,.75f,true);
  private RewardImages() {}

  public static ResponseEntity<byte[]> response(RankingRewardPayload.RewardImage original,
      boolean thumbnail,String ifNoneMatch) {
    var image=thumbnail?thumbnail(original):original;
    String etag="\""+digest(image.data())+"\"";
    boolean matched=ifNoneMatch!=null && java.util.Arrays.stream(ifNoneMatch.split(","))
        .map(String::trim).anyMatch(value->value.equals("*")||value.replaceFirst("^W/","").equals(etag));
    return ResponseEntity.status(matched?HttpStatus.NOT_MODIFIED:HttpStatus.OK)
        .cacheControl(CacheControl.noCache().cachePrivate()).eTag(etag).varyBy("Cookie")
        .contentType(MediaType.parseMediaType(image.contentType()))
        .body(matched?null:image.data());
  }

  public static synchronized RankingRewardPayload.RewardImage thumbnail(RankingRewardPayload.RewardImage original) {
    String key=original.contentType()+":"+digest(original.data());
    var cached=CACHE.get(key);
    if(cached!=null)return cached;
    // Preserve animated GIF and already-small files; unsupported formats use the original.
    if(original.data().length<24*1024||"image/gif".equals(original.contentType()))return original;
    try(var input=ImageIO.createImageInputStream(new ByteArrayInputStream(original.data()))) {
      var readers=ImageIO.getImageReaders(input);
      if(!readers.hasNext())return original;
      var reader=readers.next();
      BufferedImage source;
      try {
        reader.setInput(input,true,true);
        int width=reader.getWidth(0),height=reader.getHeight(0);
        if(width<=0||height<=0||(long)width*height>25_000_000)return original;
        int sample=Math.max(1,Math.min(width,height)/512);
        var parameters=reader.getDefaultReadParam();
        parameters.setSourceSubsampling(sample,sample,0,0);
        source=reader.read(0,parameters);
      } finally { reader.dispose(); }
      double scale=Math.min(1.0,EDGE/(double)Math.max(source.getWidth(),source.getHeight()));
      int width=Math.max(1,(int)Math.round(source.getWidth()*scale));
      int height=Math.max(1,(int)Math.round(source.getHeight()*scale));
      boolean alpha=source.getColorModel().hasAlpha();
      var target=new BufferedImage(width,height,alpha?BufferedImage.TYPE_INT_ARGB:BufferedImage.TYPE_INT_RGB);
      var graphics=target.createGraphics();
      try {
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        graphics.drawImage(source,0,0,width,height,null);
      } finally { graphics.dispose(); source.flush(); }
      var bytes=new ByteArrayOutputStream();
      if(alpha)ImageIO.write(target,"png",bytes);
      else {
        var writer=ImageIO.getImageWritersByFormatName("jpeg").next();
        try(var output=ImageIO.createImageOutputStream(bytes)) {
          writer.setOutput(output);
          var parameters=writer.getDefaultWriteParam();
          parameters.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
          parameters.setCompressionQuality(.80f);
          writer.write(null,new javax.imageio.IIOImage(target,null,null),parameters);
        } finally { writer.dispose(); }
      }
      target.flush();
      if(bytes.size()==0||bytes.size()>=original.data().length)return original;
      var result=new RankingRewardPayload.RewardImage(bytes.toByteArray(),alpha?"image/png":"image/jpeg");
      CACHE.put(key,result);
      while(CACHE.size()>CACHE_SIZE)CACHE.remove(CACHE.keySet().iterator().next());
      return result;
    } catch(java.io.IOException|RuntimeException ignored) {
      // Legacy/unsupported images must remain visible; never log their content.
      return original;
    }
  }

  private static String digest(byte[] bytes) {
    try {return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}
    catch(java.security.NoSuchAlgorithmException error){throw new IllegalStateException(error);}
  }
}
