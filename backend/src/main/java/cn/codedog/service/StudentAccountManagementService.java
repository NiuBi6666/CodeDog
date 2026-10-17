package cn.codedog.service;

import java.util.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.ConcurrencyFailureException;
import java.util.function.Supplier;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

@Service
public class StudentAccountManagementService {
  private final JdbcTemplate jdbc;
  private final PasswordEncoder passwords;
  private final PasswordRecoveryCipher recovery;
  private final AuditService audit;
  private final TransactionTemplate transactions;
  public StudentAccountManagementService(JdbcTemplate jdbc, PasswordEncoder passwords, PasswordRecoveryCipher recovery,
      AuditService audit, PlatformTransactionManager transactionManager) {
    this.jdbc=jdbc; this.passwords=passwords; this.recovery=recovery; this.audit=audit;
    this.transactions=new TransactionTemplate(transactionManager);
  }
  public record AccountStatus(String studentId,String status,String maskedPhone) {}
  public record PhoneInfo(String studentId,String studentName,String phone,String status) {}
  public record RowError(int rowNumber,String studentId,String message) {}
  public record ImportResult(String batchId,int receivedRows,int createdRows,int updatedRows,int unchangedRows,
      int rejectedRows,List<RowError> errors) {}
  private record Account(String phone,boolean enabled) {}
  private record Saved(PhoneInfo info,String previousPhone,String previousStatus,String outcome) {}

  public List<AccountStatus> statuses(String owner) {
    return jdbc.query("""
      SELECT DISTINCT s.student_id,a.phone,a.enabled FROM ranking_students s
      LEFT JOIN ranking_student_accounts a ON a.owner_username=s.owner_username AND a.student_id=s.student_id
      WHERE s.owner_username=? ORDER BY s.student_id
      """, (rs,n)->new AccountStatus(rs.getString(1),status(rs.getString(2),rs.getBoolean(3)),mask(rs.getString(2))),owner);
  }
  public PhoneInfo phone(String owner,String studentId) {
    String name=studentNames(owner,studentId,false).stream().findFirst().orElseThrow(()->missing());
    Account account=account(owner,studentId,false);
    return new PhoneInfo(studentId,name,account==null?"":account.phone(),account==null?"PENDING":status(account.phone(),account.enabled()));
  }
  public PhoneInfo edit(String owner,String studentId,String phone,String expectedPhone) {
    if(expectedPhone==null)throw invalid("缺少原手机号，请重新打开修改窗口");
    Saved result=save(owner,studentId,null,phone,expectedPhone,()->passwords.encode("123456"));
    record(result);
    return result.info();
  }
  public ImportResult importRows(String owner,List<StudentPhoneXlsxParser.PhoneRow> rows) {
    String batchId=UUID.randomUUID().toString();
    // All new accounts have the same temporary password. Hash it once per batch to keep large imports responsive;
    // individual password changes still receive a fresh BCrypt salt, and recovery ciphertext has a unique nonce.
    String[] batchHash={null};
    Supplier<String> initialHash=()->{if(batchHash[0]==null)batchHash[0]=passwords.encode("123456");return batchHash[0];};
    Map<String,Long> ids=new HashMap<>(),phones=new HashMap<>();
    rows.stream().filter(r->r.error()==null).forEach(r->{ids.merge(r.studentId(),1L,Long::sum);phones.merge(r.phone(),1L,Long::sum);});
    List<RowError> errors=new ArrayList<>(); List<Map<String,Object>> changes=new ArrayList<>(); int created=0,updated=0,unchanged=0;
    for(var row:rows) {
      String error=row.error();
      if(error==null && ids.get(row.studentId())>1)error="表内学生 ID 重复，请保留一行后重试";
      if(error==null && phones.get(row.phone())>1)error="表内手机号重复，不能绑定多位学生";
      if(error!=null){errors.add(new RowError(row.rowNumber(),row.studentId(),error));continue;}
      try {
        Saved result=save(owner,row.studentId(),row.studentName(),row.phone(),null,initialHash);
        if(!"UNCHANGED".equals(result.outcome())) changes.add(Map.of("rowNumber",row.rowNumber(),"studentId",row.studentId(),"outcome",result.outcome(),
          "before",Map.of("phone",result.previousPhone(),"accountStatus",result.previousStatus()),
          "after",Map.of("phone",result.info().phone(),"accountStatus",result.info().status())));
        switch(result.outcome()) {case "CREATED"->created++;case "UPDATED"->updated++;default->unchanged++;}
      } catch(ResponseStatusException e) {errors.add(new RowError(row.rowNumber(),row.studentId(),e.getReason()));}
    }
    audit.detail(Map.of("accountChanges",changes));
    return new ImportResult(batchId,rows.size(),created,updated,unchanged,errors.size(),List.copyOf(errors));
  }
  private Saved save(String owner,String id,String expectedName,String phoneValue,String expectedPhone,Supplier<String> initialHash) {
    String phone=phoneValue==null?"":phoneValue.trim();
    if(!phone.matches("^1\\d{10}$"))throw invalid("请输入 11 位有效手机号");
    try {
      return transactions.execute(tx->{
        // Lock all class memberships for this owner/student before opening or changing an account.
        List<String> names=studentNames(owner,id,true);
        if(names.isEmpty())throw missing();
        if(expectedName!=null&&!names.contains(expectedName))throw invalid("姓名与当前老师名下学生 ID 不匹配");
        String name=expectedName==null?names.getFirst():expectedName;
        Account before=account(owner,id,true);
        String previous=before==null?"":before.phone();
        String previousStatus=before==null?"PENDING":status(before.phone(),before.enabled());
        if(expectedPhone!=null&&!Objects.equals(expectedPhone,previous))
          throw new ResponseStatusException(HttpStatus.CONFLICT,"手机号已被其他操作修改，请重新打开窗口");
        if(before!=null&&previous.equals(phone))return new Saved(new PhoneInfo(id,name,phone,previousStatus),previous,previousStatus,"UNCHANGED");
        Integer occupied=jdbc.queryForObject("SELECT COUNT(*) FROM ranking_student_accounts WHERE phone=?",Integer.class,phone);
        if(occupied!=null&&occupied>0)throw new ResponseStatusException(HttpStatus.CONFLICT,"手机号已被其他学生使用，请核实后再修改");
        if(before==null) {
          String ciphertext=recovery.configured()?recovery.encrypt("123456","student:"+owner+":"+id):null;
          jdbc.update("INSERT INTO ranking_student_accounts(phone,owner_username,student_id,student_name,password_hash,password_ciphertext,enabled) VALUES(?,?,?,?,?,?,TRUE)",
            phone,owner,id,name,initialHash.get(),ciphertext);
        } else {
          // Keep password, initial-password reminder, enabled state, points, and teacher ownership intact.
          jdbc.update("UPDATE ranking_student_accounts SET phone=?,updated_at=CURRENT_TIMESTAMP(6) WHERE owner_username=? AND student_id=?",phone,owner,id);
        }
        return new Saved(new PhoneInfo(id,name,phone,before==null?"ACTIVE":previousStatus),previous,previousStatus,before==null?"CREATED":"UPDATED");
      });
    } catch(ConcurrencyFailureException e) {
      throw new ResponseStatusException(HttpStatus.CONFLICT,"账号正在被其他操作修改，请稍后重试");
    } catch(DataIntegrityViolationException e) {
      // The unique phone and owner/student keys are the final guard against concurrent imports/edits.
      throw new ResponseStatusException(HttpStatus.CONFLICT,"手机号或账号已被其他操作占用，请刷新后重试");
    }
  }
  private List<String> studentNames(String owner,String id,boolean lock) {
    return jdbc.query("SELECT student_name FROM ranking_students WHERE owner_username=? AND student_id=? ORDER BY camp_id,class_id"+(lock?" FOR UPDATE":""),
      (rs,n)->rs.getString(1).trim(),owner,id);
  }
  private Account account(String owner,String id,boolean lock) {
    return jdbc.query("SELECT phone,enabled FROM ranking_student_accounts WHERE owner_username=? AND student_id=?"+(lock?" FOR UPDATE":""),
      (rs,n)->new Account(rs.getString(1),rs.getBoolean(2)),owner,id).stream().findFirst().orElse(null);
  }
  private void record(Saved saved) {
    if("UNCHANGED".equals(saved.outcome()))return;
    audit.change("STUDENT_ACCOUNT",saved.info().studentId(),Map.of("phone",saved.previousPhone(),"accountStatus",saved.previousStatus()),
      Map.of("phone",saved.info().phone(),"accountStatus",saved.info().status()));
  }
  private String status(String phone,boolean enabled) { return phone==null?"PENDING":enabled?"ACTIVE":"DISABLED"; }
  private String mask(String phone) { return phone==null?"":phone.substring(0,3)+"****"+phone.substring(phone.length()-4); }
  private ResponseStatusException missing() { return new ResponseStatusException(HttpStatus.NOT_FOUND,"学生不在当前老师名下，请先导入该学生的积分名单"); }
  private ResponseStatusException invalid(String message) { return new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,message); }
}
