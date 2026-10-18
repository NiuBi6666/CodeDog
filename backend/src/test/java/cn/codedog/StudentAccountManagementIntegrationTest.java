package cn.codedog;

import cn.codedog.dao.UserRepository;
import cn.codedog.model.User;
import cn.codedog.service.*;
import java.time.Instant;
import java.util.*;
import java.io.ByteArrayOutputStream;
import java.util.concurrent.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:studentphones;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE"})
@AutoConfigureMockMvc
class StudentAccountManagementIntegrationTest {
  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired UserRepository users;
  @Autowired PasswordEncoder encoder;
  @Autowired StudentAccountManagementService accounts;
  @Autowired StudentRankingAuthService studentAuth;
  @Autowired StudentPhoneXlsxParser parser;
  @Autowired cn.codedog.service.RankingAftercareService aftercare;
  String owner;
  @BeforeEach void setup() {
    cn.codedog.OperationsTestSchema.create(jdbc);
    jdbc.execute("""
      CREATE TABLE IF NOT EXISTS ranking_students(owner_username VARCHAR(50),camp_id VARCHAR(100),class_id VARCHAR(100),
      student_id VARCHAR(100),student_name VARCHAR(100),PRIMARY KEY(owner_username,camp_id,class_id,student_id))
      """);
    jdbc.execute("""
      CREATE TABLE IF NOT EXISTS ranking_student_accounts(id BIGINT AUTO_INCREMENT PRIMARY KEY,phone VARCHAR(20) NOT NULL,
      owner_username VARCHAR(50) NOT NULL,student_id VARCHAR(100) NOT NULL,student_name VARCHAR(100) NOT NULL,password_hash VARCHAR(100) NOT NULL,
      password_ciphertext CLOB,enabled BOOLEAN DEFAULT TRUE,created_at TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP,
      updated_at TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP,password_changed_at TIMESTAMP(6),session_version INT NOT NULL DEFAULT 0,must_change_password BOOLEAN NOT NULL DEFAULT FALSE,UNIQUE(owner_username,student_id))
      """);
    owner="phone"+UUID.randomUUID().toString().substring(0,8);
    User teacher=new User();teacher.setUsername(owner);teacher.setPasswordHash(encoder.encode("teacher-test-password"));
    teacher.setAdmin(false);teacher.setCreatedAt(Instant.now());teacher.setUpdatedAt(Instant.now());teacher.setPermissions(new LinkedHashSet<>());
    users.saveAndFlush(teacher);
    add(owner,"s1","同学一");add(owner,"s2","同学二");
  }
  void add(String teacher,String id,String name) {jdbc.update("INSERT INTO ranking_students VALUES(?,'camp','class',?,?)",teacher,id,name);}
  String phone() {return "1"+String.format("%010d",Math.floorMod(UUID.randomUUID().getMostSignificantBits(),10000000000L));}
  void grant(String...permissions) {User teacher=users.findByUsername(owner).orElseThrow();teacher.setPermissions(new LinkedHashSet<>(List.of(permissions)));users.saveAndFlush(teacher);}
  MockHttpSession teacherLogin() throws Exception {
    return (MockHttpSession)mvc.perform(post("/api/auth/login").with(csrf()).contentType(APPLICATION_JSON)
      .content("{\"username\":\""+owner+"\",\"password\":\"teacher-test-password\"}"))
      .andExpect(status().isOk()).andReturn().getRequest().getSession(false);
  }
  String change(String number,String expected) {return "{\"phone\":\""+number+"\",\"expectedPhone\":\""+expected+"\"}";}
  MockMultipartFile xlsx(String[][] values) throws Exception {
    try(var workbook=new XSSFWorkbook();var output=new ByteArrayOutputStream()) {
      var sheet=workbook.createSheet("手机号");int index=0;
      for(String[] valuesRow:values){var row=sheet.createRow(index++);for(int i=0;i<valuesRow.length;i++)row.createCell(i).setCellValue(valuesRow[i]);}
      workbook.write(output);return new MockMultipartFile("file","phones.xlsx","application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",output.toByteArray());
    }
  }
  @Test void permissionsCsrfOwnershipAndImmediateRevocation() throws Exception {
    var session=teacherLogin();String number=phone();
    mvc.perform(get("/api/rankings/admin/students/account-status").session(session)).andExpect(status().isForbidden());
    mvc.perform(get("/api/rankings/admin/students/s1/phone").session(session)).andExpect(status().isForbidden());
    mvc.perform(put("/api/rankings/admin/students/s1/phone").session(session).with(csrf()).contentType(APPLICATION_JSON).content(change(number,""))).andExpect(status().isForbidden());
    var file=xlsx(new String[][]{{"用户ID","姓名","手机号"},{"s1","同学一",number}});
    mvc.perform(multipart("/api/rankings/admin/imports/phones").file(file).session(session).with(csrf())).andExpect(status().isForbidden());
    grant("rankings.board.read","rankings.accounts.import","rankings.accounts.edit");
    mvc.perform(get("/api/rankings/admin/students/account-status").session(session)).andExpect(status().isOk()).andExpect(jsonPath("$[0].status").value("PENDING"));
    mvc.perform(multipart("/api/rankings/admin/imports/phones").file(file).session(session)).andExpect(status().isForbidden());
    mvc.perform(multipart("/api/rankings/admin/imports/phones").file(file).session(session).with(csrf())).andExpect(status().isOk()).andExpect(jsonPath("$.createdRows").value(1));
    mvc.perform(get("/api/rankings/admin/students/s1/phone").session(session)).andExpect(status().isOk()).andExpect(jsonPath("$.phone").value(number));
    String replacement=phone();
    mvc.perform(put("/api/rankings/admin/students/s1/phone").session(session).with(csrf()).contentType(APPLICATION_JSON).content(change(replacement,number)))
      .andExpect(status().isOk()).andExpect(jsonPath("$.phone").value(replacement));
    String changeLog=jdbc.queryForObject("SELECT changes_json FROM audit_log WHERE action=? AND owner_username=? ORDER BY id DESC LIMIT 1",String.class,"ranking_student_phone_updated:s1",owner);
    assertThat(changeLog).contains("phone","before","after").doesNotContain(number,replacement,"123456");
    add("other-teacher","foreign","他人学生");
    mvc.perform(put("/api/rankings/admin/students/foreign/phone").session(session).with(csrf()).contentType(APPLICATION_JSON).content(change(phone(),""))).andExpect(status().isNotFound());
    mvc.perform(get("/api/rankings/admin/students/account-status").session(session)).andExpect(jsonPath("$.length()").value(2));
    grant("rankings.board.read");
    mvc.perform(get("/api/rankings/admin/students/s1/phone").session(session)).andExpect(status().isForbidden());
    mvc.perform(put("/api/rankings/admin/students/s1/phone").session(session).with(csrf()).contentType(APPLICATION_JSON).content(change(number,replacement))).andExpect(status().isForbidden());
    mvc.perform(multipart("/api/rankings/admin/imports/phones").file(file).session(session).with(csrf())).andExpect(status().isForbidden());
    mvc.perform(get("/api/rankings/admin/students/account-status")).andExpect(status().isUnauthorized());
  }
  @Test void importIsIdempotentAndKeepsPasswordPointsAndOwner() throws Exception {
    grant("rankings.accounts.import");var teacher=teacherLogin();String number=phone();
    var file=xlsx(new String[][]{{"用户ID","姓名","手机号"},{"s1","同学一",number}});
    mvc.perform(multipart("/api/rankings/admin/imports/phones").file(file).session(teacher).with(csrf())).andExpect(status().isOk()).andExpect(jsonPath("$.createdRows").value(1));
    String oldHash=jdbc.queryForObject("SELECT password_hash FROM ranking_student_accounts WHERE phone=?",String.class,number);
    assertThat(encoder.matches("123456",oldHash)).isTrue();
    mvc.perform(multipart("/api/rankings/admin/imports/phones").file(file).session(teacher).with(csrf())).andExpect(status().isOk()).andExpect(jsonPath("$.unchangedRows").value(1));
    var login=mvc.perform(post("/api/public/rankings/student-auth/login").with(csrf()).contentType(APPLICATION_JSON)
      .content("{\"phone\":\""+number+"\",\"password\":\"123456\"}"))
      .andExpect(status().isOk()).andExpect(jsonPath("$.ownerUsername").value(owner)).andReturn();
    var student=(MockHttpSession)login.getRequest().getSession(false);
    mvc.perform(post("/api/public/rankings/student-auth/change-password").session(student).with(csrf()).contentType(APPLICATION_JSON)
      .content("{\"oldPassword\":\"123456\",\"password\":\"changed-password\"}")).andExpect(status().isOk());
    String newHash=jdbc.queryForObject("SELECT password_hash FROM ranking_student_accounts WHERE phone=?",String.class,number);
    String replacement=phone();
    var replacementFile=xlsx(new String[][]{{"用户ID","姓名","手机号"},{"s1","同学一",replacement}});
    var before=jdbc.queryForList("SELECT * FROM ranking_students WHERE owner_username=?",owner);
    mvc.perform(multipart("/api/rankings/admin/imports/phones").file(replacementFile).session(teacher).with(csrf())).andExpect(status().isOk()).andExpect(jsonPath("$.updatedRows").value(1));
    assertThat(jdbc.queryForObject("SELECT password_hash FROM ranking_student_accounts WHERE phone=?",String.class,replacement)).isEqualTo(newHash);
    assertThat(jdbc.queryForList("SELECT * FROM ranking_students WHERE owner_username=?",owner)).isEqualTo(before);
    assertThat(jdbc.queryForObject("SELECT password_changed_at FROM ranking_student_accounts WHERE phone=?",java.sql.Timestamp.class,replacement)).isNotNull();
    mvc.perform(get("/api/public/rankings/student-auth/session").session(student)).andExpect(status().isUnauthorized());
    mvc.perform(post("/api/public/rankings/student-auth/login").with(csrf()).contentType(APPLICATION_JSON).content("{\"phone\":\""+number+"\",\"password\":\"changed-password\"}")).andExpect(status().isUnauthorized());
    mvc.perform(post("/api/public/rankings/student-auth/login").with(csrf()).contentType(APPLICATION_JSON).content("{\"phone\":\""+replacement+"\",\"password\":\"changed-password\"}")).andExpect(status().isOk());
    String detail=jdbc.queryForObject("SELECT detail_json FROM audit_log WHERE action LIKE 'ranking_student_phones_imported:%' ORDER BY id DESC LIMIT 1",String.class);
    assertThat(detail).contains("accountChanges","before","after","sha256").doesNotContain(number,replacement,"123456","changed-password",newHash);
  }
  @Test void partialFailuresDuplicatesAndConflictsAreExplicit() throws Exception {
    String used=phone();accounts.edit(owner,"s1",used,"");
    String other="other"+UUID.randomUUID().toString().substring(0,6);add(other,"foreign","其他学生");
    String occupied=phone();accounts.edit(other,"foreign",occupied,"");
    var rows=parser.parse(xlsx(new String[][]{{"用户ID","姓名","手机号"},
      {"s2","错误姓名",phone()},{"missing","不存在",phone()},{"s2","同学二",occupied},{"s2","同学二","123"}}));
    var result=accounts.importRows(owner,rows);
    assertThat(result.rejectedRows()).isEqualTo(4);
    assertThat(accounts.phone(owner,"s2").status()).isEqualTo("PENDING");
    var mismatch=accounts.importRows(owner,List.of(new StudentPhoneXlsxParser.PhoneRow(2,"s2","错误姓名",phone(),null)));
    assertThat(mismatch.errors().getFirst().message()).contains("姓名");
    var missing=accounts.importRows(owner,List.of(new StudentPhoneXlsxParser.PhoneRow(2,"foreign","其他学生",phone(),null)));
    assertThat(missing.errors().getFirst().message()).contains("当前老师");
    var duplicate=accounts.importRows(owner,parser.parse(xlsx(new String[][]{{"用户ID","姓名","手机号"},{"s1","同学一",used},{"s2","同学二",used}})));
    assertThat(duplicate.rejectedRows()).isZero();
    assertThatThrownBy(()->accounts.edit(owner,"s1",phone(),"stale")).hasMessageContaining("手机号已被其他操作修改");
    assertThat(accounts.phone(owner,"s1").phone()).isEqualTo(used);
    assertThat(accounts.edit(owner,"s2",occupied,used).phone()).isEqualTo(occupied);
  }
  @Test void concurrentClaimsForSamePhoneCannotCreateTwoAccounts() throws Exception {
    String other="race"+UUID.randomUUID().toString().substring(0,6);add(other,"foreign","并发学生");String number=phone();
    var pool=Executors.newFixedThreadPool(2);var start=new CountDownLatch(1);
    try {
      var tasks=List.<Callable<Boolean>>of(()->{start.await();try{accounts.edit(owner,"s1",number,"");return true;}catch(org.springframework.web.server.ResponseStatusException e){return false;}},
        ()->{start.await();try{accounts.edit(other,"foreign",number,"");return true;}catch(org.springframework.web.server.ResponseStatusException e){return false;}});
      var futures=tasks.stream().map(pool::submit).toList();start.countDown();int successes=0;
      for(var future:futures)if(future.get(15,TimeUnit.SECONDS))successes++;
      assertThat(successes).isEqualTo(2);
      assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ranking_student_accounts WHERE phone=?",Integer.class,number)).isEqualTo(2);
    } finally {pool.shutdownNow();}
  }
  @Test void sharedPhoneRequiresStudentIdAndDoesNotDiscloseOtherNames() throws Exception {
    String shared=phone();accounts.edit(owner,"s1",shared,"");accounts.edit(owner,"s2",shared,"");
    var result=mvc.perform(post("/api/public/rankings/student-auth/login").with(csrf()).contentType(APPLICATION_JSON).content("{\"phone\":\""+shared+"\",\"password\":\"123456\"}")).andExpect(status().isConflict()).andReturn();
    assertThat(result.getResponse().getContentAsString()).doesNotContain("同学一","同学二");
    mvc.perform(post("/api/public/rankings/student-auth/login").with(csrf()).contentType(APPLICATION_JSON).content("{\"phone\":\""+shared+"\",\"password\":\"123456\",\"studentId\":\"s2\"}")).andExpect(status().isOk()).andExpect(jsonPath("$.studentId").value("s2"));
  }
  @Test void parserAcceptsNumericPhonesAndRejectsWrongHeadersAndFormulas() throws Exception {
    try(var workbook=new XSSFWorkbook();var output=new ByteArrayOutputStream()) {
      var sheet=workbook.createSheet();var h=sheet.createRow(0);h.createCell(0).setCellValue("用户ID");h.createCell(1).setCellValue("姓名");h.createCell(2).setCellValue("手机号");
      var r=sheet.createRow(1);r.createCell(0).setCellValue(11111111);r.createCell(1).setCellValue("测试同学");r.createCell(2).setCellValue(17785890327d);
      var bad=sheet.createRow(2);bad.createCell(0).setCellValue("formula");bad.createCell(1).setCellValue("测试");bad.createCell(2).setCellFormula("1+1");
      workbook.write(output);
      var rows=parser.parse(new MockMultipartFile("file","numeric.xlsx","application/octet-stream",output.toByteArray()));
      assertThat(rows.getFirst().studentId()).isEqualTo("11111111");assertThat(rows.getFirst().phone()).isEqualTo("17785890327");
      assertThat(rows.get(1).error()).contains("公式");
    }
    assertThatThrownBy(()->parser.parse(xlsx(new String[][]{{"手机号","姓名","ID"},{phone(),"测试","s1"}}))).hasMessageContaining("A、B、C");
  }

  @Test void legacyStudentEndpointsRequireStudentSession() throws Exception {
    for (String path : List.of("/catalog", "?campId=camp&scope=camp&teacherId=arbitrary", "/all", "/rewards", "/announcement", "/rewards/1/image"))
      mvc.perform(get("/api/public/rankings"+path)).andExpect(status().isUnauthorized());
  }
  @Test void bootstrapRequiresTeacherPermissionAndCsrf() throws Exception {
    String body="{\"crmTeacherId\":\"29413\",\"deviceName\":\"untrusted\"}";
    mvc.perform(post("/api/public/rankings/extension/bootstrap").with(csrf()).contentType(APPLICATION_JSON).content(body))
      .andExpect(status().isUnauthorized());
    var session=teacherLogin();
    mvc.perform(post("/api/public/rankings/extension/bootstrap").session(session).with(csrf()).contentType(APPLICATION_JSON).content(body))
      .andExpect(status().isForbidden());
    grant("rankings.devices.pair");
    mvc.perform(post("/api/public/rankings/extension/bootstrap").session(session).contentType(APPLICATION_JSON).content(body))
      .andExpect(status().isForbidden());
  }
  @Test void defaultPasswordCannotBeAcceptedAsChanged() throws Exception {
    String phone=phone();accounts.edit(owner,"s1",phone,"");
    var session=(MockHttpSession)mvc.perform(post("/api/public/rankings/student-auth/login").with(csrf()).contentType(APPLICATION_JSON)
      .content("{\"phone\":\""+phone+"\",\"password\":\"123456\"}"))
      .andExpect(status().isOk()).andReturn().getRequest().getSession(false);
    mvc.perform(post("/api/public/rankings/student-auth/change-password").session(session).with(csrf()).contentType(APPLICATION_JSON)
      .content("{\"oldPassword\":\"123456\",\"password\":\"123456\"}")).andExpect(status().isUnprocessableEntity());
    assertThat(studentAuth.current(new org.springframework.mock.web.MockHttpServletRequest() {{setSession(session);}}).mustChangePassword()).isTrue();
  }


  @Test void administratorResetToInitialPasswordPreservesReminder() {
    String number=phone();accounts.edit(owner,"s1",number,"");
    studentAuth.resetPassword(owner,"s1","another-password");
    assertThat(jdbc.queryForObject("SELECT password_changed_at FROM ranking_student_accounts WHERE owner_username=? AND student_id='s1'",java.sql.Timestamp.class,owner)).isNotNull();
    studentAuth.resetPassword(owner,"s1","123456");
    assertThat(jdbc.queryForObject("SELECT password_changed_at FROM ranking_student_accounts WHERE owner_username=? AND student_id='s1'",java.sql.Timestamp.class,owner)).isNull();
  }
 @Test void recoveryRequestOnlyTargetsCurrentOwnerAfterConflictResolution(){String id="recover"+UUID.randomUUID().toString().substring(0,8),other="old"+UUID.randomUUID().toString().substring(0,8),number=phone();add(owner,id,"归属核实学员");add(other,id,"归属核实学员");accounts.edit(owner,id,number,"");accounts.edit(other,id,number,"");jdbc.update("INSERT INTO ranking_current_ownership(student_id,owner_username,state) VALUES(?,?,'ACTIVE')",id,owner);aftercare.requestRecovery(number,id,"找回");assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ranking_recovery_requests WHERE student_id=? AND owner_username=?",Integer.class,id,owner)).isEqualTo(1);assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ranking_recovery_requests WHERE student_id=? AND owner_username=?",Integer.class,id,other)).isZero();}

  @Test void studentPasswordChangesAreLimitedByBeijingCalendarMonthButAdminResetsAreNot() throws Exception {
    String number=phone(); accounts.edit(owner,"s1",number,"");
    var session=(MockHttpSession)mvc.perform(post("/api/public/rankings/student-auth/login").with(csrf()).contentType(APPLICATION_JSON)
      .content("{\"phone\":\""+number+"\",\"password\":\"123456\"}"))
      .andExpect(status().isOk()).andExpect(jsonPath("$.canChangePassword").value(true)).andReturn().getRequest().getSession(false);
    String url="/api/public/rankings/student-auth/change-password";
    mvc.perform(post(url).contentType(APPLICATION_JSON).with(csrf()).content("{\"oldPassword\":\"123456\",\"password\":\"new-password\"}"))
      .andExpect(status().isUnauthorized());
    mvc.perform(post(url).session(session).contentType(APPLICATION_JSON).content("{\"oldPassword\":\"123456\",\"password\":\"new-password\"}"))
      .andExpect(status().isForbidden());
    mvc.perform(post(url).session(session).with(csrf()).contentType(APPLICATION_JSON).content("{\"oldPassword\":\"123456\",\"password\":\"new-password\"}"))
      .andExpect(status().isOk()).andExpect(jsonPath("$.canChangePassword").value(false))
      .andExpect(jsonPath("$.nextPasswordChangeDate").value(java.time.LocalDate.now(java.time.ZoneId.of("Asia/Shanghai")).withDayOfMonth(1).plusMonths(1).toString()));
    String hash=jdbc.queryForObject("SELECT password_hash FROM ranking_student_accounts WHERE owner_username=? AND student_id='s1'",String.class,owner);
    mvc.perform(post(url).session(session).with(csrf()).contentType(APPLICATION_JSON).content("{\"oldPassword\":\"new-password\",\"password\":\"second-password\"}"))
      .andExpect(status().isConflict());
    assertThat(jdbc.queryForObject("SELECT password_hash FROM ranking_student_accounts WHERE owner_username=? AND student_id='s1'",String.class,owner)).isEqualTo(hash);
    // The instant immediately before Beijing month start belongs to the previous month.
    var boundary=java.time.LocalDate.now(java.time.ZoneId.of("Asia/Shanghai")).withDayOfMonth(1).atStartOfDay(java.time.ZoneId.of("Asia/Shanghai")).toInstant();
    jdbc.update("UPDATE ranking_student_accounts SET password_changed_at=? WHERE owner_username=? AND student_id='s1'",java.sql.Timestamp.from(boundary.minusSeconds(1)),owner);
    mvc.perform(get("/api/public/rankings/student-auth/session").session(session)).andExpect(jsonPath("$.canChangePassword").value(true));
    mvc.perform(post(url).session(session).with(csrf()).contentType(APPLICATION_JSON).content("{\"oldPassword\":\"new-password\",\"password\":\"next-month-password\"}"))
      .andExpect(status().isOk());
    studentAuth.resetPassword(owner,"s1","temporary-one");
    studentAuth.resetPassword(owner,"s1","temporary-two");
    mvc.perform(get("/api/public/rankings/student-auth/session").session(session)).andExpect(status().isUnauthorized());
    var fresh=(MockHttpSession)mvc.perform(post("/api/public/rankings/student-auth/login").with(csrf()).contentType(APPLICATION_JSON)
      .content("{\"phone\":\""+number+"\",\"password\":\"temporary-two\"}"))
      .andExpect(status().isOk()).andExpect(jsonPath("$.canChangePassword").value(true)).andReturn().getRequest().getSession(false);
    mvc.perform(post(url).session(fresh).with(csrf()).contentType(APPLICATION_JSON).content("{\"oldPassword\":\"temporary-two\",\"password\":\"after-reset-password\"}"))
      .andExpect(status().isOk()).andExpect(jsonPath("$.canChangePassword").value(false));
    String auditText=jdbc.queryForObject("SELECT changes_json FROM audit_log WHERE action='student_password_changed' AND owner_username=? ORDER BY id DESC LIMIT 1",String.class,owner);
    assertThat(auditText).contains("credentialState").doesNotContain("after-reset-password",hash);
  }

  @Test void concurrentStudentPasswordChangesOnlyCommitOnce() throws Exception {
    String number=phone();accounts.edit(owner,"s1",number,"");
    var first=(MockHttpSession)mvc.perform(post("/api/public/rankings/student-auth/login").with(csrf()).contentType(APPLICATION_JSON)
      .content("{\"phone\":\""+number+"\",\"password\":\"123456\"}"))
      .andExpect(status().isOk()).andReturn().getRequest().getSession(false);
    var second=(MockHttpSession)mvc.perform(post("/api/public/rankings/student-auth/login").with(csrf()).contentType(APPLICATION_JSON)
      .content("{\"phone\":\""+number+"\",\"password\":\"123456\"}"))
      .andExpect(status().isOk()).andReturn().getRequest().getSession(false);
    var start=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
    try {
      var futures=new ArrayList<Future<Integer>>();
      for(var session:List.of(first,second)) futures.add(pool.submit(()->{
        start.await();return mvc.perform(post("/api/public/rankings/student-auth/change-password").session(session).with(csrf())
          .contentType(APPLICATION_JSON).content("{\"oldPassword\":\"123456\",\"password\":\"concurrent-secret\"}"))
          .andReturn().getResponse().getStatus();
      }));
      start.countDown();var results=new ArrayList<Integer>();for(var f:futures)results.add(f.get(20,TimeUnit.SECONDS));
      assertThat(results.stream().filter(code->code==200).count()).isEqualTo(1);
      assertThat(results).allMatch(code->code==200||code==401||code==409);
      assertThat(jdbc.queryForObject("SELECT session_version FROM ranking_student_accounts WHERE owner_username=? AND student_id='s1'",Integer.class,owner)).isEqualTo(1);
      assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_log WHERE action='student_password_changed' AND owner_username=?",Integer.class,owner)).isEqualTo(1);
    } finally {pool.shutdownNow();}
  }

  @Test void changingPasswordRequiresCorrectOldPasswordAndFailureDoesNotConsumeQuota() throws Exception {
    String number=phone();accounts.edit(owner,"s1",number,"");
    var session=(MockHttpSession)mvc.perform(post("/api/public/rankings/student-auth/login").with(csrf()).contentType(APPLICATION_JSON)
      .content("{\"phone\":\""+number+"\",\"password\":\"123456\"}"))
      .andExpect(status().isOk()).andReturn().getRequest().getSession(false);
    String url="/api/public/rankings/student-auth/change-password";
    String hash=jdbc.queryForObject("SELECT password_hash FROM ranking_student_accounts WHERE owner_username=? AND student_id='s1'",String.class,owner);
    for(String body:List.of("{\"password\":\"updated-secret\"}","{\"oldPassword\":\"\",\"password\":\"updated-secret\"}"))
      mvc.perform(post(url).session(session).with(csrf()).contentType(APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
    mvc.perform(post(url).session(session).with(csrf()).contentType(APPLICATION_JSON)
      .content("{\"oldPassword\":\"wrong-secret\",\"password\":\"updated-secret\"}"))
      .andExpect(status().isUnprocessableEntity());
    assertThat(jdbc.queryForObject("SELECT password_hash FROM ranking_student_accounts WHERE owner_username=? AND student_id='s1'",String.class,owner)).isEqualTo(hash);
    assertThat(jdbc.queryForObject("SELECT password_changed_at FROM ranking_student_accounts WHERE owner_username=? AND student_id='s1'",java.sql.Timestamp.class,owner)).isNull();
    mvc.perform(get("/api/public/rankings/student-auth/session").session(session)).andExpect(status().isOk()).andExpect(jsonPath("$.canChangePassword").value(true));
    mvc.perform(post(url).session(session).with(csrf()).contentType(APPLICATION_JSON)
      .content("{\"oldPassword\":\"123456\",\"password\":\"updated-secret\"}"))
      .andExpect(status().isOk()).andExpect(jsonPath("$.canChangePassword").value(false));
    assertThat(encoder.matches("updated-secret",jdbc.queryForObject("SELECT password_hash FROM ranking_student_accounts WHERE owner_username=? AND student_id='s1'",String.class,owner))).isTrue();
    var logs=jdbc.queryForList("SELECT changes_json,detail_json,error_message FROM audit_log WHERE owner_username=?",owner).toString();
    assertThat(logs).doesNotContain("wrong-secret","updated-secret",hash,"123456");
  }

}
