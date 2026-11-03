
package cn.codedog.news;

import cn.codedog.service.PasswordRecoveryCipher;
import com.fasterxml.jackson.databind.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class NewsAiSettings {
 private static final String CONTEXT="news.ai.api-key.v1";
 private final JdbcTemplate db;private final PasswordRecoveryCipher cipher;private final ObjectMapper json;
 private final String envBase,envModel,envKey;
 private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NEVER).build();
 public NewsAiSettings(JdbcTemplate db,PasswordRecoveryCipher cipher,ObjectMapper json,
  @Value("${NEWS_AI_BASE_URL:}")String base,@Value("${NEWS_AI_MODEL:}")String model,@Value("${NEWS_AI_API_KEY:}")String key){this.db=db;this.cipher=cipher;this.json=json;envBase=base;envModel=model;envKey=key;}
 public record RuntimeConfig(String baseUrl,String model,String apiKey){@Override public String toString(){return "NewsAiConfig[credentials redacted]";}}
 private Map<String,Object> row(){var rows=db.queryForList("SELECT base_url,model,api_key_ciphertext,updated_at FROM news_ai_settings WHERE id=1");return rows.isEmpty()?null:rows.getFirst();}
 public Map<String,Object> view(){
  var row=row();boolean stored=row!=null;String base=stored?(String)row.get("base_url"):envBase,model=stored?(String)row.get("model"):envModel;
  boolean present=stored?!((String)row.get("api_key_ciphertext")).isBlank():!envKey.isBlank();
  var result=new LinkedHashMap<String,Object>();result.put("base_url",base);result.put("model",model);result.put("api_key_configured",present);result.put("api_key_mask",present?"••••••••":"");result.put("source",stored?"database":"environment");result.put("encryption_available",cipher.configured());result.put("configured",present&&!base.isBlank()&&!model.isBlank()&&(!stored||cipher.configured()));result.put("updated_at",stored?row.get("updated_at"):null);return result;
 }
 public boolean configured(){return Boolean.TRUE.equals(view().get("configured"));}
 public String model(){return Objects.toString(view().get("model"),"");}
 public RuntimeConfig runtime(){
  var row=row();if(row==null)return new RuntimeConfig(envBase,envModel,envKey);
  String plaintext=cipher.decrypt((String)row.get("api_key_ciphertext"),CONTEXT);
  return new RuntimeConfig((String)row.get("base_url"),(String)row.get("model"),plaintext==null?"":plaintext);
 }
 public static String validateBase(String value){
  String base=value==null?"":value.strip().replaceAll("/+$","");
  try{URI u=URI.create(base);String host=u.getHost();
   if(base.length()>500||!"https".equals(u.getScheme())||host==null||u.getUserInfo()!=null||u.getRawQuery()!=null||u.getRawFragment()!=null||(u.getPort()!=-1&&u.getPort()!=443))throw new IllegalArgumentException();
   host=host.toLowerCase(Locale.ROOT);
   if(!host.contains(".")||host.endsWith(".local")||host.endsWith(".internal")||host.equals("localhost")||host.matches("[0-9.]+")||host.contains(":"))throw new IllegalArgumentException();
   if(u.getPath().endsWith("/chat/completions"))throw new IllegalArgumentException();
   return base;
  }catch(Exception e){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"请填写公共 HTTPS 接口根地址，不含密钥、参数或 /chat/completions");}
 }
 private static void publicHost(String base){
  try{for(InetAddress a:InetAddress.getAllByName(URI.create(base).getHost())){
   byte[] bytes=a.getAddress();int first=Byte.toUnsignedInt(bytes[0]);
   if(a.isAnyLocalAddress()||a.isLoopbackAddress()||a.isLinkLocalAddress()||a.isSiteLocalAddress()||a.isMulticastAddress()||(bytes.length==16&&(first&0xfe)==0xfc)||(bytes.length==4&&first==100&&(Byte.toUnsignedInt(bytes[1])&0xc0)==64))throw new IllegalArgumentException();
  }}catch(Exception e){throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,"模型接口域名不可用或指向受保护的网络地址");}
 }
 public synchronized Map<String,Object> save(JsonNode body){
  String base=validateBase(body.path("base_url").asText()),model=body.path("model").asText().strip(),key=body.path("api_key").asText("").strip();
  if(model.isBlank()||model.length()>200||model.chars().anyMatch(Character::isISOControl)||key.length()>4096||key.chars().anyMatch(Character::isISOControl))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"模型名称或 API Key 格式无效");
  var old=row();String oldBase=old==null?envBase:(String)old.get("base_url");boolean hasKey=old==null?!envKey.isBlank():!((String)old.get("api_key_ciphertext")).isBlank();
  if(key.isBlank()&&hasKey&&!base.equals(oldBase))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"更换服务地址时请重新输入对应服务商的 API Key");
  if(key.isBlank()&&!hasKey)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"首次配置请填写 API Key");
  if(!cipher.configured())throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"服务器尚未配置加密存储，暂不能保存 API Key");
  String encrypted=key.isBlank()?(old==null?cipher.encrypt(envKey,CONTEXT):(String)old.get("api_key_ciphertext")):cipher.encrypt(key,CONTEXT);
  db.update("INSERT INTO news_ai_settings(id,base_url,model,api_key_ciphertext) VALUES(1,?,?,?) ON DUPLICATE KEY UPDATE base_url=VALUES(base_url),model=VALUES(model),api_key_ciphertext=VALUES(api_key_ciphertext),updated_at=CURRENT_TIMESTAMP",base,model,encrypted);
  return view();
 }
 public String complete(RuntimeConfig config,List<Map<String,String>> messages,Integer maxTokens)throws Exception{
  validateBase(config.baseUrl());publicHost(config.baseUrl());
  if(config.apiKey().isBlank()||config.model().isBlank())throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"请先保存大模型配置");
  var payload=new LinkedHashMap<String,Object>();payload.put("model",config.model());payload.put("temperature",0.1);payload.put("messages",messages);if(maxTokens!=null)payload.put("max_tokens",maxTokens);
  var request=HttpRequest.newBuilder(URI.create(config.baseUrl().replaceAll("/+$","")+"/chat/completions")).timeout(Duration.ofSeconds(maxTokens==null?90:20)).header("Authorization","Bearer "+config.apiKey()).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(payload),StandardCharsets.UTF_8)).build();
  var response=http.send(request,HttpResponse.BodyHandlers.ofInputStream());
  try(var stream=response.body()){
   if(response.statusCode()!=200){String message=switch(response.statusCode()){case 401,403->"API Key 无效或没有模型访问权限";case 404->"接口地址或模型名称不正确";case 429->"模型额度不足或请求过于频繁";default->"模型服务暂时不可用（HTTP "+response.statusCode()+"）";};throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,message);}
   byte[] bytes=stream.readNBytes(2*1024*1024+1);if(bytes.length>2*1024*1024)throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,"模型返回内容超限");
   return new String(bytes,StandardCharsets.UTF_8);
  }
 }
 public Map<String,Object> test(){
  if(!configured())throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"请先保存完整的大模型配置");
  try{JsonNode response=json.readTree(complete(runtime(),List.of(Map.of("role","user","content","Reply with OK.")),8));
   if(response.path("choices").path(0).path("message").path("content").asText().isBlank())throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,"模型未返回可用内容，请核对模型及兼容接口");
   return Map.of("success",true,"message","连接成功，已收到模型响应");
  }catch(ResponseStatusException e){throw e;}catch(Exception e){throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,"连接失败或超时，请检查服务地址与模型配置");}
 }
}
