package cn.codedog;
import cn.codedog.service.ExamExcelReader;
import com.fasterxml.jackson.databind.*;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import java.io.ByteArrayOutputStream;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @Transactional
class ExamIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired jakarta.persistence.EntityManager entityManager;
    @Autowired ExamExcelReader reader;
    @Autowired cn.codedog.service.ExamService service;

    MockMultipartFile file(boolean xls,Object[][] rows)throws Exception{
        try(Workbook b=xls?new HSSFWorkbook():new XSSFWorkbook();ByteArrayOutputStream out=new ByteArrayOutputStream()){
            Sheet s=b.createSheet("成绩表");
            for(int r=0;r<rows.length;r++){
                Row row=s.createRow(r);
                for(int c=0;c<rows[r].length;c++){
                    Object value=rows[r][c];
                    if(value instanceof Number number)row.createCell(c).setCellValue(number.doubleValue());
                    else if(value!=null)row.createCell(c).setCellValue(value.toString());
                }
            }
            b.write(out);return new MockMultipartFile("file",xls?"scores.xls":"scores.xlsx","application/octet-stream",out.toByteArray());
        }
    }
    String mapping(String title,int name,List<Integer> cols,List<String> labels)throws Exception{
        return json.writeValueAsString(new ExamExcelReader.Mapping(title,0,1,name,cols,labels));
    }
    JsonNode create(String title,double score)throws Exception{
        var file=file(false,new Object[][]{{"姓名","成绩","手机号"},{"同名学员",score,"不应公开"},{"另一学员",80,"不应公开"}});
        var result=mvc.perform(multipart("/api/admin/exams").file(file).param("mapping",mapping(title,0,List.of(1),List.of("总成绩"))).with(user("admin")).with(csrf()))
            .andExpect(status().isCreated()).andReturn();
        return json.readTree(result.getResponse().getContentAsString());
    }
    @Test void requiresAdminAndCsrf()throws Exception{
        mvc.perform(get("/api/admin/exams")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/exams").with(user("ordinary"))).andExpect(status().isForbidden());
        mvc.perform(multipart("/api/admin/exams/inspect").file(file(false,new Object[][]{{"姓名","成绩"},{"学员",90}})).with(user("admin")))
            .andExpect(status().isForbidden());
    }
    @Test void eachUploadCreatesIsolatedQueryEvenWithSameTitleAndName()throws Exception{
        var first=create("模拟考",0);var second=create("模拟考",91.5);
        assertThat(first.get("publicId").asText()).isNotEqualTo(second.get("publicId").asText());
        for(var exam:List.of(first,second)){
            String token=exam.get("publicId").asText();
            var info=json.readTree(mvc.perform(get("/api/public/exams/"+token)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
            assertThat(info.size()).isEqualTo(3);assertThat(info.get("title").asText()).isEqualTo("模拟考");
            var response=mvc.perform(post("/api/public/exams/"+token+"/query").with(csrf()).header("X-Real-IP",token).contentType(APPLICATION_JSON).content("{\"name\":\"  同名学员  \"}"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store")).andReturn().getResponse().getContentAsString();
            var result=json.readTree(response);
            assertThat(result.size()).isEqualTo(1);
            assertThat(result.get("scores").get(0).asText()).isEqualTo(exam==first?"未参考":"91.5");
            assertThat(response).doesNotContain("同名学员","另一学员","手机号","不应公开");
            mvc.perform(post("/api/public/exams/"+token+"/query").with(csrf()).header("X-Real-IP",token).contentType(APPLICATION_JSON).content("{\"name\":\"同名\"}"))
                .andExpect(status().isNotFound());
        }
    }
    @Test void duplicatesOrMissingNameDoNotCreatePartialExam()throws Exception{
        Long before=jdbc.queryForObject("select count(*) from exam_sessions",Long.class);
        for(Object[][] rows:List.of(new Object[][]{{"姓名","成绩"},{"重复",1},{"重复",2}},new Object[][]{{"姓名","成绩"},{null,2}})){
            mvc.perform(multipart("/api/admin/exams").file(file(false,rows)).param("mapping",mapping("错误表",0,List.of(1),List.of("分数"))).with(user("admin")).with(csrf()))
                .andExpect(status().isUnprocessableEntity());
        }
        assertThat(jdbc.queryForObject("select count(*) from exam_sessions",Long.class)).isEqualTo(before);
    }
    @Test void supportsSourceBAndWXYAndBlankRows()throws Exception{
        Object[][] rows=new Object[4][25];rows[0][1]="用户姓名";rows[0][22]="一轮";rows[0][23]="二轮";rows[0][24]="三轮";
        rows[2][1]="测试学员";rows[2][22]="未参考";rows[2][23]=33.5;rows[2][24]=0;
        rows[3][1]="空值学员";rows[3][22]=45;
        var file=file(false,rows);
        var inspect=reader.inspect(file,0,1);
        assertThat(inspect.columns().get(1).letter()).isEqualTo("B");
        assertThat(inspect.columns().get(22).letter()).isEqualTo("W");
        var parsed=reader.parse(file,new ExamExcelReader.Mapping("三轮模拟",0,1,1,List.of(22,23,24),List.of("一轮","二轮","三轮")));
        assertThat(parsed.students()).hasSize(2);
        assertThat(parsed.students().get("测试学员")).containsExactly("未参考","33.5","未参考");
        assertThat(parsed.students().get("空值学员")).containsExactly("45","暂无成绩","暂无成绩");
    }
    @Test void supportsXlsAndRejectsInvalidMapping()throws Exception{
        var file=file(true,new Object[][]{{"姓名","得分"},{"学员",75}});
        assertThat(reader.parse(file,new ExamExcelReader.Mapping("XLS考试",0,1,0,List.of(1),List.of("得分"))).students().get("学员")).containsExactly("75");
        mvc.perform(multipart("/api/admin/exams").file(file).param("mapping",mapping("错列",0,List.of(0),List.of("分数"))).with(user("admin")).with(csrf())).andExpect(status().isUnprocessableEntity());
        mvc.perform(multipart("/api/admin/exams/inspect").file(new MockMultipartFile("file","bad.xlsx","application/octet-stream",new byte[]{1,2,3})).with(user("admin")).with(csrf())).andExpect(status().isUnprocessableEntity());
    }
    @Test void canPauseAndResumeSameLink()throws Exception{
        var exam=create("暂停考试",60);String token=exam.get("publicId").asText();long id=exam.get("id").asLong();
        mvc.perform(patch("/api/admin/exams/"+id+"/status").with(user("admin")).with(csrf()).contentType(APPLICATION_JSON).content("{\"enabled\":false}")).andExpect(status().isOk());
        mvc.perform(get("/api/public/exams/"+token)).andExpect(status().isGone());
        mvc.perform(post("/api/public/exams/"+token+"/query").with(csrf()).header("X-Real-IP",token).contentType(APPLICATION_JSON).content("{\"name\":\"同名学员\"}")).andExpect(status().isGone());
        mvc.perform(patch("/api/admin/exams/"+id+"/status").with(user("admin")).with(csrf()).contentType(APPLICATION_JSON).content("{\"enabled\":true}")).andExpect(status().isOk()).andExpect(jsonPath("$.publicId").value(token));
        mvc.perform(get("/api/public/exams/"+token)).andExpect(status().isOk());
    }
    @Test void shortLinksAreEightCharactersAndLegacyLinksStillWork()throws Exception{
        var exam=create("短链接考试",88.5);
        String token=exam.get("publicId").asText();
        assertThat(token).matches("^(?=.*[A-Z])(?=.*[a-z])(?=.*[0-9])[A-Za-z0-9]{8}$");
        assertThat(exam.get("queryPath").asText()).isEqualTo("/exam/"+token);
        String legacy=jdbc.queryForObject("select public_id from exam_sessions where id=?",String.class,exam.get("id").asLong());
        assertThat(legacy).hasSize(32);
        for(String link:List.of(token,legacy)){
            mvc.perform(get("/api/public/exams/"+link)).andExpect(status().isOk()).andExpect(jsonPath("$.title").value("短链接考试"));
            mvc.perform(post("/api/public/exams/"+link+"/query").with(csrf()).header("X-Real-IP",legacy)
                .contentType(APPLICATION_JSON).content("{\"name\":\"同名学员\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.scores[0]").value("88.5"));
        }
        mvc.perform(get("/api/public/exams/"+token.substring(1))).andExpect(status().isNotFound());
        mvc.perform(patch("/api/admin/exams/"+exam.get("id").asLong()+"/status").with(user("admin")).with(csrf())
            .contentType(APPLICATION_JSON).content("{\"enabled\":false}")).andExpect(status().isOk());
        for(String link:List.of(token,legacy))mvc.perform(get("/api/public/exams/"+link)).andExpect(status().isGone());
    }
    @Test void queryIsRateLimited()throws Exception{
        var exam=create("限流考试",60);String token=exam.get("publicId").asText();
        for(int i=0;i<30;i++)mvc.perform(post("/api/public/exams/"+token+"/query").with(csrf()).header("X-Real-IP",token).contentType(APPLICATION_JSON).content("{\"name\":\"同名学员\"}")).andExpect(status().isOk());
        mvc.perform(post("/api/public/exams/"+token+"/query").with(csrf()).header("X-Real-IP",token).contentType(APPLICATION_JSON).content("{\"name\":\"同名学员\"}")).andExpect(status().isTooManyRequests());
    }

    String linkBody(String suffix,String expected)throws Exception{
        return json.writeValueAsString(Map.of("suffix",suffix,"expectedSuffix",expected));
    }
    void rename(JsonNode exam,String suffix,String expected,int expectedStatus)throws Exception{
        mvc.perform(patch("/api/admin/exams/"+exam.get("id").asLong()+"/link").with(user("admin")).with(csrf())
            .contentType(APPLICATION_JSON).content(linkBody(suffix,expected))).andExpect(status().is(expectedStatus));
    }
    @Test void editingRequiresAdminAndCsrf()throws Exception{
        var exam=create("权限测试",77);String path="/api/admin/exams/"+exam.get("id").asLong()+"/link";
        String body=linkBody("Test123a",exam.get("publicId").asText());
        mvc.perform(patch(path).with(csrf()).contentType(APPLICATION_JSON).content(body)).andExpect(status().isUnauthorized());
        mvc.perform(patch(path).with(user("ordinary")).with(csrf()).contentType(APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
        mvc.perform(patch(path).with(user("admin")).contentType(APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
    }
    @Test void savedLinkIsCanonicalAndHistoricalLinksRemainBoundToTheirExam()throws Exception{
        var exam=create("链接测试",91.5);String original=exam.get("publicId").asText();
        rename(exam,"Abcd1234",original,200);
        mvc.perform(get("/api/admin/exams").with(user("admin"))).andExpect(status().isOk())
            .andExpect(jsonPath("$.exams[0].queryPath").value("/exam/Abcd1234"));
        rename(exam,"Next123a","Abcd1234",200);
        for(String code:List.of(original,"Abcd1234","Next123a"))
            mvc.perform(post("/api/public/exams/"+code+"/query").with(csrf()).header("X-Real-IP",original)
                .contentType(APPLICATION_JSON).content("{\"name\":\"同名学员\"}")).andExpect(status().isOk()).andExpect(jsonPath("$.scores[0]").value("91.5"));
        var other=create("其他考试",60);
        rename(other,"Abcd1234",other.get("publicId").asText(),409);
        rename(other,"Next123a",other.get("publicId").asText(),409);
        rename(exam,"Abcd1234","Next123a",200);
        rename(exam,"Test123a",original,409);
        mvc.perform(patch("/api/admin/exams/"+exam.get("id").asLong()+"/status").with(user("admin")).with(csrf())
            .contentType(APPLICATION_JSON).content("{\"enabled\":false}")).andExpect(status().isOk());
        for(String code:List.of(original,"Abcd1234","Next123a"))
            mvc.perform(get("/api/public/exams/"+code)).andExpect(status().isGone());
    }
    @Test void linksAreCaseSensitiveAndInvalidSuffixesCannotChangePrefix()throws Exception{
        var exam=create("格式测试",80);String original=exam.get("publicId").asText();
        for(String invalid:List.of("12345678","abcdefgh","ABCDEFGH","abcd1234","ABCD1234","AbCdEfGh","Ab12345","Ab1234567","Abcd12_3","Abcd12/3"," Abcd123","https://codedog.online/exam/Abcd1234"))
            rename(exam,invalid,original,422);
        rename(exam,"Abcd1234",original,200);
        mvc.perform(get("/api/public/exams/abcd1234")).andExpect(status().isNotFound());
        var other=create("大小写不同考试",61);
        rename(other,"aBcd1234",other.get("publicId").asText(),200);
        mvc.perform(get("/api/public/exams/Abcd1234")).andExpect(status().isOk()).andExpect(jsonPath("$.title").value("格式测试"));
        mvc.perform(get("/api/public/exams/aBcd1234")).andExpect(status().isOk()).andExpect(jsonPath("$.title").value("大小写不同考试"));
    }
    @Test void existingEightDigitAndUuidLinksSurviveIdempotentBackfill()throws Exception{
        String uuid=UUID.randomUUID().toString().replace("-","");
        jdbc.update("insert into exam_sessions(public_id,title,score_labels,student_count,enabled,created_by,created_at,result_mode) values(?,?,?,0,true,'admin',CURRENT_TIMESTAMP,'legacy')",uuid,"旧考试","[\"成绩\"]");
        Long id=jdbc.queryForObject("select id from exam_sessions where public_id=?",Long.class,uuid);
        String numeric=String.format("%08x",id);
        service.initializeLinks();
        entityManager.flush();
        String code=jdbc.queryForObject("select query_code from exam_sessions where id=?",String.class,id);
        assertThat(code).matches("(?=.*[A-Z])(?=.*[a-z])(?=.*[0-9])[A-Za-z0-9]{8}");
        service.initializeLinks();
        assertThat(jdbc.queryForObject("select query_code from exam_sessions where id=?",String.class,id)).isEqualTo(code);
        for(String token:List.of(uuid,numeric,code))
            mvc.perform(get("/api/public/exams/"+token)).andExpect(status().isOk()).andExpect(jsonPath("$.title").value("旧考试"));
    }
    @Test @Transactional(propagation=org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void simultaneousClaimsCannotReassignAnAlias()throws Exception{
        var first=create("并发一",81);var second=create("并发二",82);
        long a=first.get("id").asLong(),b=second.get("id").asLong();
        var pool=java.util.concurrent.Executors.newFixedThreadPool(2);
        var ready=new java.util.concurrent.CountDownLatch(2);
        var start=new java.util.concurrent.CountDownLatch(1);
        try{
            var futures=new ArrayList<java.util.concurrent.Future<Long>>();
            for(var exam:List.of(first,second)){
                futures.add(pool.submit(()->{
                    ready.countDown();start.await();
                    try{
                        return service.changeLink(exam.get("id").asLong(),"Race123a",exam.get("publicId").asText()).id();
                    }catch(org.springframework.web.server.ResponseStatusException failure){
                        if(failure.getStatusCode().value()!=409)throw failure;
                        return 0L;
                    }
                }));
            }
            assertThat(ready.await(10,java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            start.countDown();
            var outcomes=new ArrayList<Long>();
            for(var future:futures)outcomes.add(future.get(15,java.util.concurrent.TimeUnit.SECONDS));
            assertThat(outcomes.stream().filter(id->id!=0).count()).isEqualTo(1);
            long winner=outcomes.stream().filter(id->id!=0).findFirst().orElseThrow();
            assertThat(jdbc.queryForObject("select exam_id from exam_query_aliases where code='Race123a'",Long.class)).isEqualTo(winner);
            assertThat(service.query("Race123a","同名学员").scores()).containsExactly(winner==a?"81":"82");
        }finally{
            start.countDown();pool.shutdownNow();
            pool.awaitTermination(10,java.util.concurrent.TimeUnit.SECONDS);
            jdbc.update("delete from exam_query_aliases where exam_id in (?,?)",a,b);
            jdbc.update("delete from exam_scores where exam_id in (?,?)",a,b);
            jdbc.update("delete from exam_sessions where id in (?,?)",a,b);
        }
    }

    Object[][] templateRows(boolean full){
        List<Object> header=new ArrayList<>(List.of("用户id","用户姓名","老师姓名","提交时间","正确题目数","总得分"));
        List<Object> attended=new ArrayList<>(List.of("private-id","参赛学员","NaT","2026-09-12 12:30:00",1,0));
        List<Object> absent=new ArrayList<>(List.of("private-id-2","缺赛学员","private-teacher"," NaT ",0,99));
        if(full){
            for(int n:List.of(1,2,3,4,5,6,7,9,8,10,11,12,13,14,15,16,17,18,19,20)){
                header.add("第"+n+"题得分");
                attended.add(n==1?0:n==20?6.5:n);
                absent.add("不应展示");
            }
        }
        return new Object[][]{header.toArray(),attended.toArray(),absent.toArray()};
    }
    JsonNode createTemplate(boolean full)throws Exception{
        var result=mvc.perform(multipart("/api/admin/exams").file(file(false,templateRows(full)))
            .param("mapping",mapping(full?"全量模板测试":"简单模板测试",0,List.of(2),List.of("老师姓名")))
            .with(user("admin")).with(csrf())).andExpect(status().isCreated()).andReturn();
        return json.readTree(result.getResponse().getContentAsString());
    }
    @Test void fullTemplateSelectsAllTwentyQuestionsPlusTotalByHeaderAndQuestionNumber()throws Exception{
        var workbook=file(false,templateRows(true));
        var inspection=reader.inspect(workbook,0,1);
        assertThat(inspection.template().type()).isEqualTo("full");
        assertThat(inspection.template().scoreColumns()).hasSize(21);
        assertThat(inspection.template().scoreColumns().get(8)).isEqualTo(14);
        assertThat(inspection.template().scoreColumns().get(9)).isEqualTo(13);
        var exam=createTemplate(true);String token=exam.get("publicId").asText();
        assertThat(exam.get("resultMode").asText()).isEqualTo("full");
        var metadata=json.readTree(mvc.perform(get("/api/public/exams/"+token)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(metadata.get("scoreLabels")).hasSize(21);
        assertThat(metadata.get("scoreLabels").get(0).asText()).isEqualTo("总成绩");
        assertThat(metadata.get("scoreLabels").get(8).asText()).isEqualTo("第8题得分");
        assertThat(metadata.get("scoreLabels").get(20).asText()).isEqualTo("第20题得分");
        var result=mvc.perform(post("/api/public/exams/"+token+"/query").with(csrf()).header("X-Real-IP",token)
            .contentType(APPLICATION_JSON).content("{\"name\":\"参赛学员\"}")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var values=json.readTree(result).get("scores");
        assertThat(values).hasSize(21);
        assertThat(values.get(0).asText()).isEqualTo("0");
        assertThat(values.get(1).asText()).isEqualTo("0");
        assertThat(values.get(8).asText()).isEqualTo("8");
        assertThat(values.get(9).asText()).isEqualTo("9");
        assertThat(values.get(20).asText()).isEqualTo("6.5");
        assertThat(result).doesNotContain("参赛学员","private-id","老师","提交时间","未参考","NaT");
    }
    @Test void simpleTemplateOnlyPublishesTotalAndNaTMeansAbsentInBothFormats()throws Exception{
        for(boolean full:List.of(false,true)){
            var exam=createTemplate(full);String token=exam.get("publicId").asText();
            var response=mvc.perform(post("/api/public/exams/"+token+"/query").with(csrf()).header("X-Real-IP",token)
                .contentType(APPLICATION_JSON).content("{\"name\":\"缺赛学员\"}")).andExpect(status().isOk())
                .andExpect(jsonPath("$.absent").value(true)).andExpect(jsonPath("$.scores").isEmpty()).andReturn().getResponse().getContentAsString();
            assertThat(response).doesNotContain("缺赛学员","private-id","private-teacher","不应展示","99");
            if(!full){
                assertThat(exam.get("scoreLabels")).hasSize(1);
                assertThat(exam.get("scoreLabels").get(0).asText()).isEqualTo("总成绩");
                mvc.perform(post("/api/public/exams/"+token+"/query").with(csrf()).header("X-Real-IP",token)
                    .contentType(APPLICATION_JSON).content("{\"name\":\"参赛学员\"}")).andExpect(status().isOk())
                    .andExpect(jsonPath("$.scores[0]").value("0")).andExpect(jsonPath("$.scores.length()").value(1));
            }
        }
    }
    @Test void templateMatchingToleratesReorderedColumnsAndExtraPrivateFields()throws Exception{
        var workbook=file(true,new Object[][]{
            {"手机号"," 第2题得分 ","总得分","用户姓名","第1题得分","提交时间","是否参加比赛","比赛ID-名称"},
            {"private-phone",9,12,"测试学员",3,"2026-09-12 18:00:00",0,"private-exam"}
        });
        var parsed=reader.parse(workbook,new ExamExcelReader.Mapping("测试",0,1,0,List.of(0),List.of("错误列")));
        assertThat(parsed.resultMode()).isEqualTo("full");
        assertThat(parsed.labels()).containsExactly("总成绩","第1题得分","第2题得分");
        assertThat(parsed.students().get("测试学员")).containsExactly("12","3","9");
        assertThat(parsed.absentNames()).isEmpty();
    }
    @Test void templateRejectsMissingOrAmbiguousHeadersInsteadOfPublishingWrongColumns()throws Exception{
        for(Object[] header:List.of(
            new Object[]{"用户姓名","总得分","第1题得分"},
            new Object[]{"用户姓名","提交时间","第1题得分"},
            new Object[]{"用户姓名","提交时间","总得分","总得分"},
            new Object[]{"用户姓名","提交时间","总得分","第1题得分","第1题得分"},
            new Object[]{"用户姓名","姓名","提交时间","总得分"})){
            mvc.perform(multipart("/api/admin/exams/inspect").file(file(false,new Object[][]{header})).with(user("admin")).with(csrf()))
                .andExpect(status().isUnprocessableEntity());
        }
    }
    @Test void onlySubmissionNaTMarksAbsenceAndBlankScoresStayUnavailable()throws Exception{
        var workbook=file(false,new Object[][]{
            {"用户姓名","提交时间","总得分"},
            {"零分","2026-09-12 18:00:00",0},
            {"缺赛","nat",100},
            {"待批阅","2026-09-12 18:00:00",null},
            {"时间空白",null,0}
        });
        var parsed=reader.parse(workbook,new ExamExcelReader.Mapping("测试",0,1,0,null,null));
        assertThat(parsed.absentNames()).containsExactly("缺赛");
        assertThat(parsed.students().get("零分")).containsExactly("0");
        assertThat(parsed.students().get("待批阅")).containsExactly("暂无成绩");
        assertThat(parsed.students().get("时间空白")).containsExactly("0");
        assertThat(parsed.students().get("缺赛")).isEmpty();
    }

    @Test void defaultExportIncludesEveryPopulatedColumnAfterQAndKeepsZero()throws Exception{
        Object[][] rows=new Object[38][21];
        rows[0][1]="用户姓名";rows[0][14]="提交时间";rows[0][16]="总得分";
        rows[0][17]="第1题得分";rows[0][18]="整列空白";rows[0][19]="加分";
        rows[1][0]="private-id";rows[1][1]="零分学员";rows[1][14]="2026-09-13 10:00:00";rows[1][16]=0;rows[1][17]=0;
        rows[2][1]="缺赛学员";rows[2][14]="NaT";rows[2][16]=100;
        rows[37][1]="末行学员";rows[37][14]="2026-09-13 11:00:00";rows[37][16]=3;rows[37][19]=7.5;rows[37][20]=2;
        var workbook=file(false,rows);
        var inspection=reader.inspect(workbook,0,1);
        assertThat(inspection.template().defaultLayout()).isTrue();
        assertThat(inspection.template().scoreColumns()).containsExactly(16,17,19,20);
        assertThat(inspection.template().scoreLabels()).containsExactly("总成绩","第1题得分","加分","U列成绩");
        var parsed=reader.parse(workbook,new ExamExcelReader.Mapping("默认模板",0,1,0,List.of(0),List.of("私密信息")));
        assertThat(parsed.students().get("零分学员")).containsExactly("0","0","","");
        assertThat(parsed.students().get("末行学员")).containsExactly("3","","7.5","2");
        assertThat(parsed.absentNames()).containsExactly("缺赛学员");
        assertThat(parsed.students().get("缺赛学员")).isEmpty();
    }
    @Test void defaultExportWithEmptyTrailingColumnsOnlyShowsTotal()throws Exception{
        Object[][] rows=new Object[2][19];
        rows[0][1]="用户姓名";rows[0][14]="提交时间";rows[0][16]="总得分";rows[0][18]="未使用明细";
        rows[1][1]="学员";rows[1][14]="2026-09-13 10:00:00";rows[1][16]=0;
        var inspection=reader.inspect(file(false,rows),0,1);
        assertThat(inspection.template().defaultLayout()).isTrue();
        assertThat(inspection.template().type()).isEqualTo("simple");
        assertThat(inspection.template().scoreColumns()).containsExactly(16);
    }
}
