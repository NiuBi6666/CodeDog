package cn.codedog.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class ClassProgressService {
    private static final String ACCOUNT_BASE_URL = "https://internal-account-api.codemao.cn";
    private static final String CLASSROOM_BASE_URL = "https://api-live-class-crm.codemao.cn";
    private static final int MAX_QUESTIONS = 80;
    private static final int MAX_PAGES = 50;
    private static final int REQUESTED_PAGE_SIZE = 200;

    private final RestClient accountClient;
    private final RestClient classroomClient;
    private final boolean configured;

    @Autowired
    public ClassProgressService(@Value("${codedog.codemao.session-cookie:}") String sessionCookie) {
        String cookie = normalizeCookie(sessionCookie);
        this.configured = !cookie.isEmpty();
        this.accountClient = client(ACCOUNT_BASE_URL, cookie);
        this.classroomClient = client(CLASSROOM_BASE_URL, cookie);
    }

    ClassProgressService(RestClient accountClient, RestClient classroomClient, boolean configured) {
        this.accountClient = accountClient;
        this.classroomClient = classroomClient;
        this.configured = configured;
    }

    public Bootstrap bootstrap() {
        Teacher teacher = teacher();
        JsonNode response = get(classroomClient, "/live/camp/getCampInfo", builder -> builder
            .queryParam("internalTeacherId", teacher.id()).build(), "获取营期");
        List<Camp> camps = new ArrayList<>();
        for (JsonNode item : data(response)) {
            if (item.path("liveCampState").path("state").asInt() != 4) continue;
            camps.add(new Camp(item.path("campId").asLong(), item.path("campName").asText(),
                item.path("coursePackageName").asText(), textList(item.path("courseNames"))));
        }
        return new Bootstrap(teacher, camps);
    }

    public List<LiveClass> classes(long campId) {
        positive(campId, "营期 ID");
        Teacher teacher = teacher();
        JsonNode response = get(classroomClient, "/live/course/getCampInfo", builder -> builder
            .queryParam("campId", campId).queryParam("internalTeacherId", teacher.id()).build(), "获取班级");
        List<LiveClass> result = new ArrayList<>();
        for (JsonNode item : response.path("data").path("liveClassInfoBaseRespList")) {
            result.add(new LiveClass(item.path("classId").asLong(), item.path("className").asText(),
                item.path("classLevel").asText(), item.path("ratio").asText(),
                item.path("bindGroupChatName").asText()));
        }
        return result;
    }

    public List<Lesson> lessons(long classId) {
        positive(classId, "班级 ID");
        JsonNode response = get(classroomClient, "/live/teacher/class-board/lessons/all", builder -> builder
            .queryParam("classId", classId).queryParam("limitLocked", true).build(), "获取课次");
        List<Lesson> result = new ArrayList<>();
        for (JsonNode item : data(response)) {
            result.add(new Lesson(item.path("id").asLong(), item.path("name").asText(),
                item.path("lessonTime").asLong(), item.path("lessonEndTime").asLong(),
                item.path("state").asText()));
        }
        return result;
    }

    public Report report(long lessonId) {
        positive(lessonId, "课次 ID");
        JsonNode response = get(classroomClient, "/live/teacher/class-board/courses/overall/static", builder -> builder
            .queryParam("lessonId", lessonId).build(), "获取课次题目");
        List<QuestionSource> sources = questionSources(response);
        if (sources.size() > MAX_QUESTIONS) throw new UpstreamException("当前课次题目数量异常，已停止查询");
        if (sources.isEmpty()) return new Report(lessonId, List.of(), new ReportSummary(0, 0, 0, 0, 0));

        List<QuestionProgress> questions;
        try (ExecutorService executor = Executors.newFixedThreadPool(Math.min(6, sources.size()))) {
            List<CompletableFuture<QuestionProgress>> futures = sources.stream()
                .map(source -> CompletableFuture.supplyAsync(() -> questionProgress(lessonId, source), executor))
                .toList();
            questions = futures.stream().map(future -> {
                try {
                    return future.join();
                } catch (CompletionException error) {
                    if (error.getCause() instanceof RuntimeException runtime) throw runtime;
                    throw error;
                }
            }).toList();
        }

        int totalStudents = questions.stream().mapToInt(QuestionProgress::totalStudents).max().orElse(0);
        int submitted = questions.stream().mapToInt(QuestionProgress::submittedStudents).sum();
        int accepted = questions.stream().mapToInt(QuestionProgress::acceptedStudents).sum();
        int unsubmitted = questions.stream().mapToInt(QuestionProgress::unsubmittedStudents).sum();
        return new Report(lessonId, questions,
            new ReportSummary(questions.size(), totalStudents, submitted, accepted, unsubmitted));
    }

    private Teacher teacher() {
        JsonNode response = get(accountClient, "/auth/info", builder -> builder.build(), "获取老师信息");
        long id = response.path("id").asLong();
        if (id <= 0) throw new UpstreamException("编程猫账号未返回有效老师 ID");
        return new Teacher(id, response.path("fullname").asText());
    }

    private List<QuestionSource> questionSources(JsonNode response) {
        LinkedHashMap<String, QuestionSource> result = new LinkedHashMap<>();
        for (JsonNode course : data(response)) {
            for (JsonNode link : course.path("linkList")) {
                String group = link.path("name").asText();
                for (JsonNode step : link.path("normalDetail").path("stepList")) {
                    long questionId = step.path("ojCppDetail").path("questionId").asLong();
                    String stepId = step.path("id").asText();
                    if (questionId <= 0 || stepId.isBlank()) continue;
                    result.putIfAbsent(stepId, new QuestionSource(stepId, questionId,
                        step.path("name").asText("未命名题目"), group));
                }
            }
        }
        return List.copyOf(result.values());
    }

    private QuestionProgress questionProgress(long lessonId, QuestionSource source) {
        JsonNode first = statisticsPage(lessonId, source, 1);
        JsonNode firstData = first.path("data");
        int totalRows = firstData.path("total").asInt();
        int pageSize = firstData.path("pageSize").asInt();
        if (pageSize <= 0) pageSize = Math.max(1, firstData.path("records").size());
        int pages = Math.max(1, (totalRows + pageSize - 1) / pageSize);
        if (pages > MAX_PAGES) throw new UpstreamException("题目“" + source.name() + "”的学生分页数量异常");

        LinkedHashMap<String, StudentResult> students = new LinkedHashMap<>();
        collectStudents(firstData.path("records"), students);
        for (int page = 2; page <= pages; page++)
            collectStudents(statisticsPage(lessonId, source, page).path("data").path("records"), students);

        JsonNode statistics = firstData.path("statistics");
        int totalStudents = statistics.path("totalStudents").asInt(totalRows);
        int submitted = statistics.path("totalSubmittedStudents").asInt();
        int accepted = (int) students.values().stream().filter(student -> student.result().equals("AC")).count();
        int notAccepted = Math.max(0, submitted - accepted);
        int unsubmitted = Math.max(0, totalStudents - submitted);
        return new QuestionProgress(source.stepId(), source.questionId(), source.name(), source.group(),
            statistics.path("passRate").asText(), totalStudents, submitted, accepted, notAccepted,
            unsubmitted, List.copyOf(students.values()));
    }

    private JsonNode statisticsPage(long lessonId, QuestionSource source, int page) {
        return get(classroomClient, "/live/teacher/class-board/steps/statistics/oj/page", builder -> builder
            .queryParam("lessonId", lessonId).queryParam("stepId", source.stepId())
            .queryParam("sort", "submitTime").queryParam("questionId", source.questionId())
            .queryParam("page", page).queryParam("limit", REQUESTED_PAGE_SIZE).build(), "获取题目完成情况");
    }

    private void collectStudents(JsonNode records, Map<String, StudentResult> students) {
        for (JsonNode record : records) {
            JsonNode user = record.path("user");
            String id = user.path("id").asText();
            if (id.isBlank()) continue;
            String result = record.path("runResult").asText().trim().toUpperCase(Locale.ROOT);
            students.put(id, new StudentResult(id, user.path("name").asText(), result,
                resultLabel(result), record.path("submitTime").asLong(0)));
        }
    }

    private String resultLabel(String result) {
        return switch (result) {
            case "AC" -> "通过";
            case "WA" -> "答案错误";
            case "CE" -> "编译错误";
            case "RE" -> "运行错误";
            case "TLE" -> "超时";
            case "MLE" -> "内存超限";
            case "OLE" -> "输出超限";
            case "" -> "未提交";
            default -> result;
        };
    }

    private JsonNode get(RestClient client, String path,
                         java.util.function.Function<org.springframework.web.util.UriBuilder, java.net.URI> query,
                         String operation) {
        requireConfigured();
        try {
            JsonNode response = client.get().uri(builder -> query.apply(builder.path(path)))
                .retrieve().body(JsonNode.class);
            if (response == null) throw new UpstreamException(operation + "失败：上游未返回数据");
            if (response.has("success") && !response.path("success").asBoolean()) {
                String message = response.path("statusText").asText(operation + "失败");
                throw new UpstreamException(message);
            }
            return response;
        } catch (RestClientResponseException error) {
            if (error.getStatusCode().value() == 401 || error.getStatusCode().value() == 403)
                throw new UpstreamAuthenticationException("编程猫登录凭据已过期或没有接口权限");
            throw new UpstreamException(operation + "失败，上游状态码 " + error.getStatusCode().value());
        } catch (ResourceAccessException error) {
            throw new UpstreamException(operation + "失败，请稍后重试");
        }
    }

    private JsonNode data(JsonNode response) {
        JsonNode data = response.path("data");
        return data.isArray() ? data : com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.arrayNode();
    }

    private List<String> textList(JsonNode values) {
        List<String> result = new ArrayList<>();
        for (JsonNode value : values) if (value.isTextual()) result.add(value.asText());
        return result;
    }

    private void positive(long value, String label) {
        if (value <= 0) throw new DocumentService.ValidationException(label + "无效");
    }

    private void requireConfigured() {
        if (!configured) throw new NotConfiguredException("服务器尚未配置编程猫登录凭据");
    }

    private static RestClient client(String baseUrl, String cookie) {
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(6)).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(20));
        RestClient.Builder builder = RestClient.builder().baseUrl(baseUrl).requestFactory(requestFactory)
            .defaultHeader(HttpHeaders.ACCEPT, "application/json")
            .defaultHeader(HttpHeaders.USER_AGENT, "CodeDog/1.0")
            .defaultHeader(HttpHeaders.ORIGIN, "https://space-teacher.codemao.cn")
            .defaultHeader(HttpHeaders.REFERER, "https://space-teacher.codemao.cn/");
        if (!cookie.isEmpty()) builder.defaultHeader(HttpHeaders.COOKIE, cookie);
        return builder.build();
    }

    static String normalizeCookie(String value) {
        String cookie = value == null ? "" : value.trim();
        if (cookie.regionMatches(true, 0, "Cookie:", 0, 7)) cookie = cookie.substring(7).trim();
        if (cookie.length() > 16 * 1024 || cookie.indexOf('\r') >= 0 || cookie.indexOf('\n') >= 0) return "";
        return cookie;
    }

    private record QuestionSource(String stepId, long questionId, String name, String group) {}

    public record Teacher(long id, String name) {}
    public record Camp(long id, String name, String coursePackageName, List<String> courseNames) {}
    public record Bootstrap(Teacher teacher, List<Camp> camps) {}
    public record LiveClass(long id, String name, String level, String ratio, String groupName) {}
    public record Lesson(long id, String name, long lessonTime, long lessonEndTime, String state) {}
    public record StudentResult(String id, String name, String result, String resultLabel, long submitTime) {}
    public record QuestionProgress(String stepId, long questionId, String name, String group, String passRate,
                                   int totalStudents, int submittedStudents, int acceptedStudents,
                                   int notAcceptedStudents, int unsubmittedStudents, List<StudentResult> students) {}
    public record ReportSummary(int questionCount, int totalStudents, int submittedCount,
                                int acceptedCount, int unsubmittedCount) {}
    public record Report(long lessonId, List<QuestionProgress> questions, ReportSummary summary) {}

    public static class NotConfiguredException extends RuntimeException {
        public NotConfiguredException(String message) { super(message); }
    }
    public static class UpstreamAuthenticationException extends RuntimeException {
        public UpstreamAuthenticationException(String message) { super(message); }
    }
    public static class UpstreamException extends RuntimeException {
        public UpstreamException(String message) { super(message); }
    }
}
