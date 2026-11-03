package cn.codedog.news;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
// Internal fixed-address gateway; public requests cannot supply a fetch target.
@Service
public class NewsFastCollector {
 private final String endpoint;private final ObjectMapper json;
 private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
 public NewsFastCollector(@Value("${NEWS_FAST_COLLECTOR_URL:}")String endpoint,ObjectMapper json){this.endpoint=endpoint;this.json=json;}
 public boolean collect(long id,String secret)throws Exception{
  if(endpoint.isBlank())return false;
  long started=System.nanoTime();
  var request=HttpRequest.newBuilder(URI.create(endpoint+"/collect")).timeout(Duration.ofSeconds(180)).header("Content-Type","application/json")
   .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(Map.of("run_id",id,"secret",secret)))).build();
  HttpResponse<java.io.InputStream> response;
  try{response=http.send(request,HttpResponse.BodyHandlers.ofInputStream());}catch(java.net.ConnectException e){return false;}
  try(var stream=response.body()){
   byte[] body=stream.readNBytes(4097);
   if(response.statusCode()!=200||body.length>4096)throw new IllegalStateException("Fast collection result unconfirmed");
   var result=json.readTree(body);if(!result.path("acknowledged").asBoolean())throw new IllegalStateException("Fast collection result unconfirmed");
   System.out.println("news_fast_collect run_id="+id+" duration_ms="+((System.nanoTime()-started)/1000000)+" fallback="+result.path("fallback").asBoolean());
   return !result.path("fallback").asBoolean();
  }
 }
}
