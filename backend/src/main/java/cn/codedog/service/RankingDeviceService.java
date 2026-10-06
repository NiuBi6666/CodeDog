package cn.codedog.service;

import cn.codedog.model.RankingPayload;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class RankingDeviceService {
  private static final SecureRandom RANDOM = new SecureRandom();

  private final JdbcTemplate jdbc;

  public RankingDeviceService(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Transactional
  public RankingPayload.PairingCode createPairingCode(String owner) {
    jdbc.update("DELETE FROM ranking_pairing_codes WHERE expires_at<CURRENT_TIMESTAMP(6) OR used_at IS NOT NULL");
    String code = "%08d".formatted(RANDOM.nextInt(100_000_000));
    Instant expires = Instant.now().plus(10, ChronoUnit.MINUTES);
    jdbc.update("INSERT INTO ranking_pairing_codes(code_hash,owner_username,expires_at) VALUES(?,?,?)",
        sha256(code), owner, Timestamp.from(expires));
    return new RankingPayload.PairingCode(code.substring(0, 4) + "-" + code.substring(4), expires);
  }

  @Transactional
  public RankingPayload.Connection connect(String codeValue, String deviceValue) {
    String code = codeValue == null ? "" : codeValue.replaceAll("[^0-9]", "");
    String device = text(deviceValue, "设备名称", 100);
    if (code.length() != 8) {
      throw invalid("连接码格式不正确");
    }
    List<String> owners = jdbc.query(
        "SELECT owner_username FROM ranking_pairing_codes WHERE code_hash=? AND used_at IS NULL "
            + "AND expires_at>CURRENT_TIMESTAMP(6) FOR UPDATE",
        (rs, n) -> rs.getString(1), sha256(code));
    if (owners.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "连接码无效或已过期");
    }
    jdbc.update("UPDATE ranking_pairing_codes SET used_at=CURRENT_TIMESTAMP(6) WHERE code_hash=?", sha256(code));
    return createDevice(owners.getFirst(), device, "");
  }

  @Transactional
  public RankingPayload.Connection bootstrap(String crmTeacherValue, String deviceValue) {
    String crmTeacherId = text(crmTeacherValue, "CRM 教师 ID", 100);
    if (!crmTeacherId.matches("^[A-Za-z0-9_-]+$")) {
      throw invalid("CRM 教师 ID 格式不正确");
    }
    String owner = lookup("SELECT owner_username FROM ranking_teacher_mappings WHERE crm_teacher_id=?", crmTeacherId);
    if (owner == null) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "当前 CRM 教师尚未绑定 CodeDog 账号，请联系管理员");
    }
    Integer active = jdbc.queryForObject(
        "SELECT COUNT(*) FROM ranking_extension_devices WHERE owner_username=? AND revoked_at IS NULL",
        Integer.class, owner);
    if (active != null && active >= 20) {
      throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "该账号已连接过多扩展设备，请先在 CodeDog 撤销旧设备");
    }
    return createDevice(owner, text(deviceValue, "设备名称", 100), crmTeacherId);
  }

  public String authenticateToken(String authorization) {
    String hash = deviceTokenHash(authorization);
    List<String> owners = jdbc.query(
        "SELECT owner_username FROM ranking_extension_devices WHERE token_hash=? AND revoked_at IS NULL",
        (rs, n) -> rs.getString(1), hash);
    if (owners.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "扩展设备令牌无效或已撤销");
    }
    jdbc.update("UPDATE ranking_extension_devices SET last_seen_at=CURRENT_TIMESTAMP(6) WHERE token_hash=?", hash);
    return owners.getFirst();
  }

  public RankingPayload.ExtensionSession session(String authorization) {
    String hash = deviceTokenHash(authorization);
    authenticateToken(authorization);
    List<RankingPayload.ExtensionSession> values = jdbc.query("""
        SELECT d.id,d.owner_username,u.teacher_public_id,m.crm_teacher_id
        FROM ranking_extension_devices d
        JOIN users u ON u.username=d.owner_username
        LEFT JOIN ranking_teacher_mappings m ON m.owner_username=d.owner_username
        WHERE d.token_hash=? AND d.revoked_at IS NULL
        """, (rs, n) -> new RankingPayload.ExtensionSession(
        rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4)), hash);
    if (values.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "扩展设备令牌无效或已撤销");
    }
    return values.getFirst();
  }

  public List<RankingPayload.Device> devices(String owner) {
    return jdbc.query("SELECT id,device_name,owner_username,created_at,last_seen_at,revoked_at "
            + "FROM ranking_extension_devices WHERE owner_username=? ORDER BY created_at DESC",
        (rs, n) -> new RankingPayload.Device(rs.getLong(1), rs.getString(2), rs.getString(3),
            rs.getTimestamp(4).toInstant(), instant(rs.getTimestamp(5)), rs.getTimestamp(6) != null), owner);
  }

  public void revoke(long id, String owner) {
    if (jdbc.update("UPDATE ranking_extension_devices SET revoked_at=CURRENT_TIMESTAMP(6) "
        + "WHERE id=? AND owner_username=? AND revoked_at IS NULL", id, owner) == 0) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "扩展设备不存在或已撤销");
    }
  }

  private RankingPayload.Connection createDevice(String owner, String device, String crmTeacherId) {
    String token = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes(32));
    KeyHolder key = new GeneratedKeyHolder();
    jdbc.update(connection -> {
      PreparedStatement statement = connection.prepareStatement(
          "INSERT INTO ranking_extension_devices(token_hash,owner_username,device_name) VALUES(?,?,?)",
          new String[]{"id"});
      statement.setString(1, sha256(token));
      statement.setString(2, owner);
      statement.setString(3, device);
      return statement;
    }, key);
    String teacherId = lookup("SELECT teacher_public_id FROM users WHERE username=?", owner);
    return new RankingPayload.Connection(token, Objects.requireNonNull(key.getKey()).longValue(), owner, teacherId,
        crmTeacherId);
  }

  private String deviceTokenHash(String authorization) {
    if (authorization == null || !authorization.regionMatches(true, 0, "Bearer ", 0, 7)
        || authorization.substring(7).trim().isEmpty()) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "缺少扩展设备令牌");
    }
    return sha256(authorization.substring(7).trim());
  }

  private String lookup(String sql, Object... args) {
    try {
      return jdbc.queryForObject(sql, String.class, args);
    } catch (EmptyResultDataAccessException ignored) {
      return null;
    }
  }

  private Instant instant(Timestamp value) {
    return value == null ? null : value.toInstant();
  }

  private String text(String value, String label, int max) {
    String normalized = value == null ? "" : value.trim();
    if (normalized.isEmpty()) throw invalid(label + "不能为空");
    if (normalized.length() > max) throw invalid(label + "不能超过 " + max + " 个字符");
    return normalized;
  }

  private ResponseStatusException invalid(String message) {
    return new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, message);
  }

  private byte[] randomBytes(int size) {
    byte[] bytes = new byte[size];
    RANDOM.nextBytes(bytes);
    return bytes;
  }

  private String sha256(String value) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
          .digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception error) {
      throw new IllegalStateException(error);
    }
  }
}
