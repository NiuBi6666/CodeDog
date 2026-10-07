package cn.codedog.controller;

import cn.codedog.service.PermissionService;
import cn.codedog.service.StructuredAuditService;
import cn.codedog.service.StructuredAuditService.AuditRow;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.NullNode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/logs")
public class StructuredAuditLogController {
    private final StructuredAuditService service;
    private final PermissionService permissions;
    private final ObjectMapper json;
    public StructuredAuditLogController(StructuredAuditService service,PermissionService permissions,ObjectMapper json) {
        this.service=service; this.permissions=permissions; this.json=json;
    }

    @GetMapping
    public PageResponse logs(@ModelAttribute AuditFilters filters,Authentication authentication) {
        service.event("logs","read","audit_logs_listed");
        var page=service.list(filters.query(),authentication.getName(),permissions.isAdmin(authentication));
        service.detail(Map.of("filterCount",filters.activeCount(),"returnedCount",page.logs().size()));
        return new PageResponse(page.logs().stream().map(this::summaryRow).toList(),
            page.total(),page.page(),page.pageCount());
    }

    @GetMapping("/{id}")
    public DetailResponse detail(@PathVariable long id,Authentication authentication) {
        service.event("logs","read","audit_log_viewed"); service.target("AUDIT_LOG",id);
        return detailRow(service.detail(id,authentication.getName(),permissions.isAdmin(authentication)));
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> export(@ModelAttribute AuditFilters filters,Authentication authentication) {
        service.event("logs","export","audit_logs_exported");
        List<AuditRow> logs=service.export(filters.query(),authentication.getName(),permissions.isAdmin(authentication));
        service.detail(Map.of("filterCount",filters.activeCount(),"exportedCount",logs.size(),"format","CSV"));
        byte[] csv=csv(logs).getBytes(StandardCharsets.UTF_8),content=new byte[csv.length+3];
        content[0]=(byte)0xEF; content[1]=(byte)0xBB; content[2]=(byte)0xBF;
        System.arraycopy(csv,0,content,3,csv.length);
        HttpHeaders headers=new HttpHeaders();
        headers.setContentType(new MediaType("text","csv",StandardCharsets.UTF_8));
        headers.setContentDisposition(ContentDisposition.attachment().filename("codedog-audit-logs.csv",StandardCharsets.UTF_8).build());
        return ResponseEntity.ok().headers(headers).body(content);
    }

    private LogEntry summaryRow(AuditRow row) {
        String code=text(row.eventCode(),legacyCode(row.action())),module=text(row.module(),legacyModule(code));
        String detail=row.targetId()==null?"":(module.equals("documents")?"文档 #":"目标 #")+row.targetId();
        return new LogEntry(row.id(),text(row.actorType(),"UNKNOWN"),row.actorId(),text(row.actorName(),"未知"),
            row.ownerUsername(),module,moduleLabel(module),text(row.eventType(),"legacy"),code,operation(code),detail,
            row.targetType(),row.targetId(),text(row.result(),code.contains("failed")?"failed":"success"),
            row.statusCode(),row.durationMs(),row.ipAddress(),row.requestId(),row.createdAt());
    }

    private DetailResponse detailRow(AuditRow row) {
        String code=text(row.eventCode(),legacyCode(row.action())),module=text(row.module(),legacyModule(code));
        return new DetailResponse(row.id(),row.action(),row.ownerUsername(),text(row.actorType(),"UNKNOWN"),row.actorId(),
            text(row.actorName(),"未知"),row.requestId(),row.httpMethod(),row.requestPath(),parse(row.queryJson()),
            row.statusCode(),row.durationMs(),module,moduleLabel(module),text(row.eventType(),"legacy"),code,operation(code),
            text(row.result(),code.contains("failed")?"failed":"success"),row.targetType(),row.targetId(),
            parse(row.changesJson()),parse(row.detailJson()),row.errorMessage(),row.userAgent(),row.ipAddress(),row.createdAt());
    }

    private String csv(List<AuditRow> logs) {
        StringBuilder out=new StringBuilder("时间,操作者类型,操作者ID,操作者,租户,模块,事件类型,事件代码,目标类型,目标ID,结果,状态码,耗时毫秒,IP,请求ID,请求方法,请求路径,查询条件,字段变更,业务明细,失败原因,User-Agent\r\n");
        for(AuditRow row:logs) {
            Object[] fields={row.createdAt(),row.actorType(),row.actorId(),row.actorName(),row.ownerUsername(),row.module(),
                row.eventType(),row.eventCode(),row.targetType(),row.targetId(),row.result(),row.statusCode(),row.durationMs(),
                row.ipAddress(),row.requestId(),row.httpMethod(),row.requestPath(),row.queryJson(),row.changesJson(),
                row.detailJson(),row.errorMessage(),row.userAgent()};
            for(int i=0;i<fields.length;i++) { if(i>0) out.append(','); out.append(csvField(fields[i])); }
            out.append("\r\n");
        }
        return out.toString();
    }
    private String csvField(Object value) { return value==null?"":"\""+String.valueOf(value).replace("\"","\"\"")+"\""; }
    private JsonNode parse(String value) {
        if(value==null||value.isBlank()) return NullNode.getInstance();
        try { return json.readTree(value); } catch(Exception ignored) { return json.getNodeFactory().textNode(value); }
    }

    public record PageResponse(List<LogEntry> logs,long total,int page,int pageCount) {}
    public record LogEntry(long id,String actorType,String actorId,String actorName,String ownerUsername,
        String module,String moduleLabel,String eventType,String eventCode,String operation,String detail,
        String targetType,String targetId,String result,Integer statusCode,Long durationMs,String ipAddress,
        String requestId,java.time.Instant createdAt) {}

    public record DetailResponse(long id,String action,String ownerUsername,String actorType,String actorId,
        String actorName,String requestId,String httpMethod,String requestPath,JsonNode query,Integer statusCode,
        Long durationMs,String module,String moduleLabel,String eventType,String eventCode,String operation,String result,
        String targetType,String targetId,JsonNode changes,JsonNode detail,String errorMessage,String userAgent,
        String ipAddress,java.time.Instant createdAt) {}

    public static class AuditFilters {
        @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) private LocalDate startDate;
        @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) private LocalDate endDate;
        private String ownerUsername,module,eventType,actorType,actor,result,targetType,targetId,keyword,requestId;
        private Integer statusCode;
        private int page;
        public LocalDate getStartDate(){return startDate;} public void setStartDate(LocalDate value){startDate=value;}
        public LocalDate getEndDate(){return endDate;} public void setEndDate(LocalDate value){endDate=value;}
        public String getOwnerUsername(){return ownerUsername;} public void setOwnerUsername(String value){ownerUsername=value;}
        public String getModule(){return module;} public void setModule(String value){module=value;}
        public String getEventType(){return eventType;} public void setEventType(String value){eventType=value;}
        public String getActorType(){return actorType;} public void setActorType(String value){actorType=value;}
        public String getActor(){return actor;} public void setActor(String value){actor=value;}
        public String getResult(){return result;} public void setResult(String value){result=value;}
        public Integer getStatusCode(){return statusCode;} public void setStatusCode(Integer value){statusCode=value;}
        public String getTargetType(){return targetType;} public void setTargetType(String value){targetType=value;}
        public String getTargetId(){return targetId;} public void setTargetId(String value){targetId=value;}
        public String getKeyword(){return keyword;} public void setKeyword(String value){keyword=value;}
        public String getRequestId(){return requestId;} public void setRequestId(String value){requestId=value;}
        public int getPage(){return page;} public void setPage(int value){page=value;}
        StructuredAuditService.AuditQuery query() {
            return new StructuredAuditService.AuditQuery(startDate,endDate,ownerUsername,module,eventType,actorType,
                actor,result,statusCode,targetType,targetId,keyword,requestId,page);
        }
        int activeCount() {
            int count=startDate==null?0:1; if(endDate!=null) count++;
            for(Object value:new Object[]{ownerUsername,module,eventType,actorType,actor,result,statusCode,targetType,
                targetId,keyword,requestId}) if(value!=null&&!String.valueOf(value).isBlank()) count++;
            return count;
        }
    }

    private static String text(String value,String fallback) { return value==null||value.isBlank()?fallback:value; }
    private static String legacyCode(String action) { return action==null?"unknown":action.split(":",2)[0]; }
    private static String legacyModule(String code) {
        if(code.startsWith("login")||code.startsWith("registration")) return "auth";
        if(code.startsWith("password")||code.startsWith("permissions")||code.startsWith("crm_teacher")) return "account";
        if(code.startsWith("document")) return "documents";
        if(code.startsWith("student")) return "students";
        if(code.startsWith("class_progress")) return "classes";
        if(code.startsWith("ranking")) return "rankings";
        if(code.startsWith("exam")) return "exams";
        return "system";
    }
    private static String moduleLabel(String module) {
        return Map.ofEntries(Map.entry("auth","登录认证"),Map.entry("account","账户与权限"),
            Map.entry("documents","文档管理"),Map.entry("students","学生查询"),Map.entry("classes","课堂完成情况"),
            Map.entry("rankings","学生排名"),Map.entry("exams","成绩管理"),Map.entry("questionnaire","问卷与作业"),
            Map.entry("dashboard","首页"),Map.entry("logs","操作日志"),Map.entry("system","系统"))
            .getOrDefault(module,module==null?"系统":module);
    }
    private static String operation(String code) {
        return Map.ofEntries(Map.entry("login_succeeded","教师登录成功"),Map.entry("login_failed","教师登录失败"),
            Map.entry("student_login_succeeded","学生登录成功"),Map.entry("student_login_failed","学生登录失败"),
            Map.entry("registration_succeeded","注册教师"),Map.entry("registration_failed","注册失败"),
            Map.entry("password_changed","修改密码"),Map.entry("permissions_updated","配置用户权限"),
            Map.entry("document_created","新建文档"),Map.entry("document_updated","修改文档"),
            Map.entry("document_offline","下线文档"),Map.entry("document_normal","上线文档"),
            Map.entry("audit_logs_listed","查询日志"),Map.entry("audit_log_viewed","查看日志详情"),
            Map.entry("audit_logs_exported","导出日志"),Map.entry("audit_retention_cleanup","清理过期日志"))
            .getOrDefault(code,code==null?"未知操作":code.replace('_',' '));
    }
}
