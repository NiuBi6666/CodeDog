package cn.codedog.news;

import com.fasterxml.jackson.databind.*;
import java.net.URI;
import java.io.*;
import java.nio.charset.StandardCharsets;
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
  if(paragraphs.isEmpty()||paragraphs.size()>401||paragraphs.stream().anyMatch(p->p==null||p.isBlank())||paragraphs.stream().mapToInt(String::length).sum()>101000)throw new IllegalArgumentException("invalid paragraphs");
  var texts=new ArrayList<String>(Collections.nCopies(paragraphs.size(),null));var missing=new ArrayList<String>();var indices=new ArrayList<Integer>();
  for(int i=0;i<paragraphs.size();i++){
   String key=NewsService.digest("translators:"+config.engine()+":zh:paragraph-v1:"+paragraphs.get(i));
   var cached=db.queryForList("SELECT translated_text FROM lens_translation_cache WHERE cache_key=?",key);
   if(cached.isEmpty()){missing.add(paragraphs.get(i));indices.add(i);}else texts.set(i,(String)cached.getFirst().get("translated_text"));
  }
  if(missing.isEmpty())return texts;
  var request=HttpRequest.newBuilder(URI.create(endpoint+"/translate")).timeout(Duration.ofSeconds(600)).header("Content-Type","application/json").header("Accept","application/x-ndjson").POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(Map.of("engine",config.engine(),"paragraphs",missing)))).build();
  var response=http.send(request,HttpResponse.BodyHandlers.ofInputStream());
  try(var stream=response.body()){
   if(response.statusCode()==200&&response.headers().firstValue("Content-Type").orElse("").startsWith("application/x-ndjson")){
    var seen=new HashSet<Integer>();boolean completed=false;int size=0;
    var reader=new BufferedReader(new InputStreamReader(stream,StandardCharsets.UTF_8));String line;
    while((line=reader.readLine())!=null){
     size+=line.length();if(size>2*1024*1024)throw new IllegalArgumentException("translation response too large");
     var item=json.readTree(line);
     if(item.path("complete").asBoolean()){completed=item.path("success").asBoolean();continue;}
     if(!item.path("index").isIntegralNumber())throw new IllegalArgumentException("invalid paragraph index");
     int i=item.path("index").asInt();if(i<0||i>=indices.size()||!seen.add(i))throw new IllegalArgumentException("paragraph alignment");
     cache(config,paragraphs,texts,indices.get(i),item.path("text"));
    }
    if(!completed||texts.stream().anyMatch(Objects::isNull))throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,"部分段落翻译失败，已保存完成的译文，稍后自动重试");
    return texts;
   }
   byte[] bytes=stream.readNBytes(2*1024*1024+1);if(bytes.length>2*1024*1024)throw new IllegalArgumentException("translation response too large");
   // A partial response may contain useful completed paragraphs even if the provider failed.
   var result=json.readTree(bytes).path("paragraphs");
   if(!result.isArray()||result.size()!=missing.size())throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,"默认翻译引擎暂不可用，可稍后重试");
   for(int i=0;i<result.size();i++){
    var p=result.get(i);if(!p.isTextual()||p.asText().isBlank())continue;
    cache(config,paragraphs,texts,indices.get(i),p);
   }
   if(response.statusCode()!=200||texts.stream().anyMatch(Objects::isNull))throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,"部分段落翻译失败，已保存完成的译文，稍后自动重试");
   return texts;
  }
 }
 private void cache(Config config,List<String> paragraphs,List<String> texts,int index,JsonNode p){
  if(!p.isTextual()||p.asText().isBlank())return;
  String value=p.asText().replaceAll("[\\r\\n]+"," ").strip();
  String key=NewsService.digest("translators:"+config.engine()+":zh:paragraph-v1:"+paragraphs.get(index));
  try{db.update("INSERT INTO lens_translation_cache(cache_key,engine,translated_text) VALUES(?,?,?)",key,config.engine(),value);}catch(org.springframework.dao.DuplicateKeyException ignored){}
  texts.set(index,value);
 }
 public Map<String,Object> test(){var c=config();if(c.mode().equals("api"))return ai.test();try{translate(c,List.of("Hello, world."));return Map.of("success",true,"message","默认翻译连接成功（"+c.engine()+"）");}catch(Exception e){throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,"默认翻译连接失败，请切换引擎或稍后重试");}}
}
