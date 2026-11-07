package cn.codedog.news;
import com.fasterxml.jackson.databind.JsonNode;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@Service
public class NewsEventService {
 private final JdbcTemplate db;
 private static final String PUBLIC="(a.status='ready' OR (a.title_zh IS NOT NULL AND a.title_zh<>''))";
 private static final Set<String> CATEGORIES=Set.of("国际","军事","科技","财经","教育","文娱","生活");
 public NewsEventService(JdbcTemplate db){this.db=db;}
 private static void check(boolean ok,String message){if(!ok)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,message);}
 private static String value(JsonNode body,String key,int max){String s=body.path(key).asText("").strip();check(!s.isBlank()&&s.length()<=max,"请填写有效的"+key);return s;}
 public Map<String,Object> event(long id,boolean admin){
  var rows=db.queryForList("SELECT * FROM news_events WHERE id=?"+(admin?"":" AND status='published'"),id);
  if(rows.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"事件不存在");
  var e=rows.getFirst();
  var nodes=db.queryForList("SELECT n.* FROM news_event_nodes n WHERE n.event_id=? AND EXISTS(SELECT 1 FROM news_event_articles l JOIN news_articles a ON a.id=l.article_id WHERE l.node_id=n.id AND l.event_id=n.event_id AND l.state='confirmed' AND "+PUBLIC+") ORDER BY n.occurred_at,n.id",id);
  var links=db.queryForList("SELECT l.article_id,l.node_id,l.state,l.confidence,l.method,a.title_en,a.title_zh,a.source_name,a.origin_url,a.published_at,a.lang FROM news_event_articles l JOIN news_articles a ON a.id=l.article_id WHERE l.event_id=? AND "+PUBLIC+(admin?" AND l.state<>'excluded'":" AND l.state='confirmed'")+" ORDER BY a.published_at,a.id",id);
  for(var n:nodes)n.put("reports",links.stream().filter(l->Objects.equals(l.get("node_id"),n.get("id"))&&"confirmed".equals(l.get("state"))).toList());
  e.put("nodes",nodes);e.put("suggestions",admin?links.stream().filter(l->"suggested".equals(l.get("state"))).toList():List.of());
  e.put("article_count",links.stream().filter(l->"confirmed".equals(l.get("state"))).count());return e;
 }
 public List<Map<String,Object>> list(boolean admin){
  return db.queryForList("SELECT e.*, (SELECT COUNT(*) FROM news_event_articles l JOIN news_articles a ON a.id=l.article_id WHERE l.event_id=e.id AND l.state='confirmed' AND "+PUBLIC+") AS article_count, (SELECT MIN(n.occurred_at) FROM news_event_nodes n WHERE n.event_id=e.id AND EXISTS(SELECT 1 FROM news_event_articles l WHERE l.node_id=n.id AND l.state='confirmed')) AS first_at, (SELECT MAX(n.occurred_at) FROM news_event_nodes n WHERE n.event_id=e.id AND EXISTS(SELECT 1 FROM news_event_articles l WHERE l.node_id=n.id AND l.state='confirmed')) AS last_at"+(admin?",(SELECT COUNT(*) FROM news_event_articles l WHERE l.event_id=e.id AND l.state='suggested') AS suggested_count":"")+" FROM news_events e"+(admin?"":" WHERE e.status='published'")+" ORDER BY e.updated_at DESC,e.id DESC LIMIT 100");
 }
 public List<Map<String,Object>> forArticles(List<Long> ids){
  if(ids.isEmpty())return List.of();
  return db.queryForList("SELECT l.article_id,e.id,e.title,(SELECT COUNT(*) FROM news_event_articles c JOIN news_articles a ON a.id=c.article_id WHERE c.event_id=e.id AND c.state='confirmed' AND "+PUBLIC+") AS article_count FROM news_event_articles l JOIN news_events e ON e.id=l.event_id WHERE e.status='published' AND l.state='confirmed' AND l.article_id IN ("+String.join(",",Collections.nCopies(ids.size(),"?"))+") ORDER BY e.updated_at DESC",ids.toArray());
 }
 public Object annotate(Object result){
  if(result instanceof Map<?,?> raw){
   @SuppressWarnings("unchecked") var map=(Map<String,Object>)raw;
   if(map.get("articles") instanceof List<?> rows){
    var links=forArticles(rows.stream().map(r->((Number)((Map<?,?>)r).get("id")).longValue()).toList());
    for(Object row:rows){@SuppressWarnings("unchecked") var a=(Map<String,Object>)row;a.put("events",links.stream().filter(l->Objects.equals(l.get("article_id"),a.get("id"))).toList());}
   }else if(map.get("id") instanceof Number n)map.put("events",forArticles(List.of(n.longValue())));
  }return result;
 }
 @Transactional public Map<String,Object> create(JsonNode b){
  String title=value(b,"title",300),category=value(b,"category",30);check(CATEGORIES.contains(category),"分类无效");
  var keys=new org.springframework.jdbc.support.GeneratedKeyHolder();
  db.update(c->{var s=c.prepareStatement("INSERT INTO news_events(title,category) VALUES(?,?)",new String[]{"id"});s.setString(1,title);s.setString(2,category);return s;},keys);
  return event(keys.getKey().longValue(),true);
 }
 @Transactional public Map<String,Object> edit(long id,JsonNode b){
  event(id,true);String title=value(b,"title",300),category=value(b,"category",30);check(CATEGORIES.contains(category),"分类无效");
  db.update("UPDATE news_events SET title=?,category=?,updated_at=CURRENT_TIMESTAMP WHERE id=?",title,category,id);return event(id,true);
 }
 @Transactional public Map<String,Object> publish(long id,JsonNode b){
  event(id,true);String status=value(b,"status",20);check(Set.of("draft","published").contains(status),"状态无效");
  if(status.equals("published"))check(((Number)event(id,true).get("article_count")).longValue()>=2,"至少确认两篇报道后发布");
  db.update("UPDATE news_events SET status=?,updated_at=CURRENT_TIMESTAMP WHERE id=?",status,id);return event(id,true);
 }
 public List<Map<String,Object>> search(String q){
  check(q.strip().length()>=2&&q.length()<=100,"请输入至少两个字符");
  return db.queryForList("SELECT a.id,a.title_en,a.title_zh,a.source_name,a.published_at FROM news_articles a WHERE "+PUBLIC+" AND (a.title_en LIKE ? OR a.title_zh LIKE ?) ORDER BY a.published_at DESC,a.id DESC LIMIT 30","%"+q+"%","%"+q+"%");
 }
 @Transactional public Map<String,Object> link(long id,long articleId,JsonNode b){
  event(id,true);
  var rows=db.queryForList("SELECT a.* FROM news_articles a WHERE a.id=? AND "+PUBLIC,articleId);check(!rows.isEmpty(),"报道不存在或尚未发布");
  var a=rows.getFirst();String state=b.path("state").asText("confirmed");check(Set.of("confirmed","excluded").contains(state),"关联状态无效");
  var old=db.queryForList("SELECT * FROM news_event_articles WHERE event_id=? AND article_id=?",id,articleId);
  Long node=null;
  if(state.equals("confirmed")){
   if(b.path("node_id").asLong()>0){
    node=b.path("node_id").asLong();check(db.queryForObject("SELECT COUNT(*) FROM news_event_nodes WHERE id=? AND event_id=?",Long.class,node,id)==1,"时间轴节点不属于此事件");
   }else{
    String stage=b.path("stage").asText("development");check(Set.of("background","development","response","outcome").contains(stage),"阶段无效");
    Timestamp when=(Timestamp)a.get("published_at");String basis="published";
    if(!b.path("occurred_at").asText("").isBlank()){try{when=Timestamp.from(Instant.parse(b.path("occurred_at").asText()));basis="occurred";}catch(Exception ex){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"发生时间无效");}}
    String title=Objects.toString(a.get("title_zh"),"");if(title.isBlank())title=(String)a.get("title_en");
    var keys=new org.springframework.jdbc.support.GeneratedKeyHolder();Timestamp at=when;String timeBasis=basis,headline=title;
    db.update(c->{var s=c.prepareStatement("INSERT INTO news_event_nodes(event_id,title,occurred_at,time_basis,stage) VALUES(?,?,?,?,?)",new String[]{"id"});s.setLong(1,id);s.setString(2,headline);s.setTimestamp(3,at);s.setString(4,timeBasis);s.setString(5,stage);return s;},keys);node=keys.getKey().longValue();
   }
  }
  if(old.isEmpty())db.update("INSERT INTO news_event_articles(event_id,article_id,node_id,state,confidence,method) VALUES(?,?,?,?,1,'manual')",id,articleId,node,state);
  else db.update("UPDATE news_event_articles SET node_id=?,state=?,confidence=1,method='manual' WHERE event_id=? AND article_id=?",node,state,id,articleId);
  if(state.equals("excluded")&&db.queryForObject("SELECT COUNT(*) FROM news_event_articles WHERE event_id=? AND state='confirmed'",Long.class,id)<2)db.update("UPDATE news_events SET status='draft' WHERE id=?",id);
  db.update("UPDATE news_events SET updated_at=CURRENT_TIMESTAMP WHERE id=?",id);return event(id,true);
 }
 static Set<String> words(String text){
  var set=new HashSet<String>();for(String w:text.toLowerCase(Locale.ROOT).split("[^a-z0-9]+"))if((w.length()>2||w.matches("[0-9]+"))&&!Set.of("the","and","for","with","from","that","this","are","has","after","new","says","news","over","its","will","have","been","into").contains(w))set.add(w);return set;
 }
 static double similarity(String first,String second){
  var a=words(first);var b=words(second);var both=new HashSet<>(a);both.retainAll(b);var all=new HashSet<>(a);all.addAll(b);
  if(both.size()<5||all.isEmpty())return 0;
  var numsA=new HashSet<>(a.stream().filter(w->w.matches(".*[0-9].*")).toList());var numsB=new HashSet<>(b.stream().filter(w->w.matches(".*[0-9].*")).toList());
  if(!numsA.equals(numsB))return 0;return (double)both.size()/all.size();
 }
 @Scheduled(fixedDelay=15000,initialDelay=60000)
 @Transactional public void propose(){
  var cursor=db.queryForList("SELECT last_article_id FROM news_event_scan WHERE id=1 FOR UPDATE");if(cursor.isEmpty())return;
  long last=((Number)cursor.getFirst().get("last_article_id")).longValue();
  var fresh=db.queryForList("SELECT a.id,a.title_en,a.category,a.published_at FROM news_articles a WHERE NOT EXISTS(SELECT 1 FROM news_event_scanned_articles s WHERE s.article_id=a.id) AND "+PUBLIC+" ORDER BY a.id LIMIT 30");
  long beforeLinks=db.queryForObject("SELECT COUNT(*) FROM news_event_articles",Long.class);
  for(var a:fresh){
   long aid=((Number)a.get("id")).longValue();last=aid;
   db.update("INSERT INTO news_event_scanned_articles(article_id) VALUES(?)",aid);
   if(words((String)a.get("title_en")).size()<5)continue;
   var matches=db.queryForList("SELECT a.id,a.title_en FROM news_articles a WHERE a.id<? AND a.category=? AND a.published_at BETWEEN ? AND ? AND "+PUBLIC+" ORDER BY a.id DESC LIMIT 150",aid,a.get("category"),Timestamp.from(((Timestamp)a.get("published_at")).toInstant().minusSeconds(30*86400L)),Timestamp.from(((Timestamp)a.get("published_at")).toInstant().plusSeconds(86400)));
   for(var other:matches){
    double score=similarity((String)a.get("title_en"),(String)other.get("title_en"));if(score<0.65)continue;
    long oid=((Number)other.get("id")).longValue();
    var events=db.queryForList("SELECT e.id FROM news_events e JOIN news_event_articles l ON l.event_id=e.id WHERE l.article_id=? AND l.state<>'excluded' AND e.category=? ORDER BY e.id LIMIT 1",oid,a.get("category"));
    long eid;
    if(events.isEmpty()){
     var keys=new org.springframework.jdbc.support.GeneratedKeyHolder();
     db.update(c->{var s=c.prepareStatement("INSERT INTO news_events(title,category) SELECT LEFT(COALESCE(NULLIF(title_zh,''),title_en),300),category FROM news_articles WHERE id=?",new String[]{"id"});s.setLong(1,oid);return s;},keys);
     eid=keys.getKey().longValue();suggest(eid,oid,score);
    }else eid=((Number)events.getFirst().get("id")).longValue();
    suggest(eid,aid,score);break;
   }
  }
  db.update("UPDATE news_event_scan SET last_article_id=? WHERE id=1",last);
  long proposed=db.queryForObject("SELECT COUNT(*) FROM news_event_articles",Long.class)-beforeLinks;
  if(proposed>0)db.update("INSERT INTO audit_log(action,owner_username,actor_type,actor_id,actor_name,request_id,http_method,request_path,status_code,duration_ms,module,event_type,event_code,result,target_type,target_id,detail_json,error_message,ip_address,created_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)","news_event_suggested","system","SYSTEM","system","事件关联推荐",UUID.randomUUID().toString(),"SYSTEM","scheduled://news-events",200,0,"news","management","event_suggested","success","NEWS_EVENT","batch","{\"proposed\":"+proposed+"}",null,"internal",Timestamp.from(Instant.now()));
 }
 private void suggest(long event,long article,double confidence){
  if(db.queryForObject("SELECT COUNT(*) FROM news_event_articles WHERE event_id=? AND article_id=?",Long.class,event,article)==0)db.update("INSERT INTO news_event_articles(event_id,article_id,state,confidence,method) VALUES(?,?,'suggested',?,'headline')",event,article,confidence);
 }
}
