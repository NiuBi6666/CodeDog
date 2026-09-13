package cn.codedog;
import cn.codedog.exams.ExamExcelReader;
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
    @Autowired ExamExcelReader reader;

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
            assertThat(info.size()).isEqualTo(2);assertThat(info.get("title").asText()).isEqualTo("模拟考");
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
    @Test void queryIsRateLimited()throws Exception{
        var exam=create("限流考试",60);String token=exam.get("publicId").asText();
        for(int i=0;i<30;i++)mvc.perform(post("/api/public/exams/"+token+"/query").with(csrf()).header("X-Real-IP",token).contentType(APPLICATION_JSON).content("{\"name\":\"同名学员\"}")).andExpect(status().isOk());
        mvc.perform(post("/api/public/exams/"+token+"/query").with(csrf()).header("X-Real-IP",token).contentType(APPLICATION_JSON).content("{\"name\":\"同名学员\"}")).andExpect(status().isTooManyRequests());
    }
}
