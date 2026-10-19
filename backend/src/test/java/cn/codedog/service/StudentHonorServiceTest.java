package cn.codedog.service;
import cn.codedog.model.RankingPayload;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class StudentHonorServiceTest {
 JdbcTemplate jdbc;StudentRankingAuthService auth;RankingBoardService boards;AuditService audit;
 StudentStyleService styles;StudentHonorService service;TransactionTemplate tx;
 MockHttpServletRequest request=new MockHttpServletRequest();
 @BeforeEach void setup(){
  var source=new DriverManagerDataSource("jdbc:h2:mem:honor"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000","sa","");
  jdbc=new JdbcTemplate(source);tx=new TransactionTemplate(new DataSourceTransactionManager(source));
  jdbc.execute("CREATE TABLE ranking_student_styles(owner_username VARCHAR(50),student_id VARCHAR(100),avatar_frame VARCHAR(24),title_code VARCHAR(24),background_code VARCHAR(24),updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,PRIMARY KEY(owner_username,student_id))");
  jdbc.execute("CREATE TABLE ranking_student_honor_progress(owner_username VARCHAR(50),student_id VARCHAR(100),highest_announced_tier INT DEFAULT 1,updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,PRIMARY KEY(owner_username,student_id))");
  auth=mock(StudentRankingAuthService.class);boards=mock(RankingBoardService.class);audit=mock(AuditService.class);
  styles=new StudentStyleService(jdbc,auth,audit,mock(RankingAnnouncementBroadcaster.class));service=new StudentHonorService(auth,boards,styles,jdbc,audit);
  when(auth.current(request)).thenReturn(new StudentRankingAuthService.StudentSession("teacherA","s11","我","19900000000",false));
  setRows(java.util.stream.IntStream.range(0,12).mapToObj(i->entry("s"+i,i+1,2200-i*10,120-i*5)).toList());
 }
 RankingPayload.Entry entry(String id,int rank,int xp,int balance){return new RankingPayload.Entry(rank,id,id,"c","班级",xp,0,0,0,1,HonorLevels.of(xp).level(),HonorLevels.of(xp).name(),Instant.parse("2026-10-19T00:00:00Z"),0.0,null,0,"NEW",balance,0,Math.max(0,xp-balance),"camp");}
 void setRows(List<RankingPayload.Entry> rows){var board=new RankingPayload.Board("camp","营期","c","班级","all",rows.size(),Instant.parse("2026-10-19T00:00:00Z"),null,rows);when(boards.allBoardForOwner("teacherA")).thenReturn(board);when(auth.board(request)).thenReturn(board);}
 @Test void onlyTopTenAndSelfStylesAreReturnedWithoutPrivateBackgrounds() throws Exception {
  jdbc.update("INSERT INTO ranking_student_styles(owner_username,student_id,avatar_frame,title_code,background_code) VALUES('teacherA','s0','regal','sovereign','orbit'),('teacherB','s0','base','rookie','grid'),('teacherA','s10','regal','sovereign','aurora')");
  var view=service.board(request);
  assertThat(view.board().rankings()).hasSize(11);assertThat(view.styles()).hasSize(11);assertThat(view.styles()).doesNotContainKey("s10");
  assertThat(view.styles().get("s0").frame()).isEqualTo("regal");assertThat(view.styles().get("s0").titleName()).isEqualTo("巅峰王者");
  var json=new ObjectMapper().findAndRegisterModules().valueToTree(view);
  assertThat(json.has("rankings")).isTrue();assertThat(json.has("board")).isFalse();
  assertThat(json.get("styles").toString()).doesNotContain("background","teacherB","phone","orbit");
  assertThat(view.motivation()).contains("6 积分");verify(boards).allBoardForOwner("teacherA");
 }
 @Test void spendingDoesNotLowerEarnedHonorOrUnlocks(){
  setRows(List.of(entry("s11",1,2200,0)));var view=service.board(request);
  assertThat(view.myHonor().level()).isEqualTo(7);assertThat(view.myHonor().experience()).isEqualTo(2200);
  assertThat(styles.read(request).level()).isEqualTo(7);assertThat(view.motivation()).contains("榜首");
 }
 @Test void unearnedLockedEquipmentIsNotPublished(){
  jdbc.update("INSERT INTO ranking_student_styles(owner_username,student_id,avatar_frame,title_code,background_code) VALUES('teacherA','s11','regal','sovereign','orbit')");
  setRows(List.of(entry("s11",1,0,9999)));var view=service.board(request);
  assertThat(view.myHonor().level()).isEqualTo(1);assertThat(view.styles().get("s11").frame()).isEqualTo("base");
 }
 @Test void promotionAnnouncesOnceAndNeverRepeatsAfterReloadOrCorrection(){
  assertThat(service.board(request).promotionDue()).isTrue();assertThat(tx.execute(s->service.claim(request)).celebrate()).isTrue();
  assertThat(service.board(request).promotionDue()).isFalse();assertThat(tx.execute(s->service.claim(request)).celebrate()).isFalse();
  setRows(List.of(entry("s11",1,0,0)));assertThat(tx.execute(s->service.claim(request)).celebrate()).isFalse();
  setRows(List.of(entry("s11",1,2090,0)));assertThat(tx.execute(s->service.claim(request)).celebrate()).isFalse();
  setRows(List.of(entry("s11",1,2200,0)));assertThat(tx.execute(s->service.claim(request)).celebrate()).isTrue();
  assertThat(tx.execute(s->service.claim(request)).celebrate()).isFalse();
 }
 @Test void concurrentTabsClaimOnlyOnePromotion() throws Exception {
  var pool=Executors.newFixedThreadPool(6);
  try{List<Callable<Boolean>> jobs=java.util.stream.IntStream.range(0,6).mapToObj(i->(Callable<Boolean>)()->tx.execute(s->service.claim(request)).celebrate()).toList();int count=0;for(var future:pool.invokeAll(jobs))if(future.get())count++;assertThat(count).isEqualTo(1);}finally{pool.shutdownNow();}
 }
 @Test void invalidSessionCannotReadBoardOrClaim(){
  when(auth.current(request)).thenThrow(new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.UNAUTHORIZED));
  assertThatThrownBy(()->service.board(request)).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
  assertThatThrownBy(()->service.claim(request)).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);verifyNoInteractions(audit);
 }
}
