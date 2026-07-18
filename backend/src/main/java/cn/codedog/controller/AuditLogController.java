package cn.codedog.controller;

import cn.codedog.model.AuditLog;
import cn.codedog.service.AuditService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/logs")
public class AuditLogController {
    private final AuditService service;

    public AuditLogController(AuditService service) { this.service = service; }

    @GetMapping
    public PageResponse logs(
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
        @RequestParam(required = false) String module,
        @RequestParam(required = false) String result,
        @RequestParam(required = false) String keyword,
        @RequestParam(defaultValue = "0") int page) {
        var logs = service.list(startDate, endDate, module, result, keyword, page);
        return new PageResponse(logs.getContent().stream().map(LogEntry::from).toList(),
            logs.getTotalElements(), logs.getNumber(), logs.getTotalPages());
    }

    public record PageResponse(List<LogEntry> logs, long total, int page, int pageCount) {}

    public record LogEntry(long id, String module, String moduleLabel, String operation,
                           String detail, String result, String ipAddress, Instant createdAt) {
        static LogEntry from(AuditLog log) {
            String action = log.getAction();
            String[] parts = action.split(":", -1);
            String type = parts[0];
            String module = module(type);
            return new LogEntry(log.getId(), module, moduleLabel(module), operation(type), detail(type, parts),
                type.equals("login_failed") ? "failed" : "success", log.getIpAddress(), log.getCreatedAt());
        }

        private static String module(String action) {
            if (action.startsWith("login_")) return "auth";
            if (action.startsWith("password_")) return "account";
            if (action.startsWith("document_")) return "documents";
            if (action.startsWith("student_")) return "students";
            return "system";
        }

        private static String moduleLabel(String module) {
            return switch (module) {
                case "auth" -> "登录认证";
                case "account" -> "账户安全";
                case "documents" -> "文档管理";
                case "students" -> "学生查询";
                default -> "系统";
            };
        }

        private static String operation(String action) {
            return switch (action) {
                case "login_succeeded" -> "登录成功";
                case "login_failed" -> "登录失败";
                case "password_changed" -> "修改密码";
                case "document_created" -> "新建文档";
                case "document_updated" -> "修改文档";
                case "document_offline" -> "下线文档";
                case "document_normal" -> "上线文档";
                case "student_query" -> "查询学生";
                default -> action;
            };
        }

        private static String detail(String action, String[] parts) {
            if (action.startsWith("document_") && parts.length > 1) return "文档 #" + parts[1];
            if (action.equals("student_query") && parts.length > 2) {
                String mode = parts[1].equals("name") ? "姓名" : "ID";
                return "按" + mode + "查询，处理 " + parts[2] + " 项";
            }
            return "";
        }
    }
}
