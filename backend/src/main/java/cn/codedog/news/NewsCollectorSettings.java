package cn.codedog.news;

import cn.codedog.service.PasswordRecoveryCipher;
import com.fasterxml.jackson.databind.*;
import java.net.URI;
import java.net.http.*;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

@Service
public class NewsCollectorSettings {
 private final JdbcTemplate db;private final PasswordRecoveryCipher cipher;private final ObjectMapper json;private final TransactionTemplate tx;
 private final String envToken,envRepo,envWorkflow,envSecret,githubApi,sealUrl;
 private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).followRedirects(HttpClient.Redirect.NEVER).build();
 private static final String TOKEN_CONTEXT="lens.collector.github.v1",SECRET_CONTEXT="lens.collector.webhook.v1";
 public NewsCollectorSettings(JdbcTemplate db,PasswordRecoveryCipher cipher,ObjectMapper json,PlatformTransactionManager manager,
  @Value("${NEWS_GITHUB_TOKEN:}")String token,@Value("${NEWS_GITHUB_REPO:}")String repo,@Value("${NEWS_GITHUB_WORKFLOW:lens-crawl.yml}")String workflow,@Value("${NEWS_WEBHOOK_SECRET:}")String secret,
  @Value("${NEWS_GITHUB_API_URL:https://api.github.com}")String githubApi,@Value("${NEWS_TRANSLATOR_URL:http://lens-translator:8090}")String sealUrl){this.db=db;this.cipher=cipher;this.json=json;tx=new TransactionTemplate(manager);envToken=token;envRepo=repo;envWorkflow=workflow;envSecret=secret;this.githubApi=githubApi;this.sealUrl=sealUrl;}
 public record RuntimeConfig(String repository,String workflow,String token,String secret,String apiUrl){@Override public String toString(){return "CollectorConfig[credentials redacted]";}}
 private Map<String,Object> row(){var rows=db.queryForList("SELECT * FROM lens_collector_settings WHERE id=1");return rows.isEmpty()?null:rows.getFirst();}
 public RuntimeConfig runtime(){var row=row();if(row==null)return new RuntimeConfig(envRepo,envWorkflow,envToken,envSecret,"https://codedog.online");return new RuntimeConfig((String)row.get("repository"),(String)row.get("workflow"),Objects.toString(cipher.decrypt((String)row.get("github_credential"),TOKEN_CONTEXT),""),Objects.toString(cipher.decrypt((String)row.get("webhook_credential"),SECRET_CONTEXT),""),(String)row.get("api_url"));}
 private static boolean complete(RuntimeConfig c){return !c.token().isBlank()&&c.repository().matches("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+")&&c.secret().length()>=32;}
 public boolean ready(){var row=row();return row==null?complete(runtime()):"ready".equals(row.get("setup_state"))&&cipher.configured();}
 public String secret(){return runtime().secret();}
 public Map<String,Object> view(){
  var row=row();var out=new LinkedHashMap<String,Object>();
  out.put("repository",row==null?(envRepo.isBlank()?"NiuBi6666/CodeDog":envRepo):row.get("repository"));out.put("workflow",row==null?envWorkflow:row.get("workflow"));out.put("api_url",row==null?"https://codedog.online":row.get("api_url"));
  out.put("credential_configured",row==null?!envToken.isBlank():!Objects.toString(row.get("github_credential"),"").isBlank());out.put("webhook_configured",row==null?envSecret.length()>=32:!Objects.toString(row.get("webhook_credential"),"").isBlank());out.put("encryption_available",cipher.configured());out.put("configured",ready());
  out.put("setup_state",row==null?(complete(runtime())?"environment":"unconfigured"):row.get("setup_state"));out.put("setup_step",row==null?"":row.get("setup_step"));out.put("last_error",row==null?null:row.get("last_error"));out.put("last_success_at",row==null?null:row.get("last_success_at"));return out;
 }
 public synchronized Map<String,Object> save(JsonNode body){
  var old=row();if(old!=null&&Set.of("queued","processing").contains(old.get("setup_state")))throw new ResponseStatusException(HttpStatus.CONFLICT,"正在自动配置，请等待任务完成");
  String repo=body.path("repository").asText().strip(),workflow=body.path("workflow").asText("lens-crawl.yml").strip(),apiUrl=NewsAiSettings.validateBase(body.path("api_url").asText("https://codedog.online")),token=body.path("github_token").asText("").strip();
  if(!repo.matches("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+")||repo.length()>200||!workflow.matches("[A-Za-z0-9_.-]+\\.ya?ml")||workflow.length()>100||token.length()>4096||token.chars().anyMatch(Character::isISOControl))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"仓库、工作流或 Token 格式无效");
  if(!cipher.configured())throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"服务器加密存储不可用");
  var previous=runtime();if(token.isBlank()&&!repo.equals(previous.repository())&&!previous.token().isBlank())throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"更换仓库时请重新填写对应 Token");
  if(token.isBlank())token=previous.token();if(token.isBlank())throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"首次配置请填写 GitHub Token");
  String secret=previous.secret();if(secret.length()<32||!repo.equals(previous.repository())){byte[] bytes=new byte[32];new SecureRandom().nextBytes(bytes);secret=HexFormat.of().formatHex(bytes);}
  db.update("INSERT INTO lens_collector_settings(id,repository,workflow,api_url,github_credential,webhook_credential) VALUES(1,?,?,?,?,?) ON DUPLICATE KEY UPDATE repository=VALUES(repository),workflow=VALUES(workflow),api_url=VALUES(api_url),github_credential=VALUES(github_credential),webhook_credential=VALUES(webhook_credential),setup_state='saved',setup_step=NULL,last_error=NULL,updated_at=CURRENT_TIMESTAMP",repo,workflow,apiUrl,cipher.encrypt(token,TOKEN_CONTEXT),cipher.encrypt(secret,SECRET_CONTEXT));return view();
 }
 public synchronized Map<String,Object> queue(String actor,String ip,String requestId){
  if(row()==null||!complete(runtime()))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"请先保存采集配置");
  db.update("UPDATE lens_collector_settings SET setup_state='queued',setup_step='等待自动配置',last_error=NULL,requested_by=?,request_ip=?,request_id=?,lease_until=NULL WHERE id=1 AND setup_state NOT IN ('queued','processing')",actor,ip.length()>100?ip.substring(0,100):ip,requestId);return view();
 }
 private record ApiResult(int status,JsonNode body){}
 private ApiResult github(RuntimeConfig config,String method,String path,Object body)throws Exception{
  var builder=HttpRequest.newBuilder(URI.create(githubApi+path)).timeout(Duration.ofSeconds(12)).header("Accept","application/vnd.github+json").header("X-GitHub-Api-Version","2022-11-28").header("Authorization","Bearer "+config.token()).header("Content-Type","application/json");
  builder.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
  var response=http.send(builder.build(),HttpResponse.BodyHandlers.ofInputStream());try(var stream=response.body()){byte[] bytes=stream.readNBytes(1024*1024+1);if(bytes.length>1024*1024)throw new IllegalArgumentException("response size");return new ApiResult(response.statusCode(),bytes.length==0?json.createObjectNode():json.readTree(bytes));}
 }
 private static void accepted(ApiResult result,int...codes){for(int code:codes)if(result.status()==code)return;String reason=switch(result.status()){case 401->"GitHub Token 无效或已过期";case 403->"GitHub 权限不足或请求受限；请检查 Actions、Secrets、Variables 的 Read and write 权限";case 404->"GitHub 仓库、工作流不存在或 Token 无权访问；请检查所选仓库和 Secrets、Variables 权限";default->"GitHub 配置请求失败（HTTP "+result.status()+"）";};throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,reason);}
 private void step(String text){db.update("UPDATE lens_collector_settings SET setup_step=? WHERE id=1",text);}
 private void variable(RuntimeConfig c,String root,String name,String value)throws Exception{var result=github(c,"PATCH",root+"/actions/variables/"+name,Map.of("name",name,"value",value));if(result.status()==404)result=github(c,"POST",root+"/actions/variables",Map.of("name",name,"value",value));accepted(result,201,204);}
 private String seal(String publicKey,String secret)throws Exception{
  var req=HttpRequest.newBuilder(URI.create(sealUrl+"/seal")).timeout(Duration.ofSeconds(10)).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(Map.of("public_key",publicKey,"secret",secret)))).build();
  var response=http.send(req,HttpResponse.BodyHandlers.ofString());if(response.statusCode()!=200)throw new IllegalArgumentException("sealed box unavailable");String value=json.readTree(response.body()).path("encrypted_value").asText();if(value.isBlank()||value.length()>2000)throw new IllegalArgumentException("sealed box response");return value;
 }
 public void dispatch()throws Exception{dispatch(runtime());}
 private void dispatch(RuntimeConfig c)throws Exception{accepted(github(c,"POST","/repos/"+c.repository()+"/actions/workflows/"+c.workflow()+"/dispatches",Map.of("ref","main","inputs",Map.of("force_all","true"))),204);}
 @Scheduled(fixedDelay=5000,initialDelay=20000)
 public synchronized void processProvision(){
  var row=row();if(row==null)return;String state=(String)row.get("setup_state");Timestamp lease=(Timestamp)row.get("lease_until");if(!state.equals("queued")&&!(state.equals("processing")&&lease!=null&&lease.toInstant().isBefore(Instant.now())))return;
  if(db.update("UPDATE lens_collector_settings SET setup_state='processing',lease_until=? WHERE id=1 AND (setup_state='queued' OR (setup_state='processing' AND lease_until<CURRENT_TIMESTAMP))",Timestamp.from(Instant.now().plusSeconds(300)))!=1)return;
  boolean success=false;String message="自动配置失败，请检查网络和加密服务后重试";
  try{
   var c=runtime();String root="/repos/"+c.repository();
   step("检查仓库与采集工作流");var workflow=github(c,"GET",root+"/actions/workflows/"+c.workflow(),null);accepted(workflow,200);
   step("加密并同步 Webhook 采集密钥");var key=github(c,"GET",root+"/actions/secrets/public-key",null);accepted(key,200);
   accepted(github(c,"PUT",root+"/actions/secrets/NEWS_WEBHOOK_SECRET",Map.of("key_id",key.body().path("key_id").asText(),"encrypted_value",seal(key.body().path("key").asText(),c.secret()))),201,204);
   step("设置采集地址");variable(c,root,"NEWS_API_URL",c.apiUrl());
   step("启用采集工作流");accepted(github(c,"PUT",root+"/actions/workflows/"+c.workflow()+"/enable",null),204);
   variable(c,root,"NEWS_ENABLED","true");
   step("触发首次采集");dispatch(c);
   db.update("INSERT INTO lens_refresh_runs(status) VALUES('dispatched')");success=true;message="自动配置成功，已触发首次采集；等待 GitHub 抓取和正文翻译";
  }catch(ResponseStatusException e){message=e.getReason();}catch(Exception ignored){}
  final boolean ok=success;final String result=message;final String completedStep=ok?result:Objects.toString(view().get("setup_step"),"自动配置")+"（失败）";
  tx.executeWithoutResult(status->{
   db.update("UPDATE lens_collector_settings SET setup_state=?,setup_step=?,last_error=?,lease_until=NULL,last_success_at="+(ok?"CURRENT_TIMESTAMP":"last_success_at")+" WHERE id=1",ok?"ready":"failed",completedStep,ok?null:result);
   db.update("INSERT INTO audit_log(action,owner_username,actor_type,actor_id,actor_name,request_id,http_method,request_path,status_code,duration_ms,module,event_type,event_code,result,target_type,target_id,detail_json,error_message,ip_address,created_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)","collector_setup_completed",row.get("requested_by"),"SYSTEM",row.get("requested_by"),"新闻采集自动配置",UUID.randomUUID().toString(),"SYSTEM","scheduled://news-collector-setup",ok?200:502,0,"news","management","collector_setup_completed",ok?"success":"failed","NEWS_COLLECTOR_SETTINGS","1",safeJson(Map.of("requested_by",Objects.toString(row.get("requested_by"),""),"repository",row.get("repository"),"origin_request_id",Objects.toString(row.get("request_id"),""),"step",Objects.toString(view().get("setup_step"),""))),ok?null:result,row.get("request_ip"),Timestamp.from(Instant.now()));
  });
 }
 private String safeJson(Object value){try{return json.writeValueAsString(value);}catch(Exception e){return "{}";}}
}
