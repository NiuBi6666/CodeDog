package cn.codedog.service;

import cn.codedog.model.RankingPayload;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.sql.Timestamp;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class StudentRankingAuthService {
  public static final String OWNER_ATTRIBUTE = "rankingStudentOwner";
  public static final String STUDENT_ID_ATTRIBUTE = "rankingStudentId";
  public static final String STUDENT_NAME_ATTRIBUTE = "rankingStudentName";
  public static final String PHONE_ATTRIBUTE = "rankingStudentPhone";

  private final JdbcTemplate jdbc;
  private final PasswordEncoder passwordEncoder;
  private final AuditService audit;
  private final RankingBoardService rankings;
  private final PasswordRecoveryCipher passwordRecovery;

  public StudentRankingAuthService(JdbcTemplate jdbc, PasswordEncoder passwordEncoder,
                                   AuditService audit, RankingBoardService rankings,
                                   PasswordRecoveryCipher passwordRecovery) {
    this.jdbc = jdbc;
    this.passwordEncoder = passwordEncoder;
    this.audit = audit;
    this.rankings = rankings;
    this.passwordRecovery = passwordRecovery;
  }

  public StudentSession login(String phoneValue, String password, HttpServletRequest request) {
    if (audit.recentStudentLoginFailures(request) >= 8)
      throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "登录尝试过于频繁，请 15 分钟后再试");
    String phone = normalizePhone(phoneValue);
    String secret = password == null ? "" : password;
    Account account = find(phone);
    if (account == null || !account.enabled() || !passwordEncoder.matches(secret, account.passwordHash())) {
      audit.record("student_login_failed", request);
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "手机号或密码不正确");
    }
    if ((account.passwordCiphertext() == null || account.passwordCiphertext().isBlank()) && passwordRecovery.configured()) {
      jdbc.update("UPDATE ranking_student_accounts SET password_ciphertext=? WHERE phone=? AND owner_username=? AND student_id=?",
        passwordRecovery.encrypt(secret, studentPasswordContext(account.ownerUsername(), account.studentId())),
        account.phone(), account.ownerUsername(), account.studentId());
    }
    HttpSession session = request.getSession(true);
    request.changeSessionId();
    put(session, account);
    audit.record("student_login_succeeded", request);
    return profile(account);
  }

  public StudentSession current(HttpServletRequest request) {
    HttpSession session = request.getSession(false);
    if (session == null) throw unauthorized();
    String phone = value(session.getAttribute(PHONE_ATTRIBUTE));
    String owner = value(session.getAttribute(OWNER_ATTRIBUTE));
    String studentId = value(session.getAttribute(STUDENT_ID_ATTRIBUTE));
    if (phone.isEmpty() || owner.isEmpty() || studentId.isEmpty()) throw unauthorized();
    Account account = find(phone, owner, studentId);
    if (account == null || !account.enabled()) {
      clear(session);
      throw unauthorized();
    }
    session.setAttribute(STUDENT_NAME_ATTRIBUTE, account.studentName());
    return profile(account);
  }

  public StudentSession changePassword(String passwordValue, HttpServletRequest request) {
    StudentSession current = current(request);
    String password = passwordValue == null ? "" : passwordValue;
    if (password.length() < 6 || password.length() > 72)
      throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "新密码长度应为 6-72 个字符");
    int updated = jdbc.update("UPDATE ranking_student_accounts SET password_hash=?,password_ciphertext=?,password_changed_at=CURRENT_TIMESTAMP(6),updated_at=CURRENT_TIMESTAMP(6) WHERE phone=? AND owner_username=? AND student_id=?",
      passwordEncoder.encode(password), passwordRecovery.encrypt(password, studentPasswordContext(current.ownerUsername(), current.studentId())),
      current.phone(), current.ownerUsername(), current.studentId());
    if (updated != 1) throw unauthorized();
    audit.change("STUDENT", current.studentId(), java.util.Map.of("credentialState", "existing"),
      java.util.Map.of("credentialState", "rotated"));
    audit.record("student_password_changed", request);
    return current(request);
  }

  public void logout(HttpServletRequest request) {
    HttpSession session = request.getSession(false);
    if (session != null) {
      String owner=value(session.getAttribute(OWNER_ATTRIBUTE));
      String studentId=value(session.getAttribute(STUDENT_ID_ATTRIBUTE));
      String studentName=value(session.getAttribute(STUDENT_NAME_ATTRIBUTE));
      if(!owner.isEmpty()&&!studentId.isEmpty()) {
        audit.owner(owner);audit.actor("STUDENT",studentId,studentName);audit.record("student_logout",request);
      }
      clear(session);
    }
  }

  public RankingPayload.Board board(HttpServletRequest request) {
    StudentSession student = current(request);
    return rankings.studentBoard(student.ownerUsername(), student.studentId());
  }

  public RankingPayload.OpportunitySummary opportunities(HttpServletRequest request, String studentId) {
    StudentSession student = current(request);
    if (!student.studentId().equals(studentId))
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "只能查看自己的积分机会");
    return rankings.opportunitiesForOwner(student.ownerUsername(), student.studentId());
  }

  private Account find(String phone) {
    try {
      return jdbc.queryForObject("SELECT phone,owner_username,student_id,student_name,password_hash,password_ciphertext,enabled,password_changed_at FROM ranking_student_accounts WHERE phone=?",
        (rs, n) -> new Account(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5), rs.getString(6), rs.getBoolean(7), rs.getTimestamp(8)), phone);
    } catch (EmptyResultDataAccessException ignored) { return null; }
  }

  private Account find(String phone, String owner, String studentId) {
    try {
      return jdbc.queryForObject("SELECT phone,owner_username,student_id,student_name,password_hash,password_ciphertext,enabled,password_changed_at FROM ranking_student_accounts WHERE phone=? AND owner_username=? AND student_id=?",
        (rs, n) -> new Account(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5), rs.getString(6), rs.getBoolean(7), rs.getTimestamp(8)), phone, owner, studentId);
    } catch (EmptyResultDataAccessException ignored) { return null; }
  }

  private void put(HttpSession session, Account account) {
    session.setAttribute(OWNER_ATTRIBUTE, account.ownerUsername());
    session.setAttribute(STUDENT_ID_ATTRIBUTE, account.studentId());
    session.setAttribute(STUDENT_NAME_ATTRIBUTE, account.studentName());
    session.setAttribute(PHONE_ATTRIBUTE, account.phone());
  }

  private void clear(HttpSession session) {
    session.removeAttribute(OWNER_ATTRIBUTE);
    session.removeAttribute(STUDENT_ID_ATTRIBUTE);
    session.removeAttribute(STUDENT_NAME_ATTRIBUTE);
    session.removeAttribute(PHONE_ATTRIBUTE);
  }

  private StudentSession profile(Account account) {
    return new StudentSession(account.ownerUsername(), account.studentId(), account.studentName(), account.phone(), account.passwordChangedAt() == null);
  }

  public RecoveredPassword recoveredPassword(String owner, String studentId) {
    try {
      return jdbc.queryForObject("SELECT student_name,phone,password_ciphertext FROM ranking_student_accounts WHERE owner_username=? AND student_id=?",
        (rs, n) -> new RecoveredPassword(studentId, rs.getString(1), rs.getString(2),
          passwordRecovery.decrypt(rs.getString(3), studentPasswordContext(owner, studentId))), owner, studentId);
    } catch (EmptyResultDataAccessException ignored) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "学生账号不存在");
    }
  }

  public RecoveredPassword resetPassword(String owner, String studentId, String passwordValue) {
    String password = passwordValue == null ? "" : passwordValue;
    if (password.length() < 6 || password.length() > 72)
      throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "密码长度应为 6-72 个字符");
    int updated = jdbc.update("UPDATE ranking_student_accounts SET password_hash=?,password_ciphertext=?,password_changed_at=CURRENT_TIMESTAMP(6),updated_at=CURRENT_TIMESTAMP(6) WHERE owner_username=? AND student_id=?",
      passwordEncoder.encode(password), passwordRecovery.encrypt(password, studentPasswordContext(owner, studentId)), owner, studentId);
    if (updated != 1) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "学生账号不存在");
    return recoveredPassword(owner, studentId);
  }

  private String studentPasswordContext(String owner, String studentId) { return "student:" + owner + ":" + studentId; }

  private String normalizePhone(String value) {
    String phone = value == null ? "" : value.trim();
    if (!phone.matches("^1\\d{10}$"))
      throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "请输入 11 位手机号");
    return phone;
  }

  private String value(Object value) { return value == null ? "" : String.valueOf(value); }
  private ResponseStatusException unauthorized() { return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "学生登录已失效，请重新登录"); }

  private record Account(String phone, String ownerUsername, String studentId, String studentName,
                         String passwordHash, String passwordCiphertext, boolean enabled, Timestamp passwordChangedAt) {}
  public record RecoveredPassword(String studentId, String studentName, String phone, String password) {}
  public record StudentSession(String ownerUsername, String studentId, String studentName, String phone,
                               boolean mustChangePassword) {}
}
