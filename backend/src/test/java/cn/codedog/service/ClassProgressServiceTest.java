package cn.codedog.service;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class ClassProgressServiceTest {
    private static final String COOKIE = "session=secret";

    @Test
    void loadsTeacherCampsClassesAndLessons() {
        RestClient.Builder accountBuilder = RestClient.builder().baseUrl("https://account.test")
            .defaultHeader(HttpHeaders.COOKIE, COOKIE);
        RestClient.Builder classroomBuilder = RestClient.builder().baseUrl("https://classroom.test")
            .defaultHeader(HttpHeaders.COOKIE, COOKIE);
        MockRestServiceServer account = MockRestServiceServer.bindTo(accountBuilder).build();
        MockRestServiceServer classroom = MockRestServiceServer.bindTo(classroomBuilder).build();
        ClassProgressService service = new ClassProgressService(accountBuilder.build(), classroomBuilder.build(), true);

        account.expect(once(), requestTo("https://account.test/auth/info"))
            .andExpect(header(HttpHeaders.COOKIE, COOKIE))
            .andRespond(withSuccess("{\"id\":29413,\"fullname\":\"李老师\"}", MediaType.APPLICATION_JSON));
        classroom.expect(once(), requestTo("https://classroom.test/live/camp/getCampInfo?internalTeacherId=29413"))
            .andRespond(withSuccess("""
                {"success":true,"data":[
                  {"campId":91,"campName":"进行中","coursePackageName":"C3","courseNames":["P1"],"liveCampState":{"state":4}},
                  {"campId":67,"campName":"已结束","coursePackageName":"C2","courseNames":[],"liveCampState":{"state":5}}
                ]}
                """, MediaType.APPLICATION_JSON));
        account.expect(once(), requestTo("https://account.test/auth/info"))
            .andRespond(withSuccess("{\"id\":29413,\"fullname\":\"李老师\"}", MediaType.APPLICATION_JSON));
        classroom.expect(once(), requestTo("https://classroom.test/live/course/getCampInfo?campId=91&internalTeacherId=29413"))
            .andRespond(withSuccess("""
                {"success":true,"data":{"liveClassInfoBaseRespList":[
                  {"classId":1599,"className":"周六班","classLevel":"A","ratio":"21/21","bindGroupChatName":"周六09"}
                ]}}
                """, MediaType.APPLICATION_JSON));
        classroom.expect(once(), requestTo("https://classroom.test/live/teacher/class-board/lessons/all?classId=1599&limitLocked=true"))
            .andRespond(withSuccess("""
                {"success":true,"data":[{"id":46954,"name":"P1-枚举算法","lessonTime":1784250000,"lessonEndTime":1784260800,"state":"END"}]}
                """, MediaType.APPLICATION_JSON));

        ClassProgressService.Bootstrap bootstrap = service.bootstrap();
        assertThat(bootstrap.teacher().id()).isEqualTo(29413);
        assertThat(bootstrap.camps()).extracting(ClassProgressService.Camp::id).containsExactly(91L);
        assertThat(service.classes(91)).singleElement().satisfies(item -> {
            assertThat(item.id()).isEqualTo(1599);
            assertThat(item.name()).isEqualTo("周六班");
        });
        assertThat(service.lessons(1599)).singleElement().satisfies(item -> {
            assertThat(item.id()).isEqualTo(46954);
            assertThat(item.state()).isEqualTo("END");
        });
        account.verify();
        classroom.verify();
    }

    @Test
    void aggregatesEveryStatisticsPageAndKeepsUnsubmittedStudents() {
        RestClient.Builder classroomBuilder = RestClient.builder().baseUrl("https://classroom.test");
        MockRestServiceServer classroom = MockRestServiceServer.bindTo(classroomBuilder).build();
        ClassProgressService service = new ClassProgressService(RestClient.create(), classroomBuilder.build(), true);

        classroom.expect(once(), requestTo("https://classroom.test/live/teacher/class-board/courses/overall/static?lessonId=46954"))
            .andRespond(withSuccess("""
                {"success":true,"data":[{"linkList":[{"name":"课后作业","normalDetail":{"stepList":[
                  {"id":"step-1","name":"数字统计","ojCppDetail":{"questionId":19725}}
                ]}}]}]}
                """, MediaType.APPLICATION_JSON));
        statisticsPage(classroom, 1, "AC", "1001", "张三");
        statisticsPage(classroom, 2, "WA", "1002", "李四");
        statisticsPage(classroom, 3, "", "1003", "王五");

        ClassProgressService.Report report = service.report(46954);
        assertThat(report.summary().questionCount()).isEqualTo(1);
        assertThat(report.summary().totalStudents()).isEqualTo(3);
        assertThat(report.summary().acceptedCount()).isEqualTo(1);
        assertThat(report.summary().unsubmittedCount()).isEqualTo(1);
        assertThat(report.questions()).singleElement().satisfies(question -> {
            assertThat(question.submittedStudents()).isEqualTo(2);
            assertThat(question.notAcceptedStudents()).isEqualTo(1);
            assertThat(question.students()).extracting(ClassProgressService.StudentResult::resultLabel)
                .containsExactly("通过", "答案错误", "未提交");
        });
        classroom.verify();
    }

    @Test
    void rejectsMissingOrUnsafeCookieConfiguration() {
        assertThat(ClassProgressService.normalizeCookie("Cookie: session=ok")).isEqualTo("session=ok");
        assertThat(ClassProgressService.normalizeCookie("session=ok\r\nInjected: value")).isEmpty();
        ClassProgressService service = new ClassProgressService(RestClient.create(), RestClient.create(), false);
        assertThatThrownBy(service::bootstrap).isInstanceOf(ClassProgressService.NotConfiguredException.class)
            .hasMessageContaining("尚未配置");
    }

    private void statisticsPage(MockRestServiceServer server, int page, String result, String id, String name) {
        server.expect(once(), requestTo("https://classroom.test/live/teacher/class-board/steps/statistics/oj/page"
                + "?lessonId=46954&stepId=step-1&sort=submitTime&questionId=19725&page=" + page + "&limit=200"))
            .andRespond(withSuccess("""
                {"success":true,"data":{"statistics":{"passRate":"50%%","totalStudents":3,"totalSubmittedStudents":2},
                "records":[{"user":{"id":"%s","name":"%s"},"runResult":"%s","submitTime":1784251490}],
                "total":3,"currentPage":%d,"pageSize":1}}
                """.formatted(id, name, result, page), MediaType.APPLICATION_JSON));
    }
}
