package cn.codedog.service;
import cn.codedog.model.RankingPayload;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.server.ResponseStatusException;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class StudentStyleServiceTest {
  JdbcTemplate jdbc;
  StudentRankingAuthService auth;
  AuditService audit;
  StudentStyleService service;
  MockHttpServletRequest request=new MockHttpServletRequest();
  RankingPayload.Entry entry;
  @BeforeEach void setup(){
    jdbc=new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:style"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1","sa",""));
    jdbc.execute("CREATE TABLE ranking_student_styles(owner_username VARCHAR(50),student_id VARCHAR(100),avatar_frame VARCHAR(24),title_code VARCHAR(24),background_code VARCHAR(24),updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,PRIMARY KEY(owner_username,student_id))");
    auth=mock(StudentRankingAuthService.class);audit=mock(AuditService.class);service=new StudentStyleService(jdbc,auth,audit,mock(RankingAnnouncementBroadcaster.class));
    when(auth.current(request)).thenReturn(new StudentRankingAuthService.StudentSession("teacherA","s1","学生一","19900000000",false));
    entry=mock(RankingPayload.Entry.class);when(entry.studentId()).thenReturn("s1");when(entry.totalPoints()).thenReturn(0);
    var board=mock(RankingPayload.Board.class);when(board.rankings()).thenReturn(List.of(entry));when(auth.board(request)).thenReturn(board);
  }
  @Test void defaultsAndLockedChoicesAreEnforced(){
    assertThat(service.read(request).selection().frame()).isEqualTo("base");
    assertThatThrownBy(()->service.save(new StudentStyleService.Selection("nova","rookie","grid"),request)).isInstanceOf(ResponseStatusException.class);
    assertThatThrownBy(()->service.save(new StudentStyleService.Selection("hacked","rookie","grid"),request)).isInstanceOf(ResponseStatusException.class);
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ranking_student_styles",Integer.class)).isZero();
  }
  @Test void savesOwnChoicesAndAuditsBeforeAfterWithoutSpending(){
    when(entry.totalPoints()).thenReturn(1800);
    var first=new StudentStyleService.Selection("nova","architect","orbit");
    assertThat(service.save(first,request).selection()).isEqualTo(first);
    assertThat(service.read(request).selection()).isEqualTo(first);
    var next=new StudentStyleService.Selection("cyan","solver","aurora");
    service.save(next,request);
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ranking_student_styles",Integer.class)).isEqualTo(1);
    verify(audit).change(eq("STUDENT_STYLE"),eq("s1"),eq(Map.of("frame","nova","title","architect","background","orbit")),eq(Map.of("frame","cyan","title","solver","background","aurora")));
    verify(audit,atLeast(2)).owner("teacherA");
  }
  @Test void tenantAndStudentCannotReadOthersStyle(){
    jdbc.update("INSERT INTO ranking_student_styles(owner_username,student_id,avatar_frame,title_code,background_code) VALUES('teacherB','s1','nova','architect','orbit'),('teacherA','s2','nova','architect','orbit')");
    when(entry.totalPoints()).thenReturn(1800);
    assertThat(service.read(request).selection().frame()).isEqualTo("base");
    service.save(new StudentStyleService.Selection("cyan","rookie","grid"),request);
    assertThat(jdbc.queryForObject("SELECT avatar_frame FROM ranking_student_styles WHERE owner_username='teacherB' AND student_id='s1'",String.class)).isEqualTo("nova");
  }
  @Test void downgradedLevelNormalizesDisplayAndRejectsLockedSave(){
    when(entry.totalPoints()).thenReturn(1800);service.save(new StudentStyleService.Selection("nova","architect","orbit"),request);
    when(entry.totalPoints()).thenReturn(0);
    assertThat(service.read(request).selection()).isEqualTo(new StudentStyleService.Selection("base","rookie","grid"));
    assertThatThrownBy(()->service.save(new StudentStyleService.Selection("nova","architect","orbit"),request)).isInstanceOf(ResponseStatusException.class);
  }
  @Test void invalidSessionIsDeniedBeforeDataAccess(){
    when(auth.current(request)).thenThrow(new ResponseStatusException(org.springframework.http.HttpStatus.UNAUTHORIZED));
    assertThatThrownBy(()->service.read(request)).isInstanceOf(ResponseStatusException.class);
    assertThatThrownBy(()->service.save(new StudentStyleService.Selection("base","rookie","grid"),request)).isInstanceOf(ResponseStatusException.class);
    verifyNoInteractions(audit);
  }
}
