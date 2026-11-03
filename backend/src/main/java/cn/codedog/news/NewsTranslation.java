package cn.codedog.news;

import com.fasterxml.jackson.databind.*;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@Service
public class NewsTranslation {
 private final JdbcTemplate db;private final NewsAiSettings ai;private final ObjectMapper json;private final String endpoint;
 private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
 public NewsTranslation(JdbcTemplate db,NewsAiSettings ai,ObjectMapper json,@Value("${NEWS_TRANSLATOR_URL:http://lens-translator:8090}")String endpoint){this.db=db;this.ai=ai;this.json=json;this.endpoint=endpoint;}
 public record Config(String mode,String engine){}
 public Config config(){var rows=db.queryForList("SELECT mode,engine FROM lens_translation_settings WHERE id=1");return rows.isEmpty()?new Config(ai.configured()?"api":"translators","bing"):new Config((String)rows.getFirst().get("mode"),(String)rows.getFirst().get("engine"));}
 public boolean configured(){return !config().mode().equals("api")||ai.configured();}
 public String label(){var c=config();return c.mode().equals("api")?ai.model():"translators / "+c.engine();}
 public Map<String,Object> view(){var result=new LinkedHashMap<>(ai.view());var c=config();result.put("mode",c.mode());result.put("engine",c.engine());result.put("api_configured",ai.configured());result.put("configured",configured());return result;}
 @org.springframework.transaction.annotation.Transactional
 public synchronized Map<String,Object> save(JsonNode body){
  // Legacy API clients without a mode continue to save model credentials.
  String mode=body.path("mode").asText("api"),engine=body.path("engine").asText(config().engine());
  if(!Set.of("api","translators").contains(mode)||!Set.of("bing","alibaba","baidu").contains(engine))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"翻译方式或引擎无效");
  if(mode.equals("api"))ai.save(body);
  db.update("INSERT INTO lens_translation_settings(id,mode,engine) VALUES(1,?,?) ON DUPLICATE KEY UPDATE mode=VALUES(mode),engine=VALUES(engine),updated_at=CURRENT_TIMESTAMP",mode,engine);
  return view();
 }
 public List<String> translate(Config config,List<String> paragraphs)throws Exception{
  var request=HttpRequest.newBuilder(URI.create(endpoint+"/translate")).timeout(Duration.ofSeconds(600)).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(Map.of("engine",config.engine(),"paragraphs",paragraphs)))).build();
  var response=http.send(request,HttpResponse.BodyHandlers.ofInputStream());
  try(var stream=response.body()){
   if(response.statusCode()!=200)throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,"默认翻译引擎暂不可用，可切换引擎或使用大模型 API");
   byte[] bytes=stream.readNBytes(2*1024*1024+1);if(bytes.length>2*1024*1024)throw new IllegalArgumentException("translation response too large");
   var result=json.readTree(bytes).path("paragraphs");if(!result.isArray()||result.size()!=paragraphs.size())throw new IllegalArgumentException("paragraph count");
   var texts=new ArrayList<String>();for(var p:result){if(!p.isTextual()||p.asText().isBlank())throw new IllegalArgumentException("empty translation");texts.add(p.asText().replaceAll("[\\r\\n]+"," ").strip());}return texts;
  }
 }
 public Map<String,Object> test(){var c=config();if(c.mode().equals("api"))return ai.test();try{translate(c,List.of("Hello, world."));return Map.of("success",true,"message","默认翻译连接成功（"+c.engine()+"）");}catch(Exception e){throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,"默认翻译连接失败，请切换引擎或稍后重试");}}
}
