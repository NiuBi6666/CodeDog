package cn.codedog.security;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class PermissionCatalog {
    public static final String DASHBOARD_VIEW = "dashboard.view";
    public static final String DASHBOARD_DOCUMENT_STATS = "dashboard.document_stats";
    public static final String DASHBOARD_STUDENT_STATS = "dashboard.student_stats";
    public static final String DASHBOARD_LATEST_DOCUMENT = "dashboard.latest_document";
    public static final String STUDENTS_VIEW = "students.view";
    public static final String STUDENTS_QUERY = "students.query";
    public static final String STUDENTS_COPY = "students.copy";
    public static final String STUDENTS_EXPORT = "students.export";
    public static final String CLASS_PROGRESS_VIEW = "class_progress.view";
    public static final String CLASS_PROGRESS_IMPORT = "class_progress.import";
    public static final String CLASS_PROGRESS_COPY = "class_progress.copy";
    public static final String CLASS_PROGRESS_EXPORT = "class_progress.export";
    public static final String QUESTIONNAIRE_VIEW = "questionnaire.view";
    public static final String DOCUMENTS_VIEW = "documents.view";
    public static final String DOCUMENTS_CREATE = "documents.create";
    public static final String DOCUMENTS_EDIT = "documents.edit";
    public static final String DOCUMENTS_SHARE = "documents.share";
    public static final String DOCUMENTS_STATUS = "documents.status";
    public static final String LOGS_VIEW = "logs.view";

    public static final Set<String> DEFAULT_PERMISSIONS = Set.of(DASHBOARD_VIEW);

    private static final List<Group> GROUPS = List.of(
        new Group("dashboard", "首页", List.of(
            page(DASHBOARD_VIEW, "访问首页"),
            data(DASHBOARD_DOCUMENT_STATS, "查看文档统计"),
            data(DASHBOARD_STUDENT_STATS, "查看学生统计"),
            data(DASHBOARD_LATEST_DOCUMENT, "查看当前公开文档")
        )),
        new Group("students", "查询学生", List.of(
            page(STUDENTS_VIEW, "访问查询学生页面"),
            action(STUDENTS_QUERY, "执行学生查询"),
            action(STUDENTS_COPY, "复制查询结果"),
            action(STUDENTS_EXPORT, "导出查询结果")
        )),
        new Group("class_progress", "课堂完成情况", List.of(
            page(CLASS_PROGRESS_VIEW, "访问课堂完成情况页面"),
            action(CLASS_PROGRESS_IMPORT, "导入班级 Excel"),
            action(CLASS_PROGRESS_COPY, "复制课堂结果"),
            action(CLASS_PROGRESS_EXPORT, "导出课堂结果")
        )),
        new Group("questionnaire", "问卷与作业", List.of(
            page(QUESTIONNAIRE_VIEW, "访问问卷与作业")
        )),
        new Group("documents", "文档管理", List.of(
            page(DOCUMENTS_VIEW, "访问文档列表"),
            action(DOCUMENTS_CREATE, "新建文档"),
            action(DOCUMENTS_EDIT, "编辑文档"),
            action(DOCUMENTS_SHARE, "复制文档分享链接"),
            action(DOCUMENTS_STATUS, "上线或下线文档")
        )),
        new Group("logs", "操作日志", List.of(
            page(LOGS_VIEW, "访问操作日志")
        ))
    );

    private static final Set<String> ALL_CODES;

    static {
        LinkedHashSet<String> codes = new LinkedHashSet<>();
        GROUPS.forEach(group -> group.permissions().forEach(permission -> codes.add(permission.code())));
        ALL_CODES = Set.copyOf(codes);
    }

    private PermissionCatalog() {}

    public static List<Group> groups() { return GROUPS; }
    public static Set<String> allCodes() { return ALL_CODES; }

    private static Permission page(String code, String label) { return new Permission(code, label, "page"); }
    private static Permission data(String code, String label) { return new Permission(code, label, "data"); }
    private static Permission action(String code, String label) { return new Permission(code, label, "action"); }

    public record Permission(String code, String label, String type) {}
    public record Group(String key, String label, List<Permission> permissions) {}
}
