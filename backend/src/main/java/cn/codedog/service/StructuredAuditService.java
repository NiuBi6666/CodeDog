package cn.codedog.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class StructuredAuditService {
    private static final ZoneId CHINA=ZoneId.of("Asia/Shanghai");
    private static final Set<String> RESULTS=Set.of("success","failed","rejected");
    private static final Set<String> ACTORS=Set.of("TEACHER","STUDENT","EXTENSION_DEVICE","ANONYMOUS","SYSTEM","UNKNOWN");
    private static final Set<String> MODULES=Set.of("auth","account","documents","students","classes","rankings",
        "exams","questionnaire","dashboard","logs","system");
    private static final Pattern SENSITIVE_KEY=Pattern.compile(
        "(?i).*(password|passwd|secret|token|cookie|authorization|csrf|cipher|hash|connection.?code|pairing.?code|recovery.?key).*"
    );
    private static final Pattern PHONE=Pattern.compile("(?<!\\d)(1\\d{2})\\d{4}(\\d{4})(?!\\d)");
    private static final String SELECT="""
        SELECT id,action,owner_username,actor_type,actor_id,actor_name,request_id,http_method,request_path,
          query_json,status_code,duration_ms,module,event_type,event_code,result,target_type,target_id,
          changes_json,detail_json,error_message,user_agent,ip_address,created_at FROM audit_log
        """;
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public StructuredAuditService(JdbcTemplate jdbc,ObjectMapper json) { this.jdbc=jdbc; this.json=json; }

    public void record(String action,HttpServletRequest request) {
        boolean temporary=!AuditContext.active();
        AuditContext.State state=temporary?AuditContext.begin(request.getMethod(),request.getRequestURI()):AuditContext.current();
        state.action(trim(action,255));
        parseLegacyAction(state,action);
        if(temporary) {
            finish(request,null,0);
            AuditContext.clear();
        }
    }
    public void event(String module,String eventType,String eventCode) {
        AuditContext.State state=AuditContext.current();
        if(state!=null) state.event(normalize(module),normalize(eventType),normalize(eventCode));
    }
    public void owner(String username) {
        AuditContext.State state=AuditContext.current();
        if(state!=null) state.owner(trim(username,50));
    }
    public void actor(String type,String id,String name) {
        AuditContext.State state=AuditContext.current();
        if(state!=null) state.actor(upper(type),trim(id,100),trim(maskPhones(name),100));
    }
    public void target(String type,Object id) {
        AuditContext.State state=AuditContext.current();
        if(state!=null) state.target(trim(upper(type),50),trim(id==null?null:String.valueOf(id),120));
    }
    public void detail(Map<String,?> values) {
        AuditContext.State state=AuditContext.current();
        if(state==null || values==null) return;
        JsonNode clean=sanitize(values);
        clean.fields().forEachRemaining(entry->state.detail(entry.getKey(),entry.getValue()));
    }
    public void change(String targetType,Object targetId,Map<String,?> before,Map<String,?> after) {
        AuditContext.State state=AuditContext.current();
        if(state==null) return;
        target(targetType,targetId);
        JsonNode oldValue=sanitize(before==null?Map.of():before);
        JsonNode newValue=sanitize(after==null?Map.of():after);
        LinkedHashSet<String> fields=new LinkedHashSet<>();
        oldValue.fieldNames().forEachRemaining(fields::add);
        newValue.fieldNames().forEachRemaining(fields::add);
        LinkedHashMap<String,Object> changed=new LinkedHashMap<>();
        for(String field:fields) {
            JsonNode oldField=oldValue.get(field),newField=newValue.get(field);
            if(!Objects.equals(oldField,newField)) {
                LinkedHashMap<String,Object> pair=new LinkedHashMap<>();
                pair.put("before",oldField==null?json.nullNode():oldField);
                pair.put("after",newField==null?json.nullNode():newField);
                changed.put(field,pair);
            }
        }
        state.changes(changed);
    }
    public Map<String,Object> fileMetadata(MultipartFile file) {
        LinkedHashMap<String,Object> value=new LinkedHashMap<>();
        value.put("filename",trim(file.getOriginalFilename(),255));
        value.put("contentType",trim(file.getContentType(),120));
        value.put("size",file.getSize());
        try(InputStream input=file.getInputStream()) {
            MessageDigest digest=MessageDigest.getInstance("SHA-256");
            byte[] buffer=new byte[8192];
            for(int read;(read=input.read(buffer))>=0;) if(read>0) digest.update(buffer,0,read);
            value.put("sha256",HexFormat.of().formatHex(digest.digest()));
        } catch(IOException|NoSuchAlgorithmException error) {
            value.put("sha256","unavailable");
        }
        return value;
    }

    public void finish(HttpServletRequest request,HttpServletResponse response,long durationMs) {
        AuditContext.State state=AuditContext.current();
        if(state==null) return;
        identifyActor(state,request);
        defaults(state,request);
        int status=response==null?200:response.getStatus();
        String result=status==401||status==403?"rejected":status>=400?"failed":"success";
        if(state.responseCount!=null) state.details.putIfAbsent("responseCount",state.responseCount);
        String error=status>=400?trim(maskPhones(state.errorMessage==null?"HTTP "+status:state.errorMessage),2000):null;
        jdbc.update("""
            INSERT INTO audit_log(action,owner_username,actor_type,actor_id,actor_name,request_id,
              http_method,request_path,query_json,status_code,duration_ms,module,event_type,event_code,
              result,target_type,target_id,changes_json,detail_json,error_message,user_agent,ip_address,created_at)
            VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
            """,trim(state.action==null?state.eventCode:state.action,255),state.ownerUsername,state.actorType,
            state.actorId,state.actorName,state.requestId,state.httpMethod,trim(state.requestPath,500),parametersJson(request),
            status,Math.max(durationMs,0),state.module,state.eventType,state.eventCode,result,state.targetType,state.targetId,
            state.changes.isEmpty()?null:writeJson(sanitize(state.changes)),
            state.details.isEmpty()?null:writeJson(sanitize(state.details)),error,trim(request.getHeader("User-Agent"),500),
            clientIp(request),Timestamp.from(Instant.now()));
    }

    public PageResult list(AuditQuery query,String viewer,boolean administrator) {
        validate(query);
        QueryParts parts=where(query,viewer,administrator);
        Long total=jdbc.queryForObject("SELECT COUNT(*) FROM audit_log"+parts.sql(),Long.class,parts.args().toArray());
        List<Object> args=new ArrayList<>(parts.args());
        args.add(30); args.add(Math.max(query.page(),0)*30);
        List<AuditRow> rows=jdbc.query(SELECT+parts.sql()+" ORDER BY created_at DESC,id DESC LIMIT ? OFFSET ?",
            this::row,args.toArray());
        long count=total==null?0:total;
        return new PageResult(rows,count,Math.max(query.page(),0),(int)((count+29)/30));
    }
    public AuditRow detail(long id,String viewer,boolean administrator) {
        String sql=SELECT+" WHERE id=?"+(administrator?"":" AND owner_username=?");
        List<AuditRow> rows=administrator?jdbc.query(sql,this::row,id):jdbc.query(sql,this::row,id,viewer);
        if(rows.isEmpty()) {
            if(!administrator && Boolean.TRUE.equals(jdbc.queryForObject("SELECT COUNT(*)>0 FROM audit_log WHERE id=?",Boolean.class,id)))
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,"无权查看该日志");
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,"日志不存在");
        }
        return rows.getFirst();
    }
    public List<AuditRow> export(AuditQuery query,String viewer,boolean administrator) {
        validate(query);
        QueryParts parts=where(query,viewer,administrator);
        List<Object> args=new ArrayList<>(parts.args()); args.add(50_001);
        List<AuditRow> rows=jdbc.query(SELECT+parts.sql()+" ORDER BY created_at DESC,id DESC LIMIT ?",this::row,args.toArray());
        if(rows.size()>50_000) throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
            "日志超过 50,000 条，请缩小时间范围");
        return rows;
    }

    private QueryParts where(AuditQuery query,String viewer,boolean administrator) {
        StringBuilder sql=new StringBuilder(" WHERE 1=1");
        List<Object> args=new ArrayList<>();
        if(!administrator) equal(sql,args,"owner_username",viewer);
        else equal(sql,args,"owner_username",query.ownerUsername());
        if(query.startDate()!=null) { sql.append(" AND created_at>=?"); args.add(Timestamp.from(query.startDate().atStartOfDay(CHINA).toInstant())); }
        if(query.endDate()!=null) { sql.append(" AND created_at<?"); args.add(Timestamp.from(query.endDate().plusDays(1).atStartOfDay(CHINA).toInstant())); }
        equal(sql,args,"module",query.module());
        equal(sql,args,"event_type",query.eventType());
        equal(sql,args,"actor_type",upper(query.actorType()));
        equal(sql,args,"result",query.result());
        equal(sql,args,"target_type",query.targetType());
        equal(sql,args,"target_id",query.targetId());
        equal(sql,args,"request_id",query.requestId());
        if(query.statusCode()!=null) { sql.append(" AND status_code=?"); args.add(query.statusCode()); }
        likeAny(sql,args,query.actor(),List.of("actor_id","actor_name"));
        likeAny(sql,args,query.keyword(),List.of("action","event_code","target_id","actor_name","ip_address"));
        return new QueryParts(sql.toString(),args);
    }
    private void equal(StringBuilder sql,List<Object> args,String column,String value) {
        String normalized=normalize(value);
        if(normalized.isEmpty()) return;
        sql.append(" AND LOWER(COALESCE(").append(column).append(",''))=?"); args.add(normalized);
    }
    private void likeAny(StringBuilder sql,List<Object> args,String value,List<String> columns) {
        String normalized=normalize(value);
        if(normalized.isEmpty()) return;
        sql.append(" AND (");
        for(int i=0;i<columns.size();i++) {
            if(i>0) sql.append(" OR ");
            sql.append("LOWER(COALESCE(").append(columns.get(i)).append(",'')) LIKE ?");
            args.add("%"+normalized+"%");
        }
        sql.append(')');
    }
    private void validate(AuditQuery query) {
        if(query.startDate()!=null&&query.endDate()!=null&&query.startDate().isAfter(query.endDate()))
            throw new IllegalArgumentException("开始日期不能晚于结束日期");
        if(!normalize(query.module()).isEmpty()&&!MODULES.contains(normalize(query.module())))
            throw new IllegalArgumentException("未知日志模块");
        if(!normalize(query.result()).isEmpty()&&!RESULTS.contains(normalize(query.result())))
            throw new IllegalArgumentException("未知执行结果");
        if(!upper(query.actorType()).isEmpty()&&!ACTORS.contains(upper(query.actorType())))
            throw new IllegalArgumentException("未知主体类型");
        if(query.statusCode()!=null&&(query.statusCode()<100||query.statusCode()>599))
            throw new IllegalArgumentException("无效状态码");
    }

    public int recentFailures(String failed,HttpServletRequest request,int minutes,String succeeded) {
        Integer count=jdbc.queryForObject("""
            SELECT COUNT(*) FROM audit_log WHERE (action=? OR event_code=?) AND ip_address=? AND created_at>=?
              AND id>COALESCE((SELECT MAX(id) FROM audit_log WHERE (action=? OR event_code=?) AND ip_address=?),0)
            """,Integer.class,failed,failed,clientIp(request),Timestamp.from(Instant.now().minus(minutes,ChronoUnit.MINUTES)),
            succeeded,succeeded,clientIp(request));
        return count==null?0:count;
    }
    public int recentRegistrations(HttpServletRequest request) {
        Integer count=jdbc.queryForObject("""
            SELECT COUNT(*) FROM audit_log WHERE (action LIKE 'registration_%' OR event_code LIKE 'registration_%')
              AND ip_address=? AND created_at>=?
            """,Integer.class,clientIp(request),Timestamp.from(Instant.now().minus(1,ChronoUnit.HOURS)));
        return count==null?0:count;
    }

    @Scheduled(cron="0 10 3 * * *",zone="Asia/Shanghai")
    public void purgeExpired() {
        Timestamp cutoff=Timestamp.from(Instant.now().minus(365,ChronoUnit.DAYS));
        int total=0,count;
        do {
            List<Long> ids=jdbc.queryForList(
                "SELECT id FROM audit_log WHERE created_at<? ORDER BY id LIMIT 1000",Long.class,cutoff);
            if(ids.isEmpty()) break;
            int[][] deleted=jdbc.batchUpdate("DELETE FROM audit_log WHERE id=?",ids,250,
                (statement,id)->statement.setLong(1,id));
            count=java.util.Arrays.stream(deleted).flatMapToInt(java.util.Arrays::stream).sum();
            total+=count;
        } while(count==1000);
        if(total>0) systemRecord("audit_retention_cleanup",Map.of("deletedCount",total,"retentionDays",365));
    }
    private void systemRecord(String code,Map<String,?> details) {
        jdbc.update("""
            INSERT INTO audit_log(action,actor_type,actor_name,request_id,http_method,request_path,status_code,
              duration_ms,module,event_type,event_code,result,detail_json,ip_address,created_at)
            VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
            """,code,"SYSTEM","系统任务",UUID.randomUUID().toString(),"SYSTEM","scheduled://audit-retention",200,0,
            "logs","delete",code,"success",writeJson(sanitize(details)),"127.0.0.1",Timestamp.from(Instant.now()));
    }

    private AuditRow row(ResultSet rs,int ignored) throws SQLException {
        return new AuditRow(rs.getLong("id"),rs.getString("action"),rs.getString("owner_username"),
            rs.getString("actor_type"),rs.getString("actor_id"),rs.getString("actor_name"),rs.getString("request_id"),
            rs.getString("http_method"),rs.getString("request_path"),rs.getString("query_json"),
            (Integer)rs.getObject("status_code"),(Long)rs.getObject("duration_ms"),rs.getString("module"),
            rs.getString("event_type"),rs.getString("event_code"),rs.getString("result"),rs.getString("target_type"),
            rs.getString("target_id"),rs.getString("changes_json"),rs.getString("detail_json"),rs.getString("error_message"),
            rs.getString("user_agent"),rs.getString("ip_address"),rs.getTimestamp("created_at").toInstant());
    }
    private void defaults(AuditContext.State state,HttpServletRequest request) {
        if(blank(state.module)) state.module=moduleForPath(request.getRequestURI());
        if(blank(state.eventType)) state.eventType=eventType(request.getMethod());
        if(blank(state.eventCode)) state.eventCode=genericCode(request.getMethod(),request.getRequestURI());
        if(blank(state.action)) state.action=state.eventCode;
        if(blank(state.actorType)) { state.actorType="ANONYMOUS"; state.actorName="匿名访问者"; }
        if(blank(state.targetId)) inferTargetFromPath(state,request.getRequestURI());
        if(blank(state.ownerUsername)&&!"ANONYMOUS".equals(state.actorType)) state.ownerUsername=state.actorId;
    }
    private void identifyActor(AuditContext.State state,HttpServletRequest request) {
        if(!blank(state.actorType)) return;
        HttpSession session=request.getSession(false);
        if(session!=null) {
            Object owner=session.getAttribute(StudentRankingAuthService.OWNER_ATTRIBUTE);
            Object studentId=session.getAttribute(StudentRankingAuthService.STUDENT_ID_ATTRIBUTE);
            if(owner!=null&&studentId!=null) {
                state.owner(trim(String.valueOf(owner),50));
                state.actor("STUDENT",trim(String.valueOf(studentId),100),
                    trim(maskName(String.valueOf(session.getAttribute(StudentRankingAuthService.STUDENT_NAME_ATTRIBUTE))),100));
                return;
            }
            Object context=session.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
            if(context instanceof SecurityContext security) {
                Authentication auth=security.getAuthentication();
                if(auth!=null&&auth.isAuthenticated()&&!"anonymousUser".equals(auth.getName())) {
                    state.actor("TEACHER",trim(auth.getName(),100),trim(auth.getName(),100));
                    if(blank(state.ownerUsername)) state.owner(trim(auth.getName(),50));
                    return;
                }
            }
        }
        String authorization=request.getHeader("Authorization");
        if(authorization!=null&&authorization.regionMatches(true,0,"Bearer ",0,7)) {
            try {
                String hash=sha256(authorization.substring(7).trim());
                jdbc.query("SELECT id,owner_username,device_name FROM ranking_extension_devices WHERE token_hash=? AND revoked_at IS NULL",
                    rs->{ if(rs.next()) { state.owner(trim(rs.getString(2),50)); state.actor("EXTENSION_DEVICE",String.valueOf(rs.getLong(1)),trim(rs.getString(3),100)); } return null; },hash);
            } catch(DataAccessException ignored) {
                // Invalid device credentials remain anonymous; credential values are never logged.
            }
        }
    }
    private void parseLegacyAction(AuditContext.State state,String raw) {
        String[] parts=raw==null?new String[0]:raw.split(":",-1);
        String code=parts.length==0?"api_request":normalize(parts[0]);
        state.eventCode=code; state.module=moduleForCode(code);
        if(code.startsWith("document_")&&parts.length>1) state.target("DOCUMENT",parts[1]);
        else if((code.startsWith("permissions_")||code.startsWith("crm_teacher_mapping_"))&&parts.length>1) {
            state.target("TEACHER",parts[1]); state.owner(parts[1]);
        } else if(code.startsWith("registration_")&&parts.length>1) {
            state.target("TEACHER",parts[1]); state.owner(parts[1]);
        } else if((code.contains("reward")||code.contains("announcement")||code.contains("redemption")||code.contains("device"))&&parts.length>1&&!parts[1].contains("=")) {
            String type=code.contains("reward")?"REWARD":code.contains("announcement")?"ANNOUNCEMENT":code.contains("device")?"EXTENSION_DEVICE":"REDEMPTION";
            state.target(type,parts[1]);
        }
        for(int i=1;i<parts.length;i++) {
            int equals=parts[i].indexOf('=');
            if(equals<=0) continue;
            String key=parts[i].substring(0,equals),value=parts[i].substring(equals+1);
            if("owner".equals(key)) state.owner(trim(value,50));
            else state.detail(key,SENSITIVE_KEY.matcher(key).matches()?"[REDACTED]":maskPhones(value));
        }
    }
    private String parametersJson(HttpServletRequest request) {
        if(request.getParameterMap().isEmpty()) return null;
        LinkedHashMap<String,Object> values=new LinkedHashMap<>();
        request.getParameterMap().forEach((key,value)->values.put(key,
            SENSITIVE_KEY.matcher(key).matches()?"[REDACTED]":value.length==1?maskPhones(value[0]):List.of(value)));
        return writeJson(sanitize(values));
    }
    private JsonNode sanitize(Object value) { return sanitizeNode(json.valueToTree(value),null); }
    private JsonNode sanitizeNode(JsonNode node,String fieldName) {
        if(fieldName!=null&&SENSITIVE_KEY.matcher(fieldName).matches()) return json.getNodeFactory().textNode("[REDACTED]");
        if(node instanceof ObjectNode object) {
            List<String> names=new ArrayList<>(); object.fieldNames().forEachRemaining(names::add);
            for(String name:names) object.set(name,sanitizeNode(object.get(name),name));
        } else if(node instanceof ArrayNode array) {
            for(int i=0;i<array.size();i++) array.set(i,sanitizeNode(array.get(i),fieldName));
        } else if(node!=null&&node.isTextual()) return json.getNodeFactory().textNode(maskPhones(node.asText()));
        return node;
    }
    private String writeJson(JsonNode value) {
        try { return json.writeValueAsString(value); }
        catch(JsonProcessingException error) { return "{\"serializationError\":true}"; }
    }
    private String moduleForPath(String path) {
        if(path.startsWith("/api/auth")) return "auth";
        if(path.startsWith("/api/logs")) return "logs";
        if(path.contains("/documents")) return "documents";
        if(path.contains("/rankings")) return "rankings";
        if(path.contains("/exams")) return "exams";
        if(path.contains("/students")) return "students";
        if(path.contains("/class-progress")) return "classes";
        if(path.contains("/questionnaire")) return "questionnaire";
        if(path.contains("/dashboard")) return "dashboard";
        if(path.startsWith("/api/admin")) return "account";
        return "system";
    }
    private String moduleForCode(String code) {
        if(code.startsWith("login")||code.startsWith("registration")) return "auth";
        if(code.startsWith("password")||code.startsWith("permissions")||code.startsWith("crm_teacher")) return "account";
        if(code.startsWith("document")) return "documents";
        if(code.startsWith("class_progress")) return "classes";
        if(code.startsWith("student")) return "students";
        if(code.startsWith("ranking")) return "rankings";
        if(code.startsWith("exam")) return "exams";
        if(code.startsWith("questionnaire")) return "questionnaire";
        return "system";
    }
    private String genericCode(String method,String path) {
        String normalized=path.replaceFirst("^/api/","").replaceAll("/[0-9]+(?=/|$)","/{id}")
            .replaceAll("/[0-9a-fA-F]{8}(?=/|$)","/{id}").replaceAll("[^A-Za-z0-9{}]+","_");
        return trim(method.toLowerCase(Locale.ROOT)+"_"+normalized.toLowerCase(Locale.ROOT),120);
    }
    private String eventType(String method) {
        return switch(method.toUpperCase(Locale.ROOT)) {
            case "GET","HEAD"->"read"; case "POST"->"create"; case "PUT","PATCH"->"update";
            case "DELETE"->"delete"; default->"execute";
        };
    }
    private void inferTargetFromPath(AuditContext.State state,String path) {
        String[] segments=path.split("/");
        for(int i=segments.length-1;i>=0;i--) if(segments[i].matches("[0-9]+|[0-9a-fA-F]{8}")) {
            state.targetType=i>0?segments[i-1].replace('-','_').toUpperCase(Locale.ROOT):"RESOURCE";
            state.targetId=segments[i]; return;
        }
    }
    private String clientIp(HttpServletRequest request) {
        String forwarded=request.getHeader("X-Forwarded-For");
        return trim(forwarded==null||forwarded.isBlank()?request.getRemoteAddr():forwarded.split(",",2)[0].trim(),80);
    }
    private String sha256(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch(NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    private String maskName(String value) {
        if(value==null||"null".equals(value)) return null;
        int length=value.codePointCount(0,value.length()); if(length<=1) return "*";
        int first=value.offsetByCodePoints(0,1),last=value.offsetByCodePoints(0,length-1);
        return value.substring(0,first)+"*".repeat(Math.max(1,length-2))+value.substring(last);
    }
    private String maskPhones(String value) { return value==null?null:PHONE.matcher(value).replaceAll("$1****$2"); }
    private String normalize(String value) { return value==null?"":value.trim().toLowerCase(Locale.ROOT); }
    private String upper(String value) { return value==null?"":value.trim().toUpperCase(Locale.ROOT); }
    private boolean blank(String value) { return value==null||value.isBlank(); }
    private String trim(String value,int max) { return value==null?null:value.length()<=max?value:value.substring(0,max); }

    private record QueryParts(String sql,List<Object> args) {}
    public record AuditQuery(LocalDate startDate,LocalDate endDate,String ownerUsername,String module,String eventType,
        String actorType,String actor,String result,Integer statusCode,String targetType,String targetId,String keyword,
        String requestId,int page) {}
    public record PageResult(List<AuditRow> logs,long total,int page,int pageCount) {}
    public record AuditRow(long id,String action,String ownerUsername,String actorType,String actorId,String actorName,
        String requestId,String httpMethod,String requestPath,String queryJson,Integer statusCode,Long durationMs,
        String module,String eventType,String eventCode,String result,String targetType,String targetId,String changesJson,
        String detailJson,String errorMessage,String userAgent,String ipAddress,Instant createdAt) {}
}
