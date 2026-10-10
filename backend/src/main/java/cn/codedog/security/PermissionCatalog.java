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
    public static final String RANKINGS_VIEW = "rankings.view";
    public static final String RANKINGS_BOARD_READ = "rankings.board.read";
    public static final String RANKINGS_SHARE = "rankings.share";
    public static final String RANKINGS_ANNOUNCEMENTS_READ = "rankings.announcements.read";
    public static final String RANKINGS_ANNOUNCEMENTS_CREATE = "rankings.announcements.create";
    public static final String RANKINGS_ANNOUNCEMENTS_EDIT = "rankings.announcements.edit";
    public static final String RANKINGS_ANNOUNCEMENTS_STATUS = "rankings.announcements.status";
    public static final String RANKINGS_REWARDS_READ = "rankings.rewards.read";
    public static final String RANKINGS_REWARDS_CREATE = "rankings.rewards.create";
    public static final String RANKINGS_REWARDS_EDIT = "rankings.rewards.edit";
    public static final String RANKINGS_REWARDS_DELETE = "rankings.rewards.delete";
    public static final String RANKINGS_REDEMPTIONS_READ = "rankings.redemptions.read";
    public static final String RANKINGS_REDEMPTIONS_CREATE = "rankings.redemptions.create";
    public static final String RANKINGS_REDEMPTIONS_FULFILL = "rankings.redemptions.fulfill";
    public static final String RANKINGS_POINTS_EDIT = "rankings.points.edit";
    public static final String RANKINGS_IMPORT = "rankings.import";
    public static final String RANKINGS_DEVICES_READ = "rankings.devices.read";
    public static final String RANKINGS_DEVICES_PAIR = "rankings.devices.pair";
    public static final String RANKINGS_DEVICES_REVOKE = "rankings.devices.revoke";
    public static final String EXAMS_VIEW = "exams.view";
    public static final String EXAMS_READ = "exams.read";
    public static final String EXAMS_INSPECT = "exams.inspect";
    public static final String EXAMS_CREATE = "exams.create";
    public static final String EXAMS_LINK_EDIT = "exams.link.edit";
    public static final String EXAMS_STATUS = "exams.status";
    public static final String DOCUMENTS_VIEW = "documents.view";
    public static final String DOCUMENTS_CREATE = "documents.create";
    public static final String DOCUMENTS_EDIT = "documents.edit";
    public static final String DOCUMENTS_SHARE = "documents.share";
    public static final String DOCUMENTS_STATUS = "documents.status";
    public static final String LOGS_VIEW = "logs.view";
    public static final String LOGS_EXPORT = "logs.export";
    public static final String USERS_VIEW = "users.view";
    public static final String USERS_PERMISSIONS_MANAGE = "users.permissions.manage";
    public static final String USERS_CRM_MANAGE = "users.crm.manage";

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
        new Group("rankings", "学生排名", List.of(
            page(RANKINGS_VIEW, "访问学生排名页面"),
            data(RANKINGS_BOARD_READ, "查看全量排名"),
            action(RANKINGS_SHARE, "分享学生排行榜"),
            data(RANKINGS_ANNOUNCEMENTS_READ, "查看公告"),
            action(RANKINGS_ANNOUNCEMENTS_CREATE, "发布公告"),
            action(RANKINGS_ANNOUNCEMENTS_EDIT, "修改公告内容和时间"),
            action(RANKINGS_ANNOUNCEMENTS_STATUS, "上线或下线公告"),
            data(RANKINGS_REWARDS_READ, "查看奖品"),
            action(RANKINGS_REWARDS_CREATE, "新增奖品"),
            action(RANKINGS_REWARDS_EDIT, "修改奖品"),
            action(RANKINGS_REWARDS_DELETE, "删除奖品"),
            data(RANKINGS_REDEMPTIONS_READ, "查看兑换记录"),
            action(RANKINGS_REDEMPTIONS_CREATE, "登记兑换"),
            action(RANKINGS_REDEMPTIONS_FULFILL, "确认或撤销发放"),
            action(RANKINGS_POINTS_EDIT, "手动调整学员积分"),
            action(RANKINGS_IMPORT, "导入排行榜数据"),
            data(RANKINGS_DEVICES_READ, "查看扩展设备"),
            action(RANKINGS_DEVICES_PAIR, "创建设备配对"),
            action(RANKINGS_DEVICES_REVOKE, "撤销扩展设备")
        )),
        new Group("exams", "成绩管理", List.of(
            page(EXAMS_VIEW, "访问成绩管理页面"),
            data(EXAMS_READ, "查看考试列表"),
            action(EXAMS_INSPECT, "解析成绩文件"),
            action(EXAMS_CREATE, "导入考试成绩"),
            action(EXAMS_LINK_EDIT, "修改成绩查询链接"),
            action(EXAMS_STATUS, "启用或暂停成绩查询")
        )),
        new Group("documents", "文档管理", List.of(
            page(DOCUMENTS_VIEW, "访问文档列表"),
            action(DOCUMENTS_CREATE, "新建文档"),
            action(DOCUMENTS_EDIT, "编辑文档"),
            action(DOCUMENTS_SHARE, "复制文档分享链接"),
            action(DOCUMENTS_STATUS, "上线或下线文档")
        )),
        new Group("logs", "操作日志", List.of(
            page(LOGS_VIEW, "访问操作日志"),
            action(LOGS_EXPORT, "导出操作日志")
        )),
        new Group("users", "用户与权限", List.of(
            page(USERS_VIEW, "访问用户与权限页面"),
            action(USERS_PERMISSIONS_MANAGE, "配置普通用户权限"),
            action(USERS_CRM_MANAGE, "配置 CRM 教师绑定")
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
