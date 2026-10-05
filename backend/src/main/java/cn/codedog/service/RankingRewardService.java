package cn.codedog.service;

import cn.codedog.model.RankingRewardPayload;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

@Service
public class RankingRewardService {
  private static final int MAX_IMAGE_BYTES = 2 * 1024 * 1024;
  private static final Set<String> IMAGE_TYPES = Set.of("image/png", "image/jpeg", "image/webp", "image/gif");
  private final JdbcTemplate jdbc;

  public RankingRewardService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

  public RankingRewardPayload.Announcement announcement(String owner) {
    List<RankingRewardPayload.Announcement> values = jdbc.query(
      "SELECT announcement,updated_at FROM ranking_board_settings WHERE owner_username=?",
      (rs, n) -> new RankingRewardPayload.Announcement(rs.getString(1), instant(rs.getTimestamp(2))), owner);
    return values.isEmpty() ? new RankingRewardPayload.Announcement("", null) : values.getFirst();
  }

  public RankingRewardPayload.Announcement publicAnnouncement() {
    return announcement(publicOwner());
  }

  @Transactional
  public RankingRewardPayload.Announcement updateAnnouncement(String owner, String value) {
    String announcement = optionalText(value, "排行榜公告", 500);
    jdbc.update("INSERT INTO ranking_board_settings(owner_username,announcement) VALUES(?,?) ON DUPLICATE KEY UPDATE announcement=VALUES(announcement),updated_at=CURRENT_TIMESTAMP(6)", owner, announcement);
    return announcement(owner);
  }

  public List<RankingRewardPayload.Reward> rewards(String owner) {
    return jdbc.query("SELECT id,reward_name,required_points,enabled,image_data IS NOT NULL,created_at,updated_at FROM ranking_rewards WHERE owner_username=? ORDER BY enabled DESC,required_points,id",
      (rs, n) -> reward(rs.getLong(1), rs.getString(2), rs.getInt(3), rs.getBoolean(4), rs.getBoolean(5), instant(rs.getTimestamp(6)), instant(rs.getTimestamp(7)), false), owner);
  }

  public List<RankingRewardPayload.Reward> publicRewards() {
    String owner = publicOwner();
    return jdbc.query("SELECT id,reward_name,required_points,enabled,image_data IS NOT NULL,created_at,updated_at FROM ranking_rewards WHERE owner_username=? AND enabled=TRUE ORDER BY required_points,id",
      (rs, n) -> reward(rs.getLong(1), rs.getString(2), rs.getInt(3), true, rs.getBoolean(5), instant(rs.getTimestamp(6)), instant(rs.getTimestamp(7)), true), owner);
  }

  @Transactional
  public RankingRewardPayload.Reward createReward(String owner, String nameValue, Integer pointsValue, Boolean enabledValue, MultipartFile image) {
    String name = text(nameValue, "奖品名称", 100);
    int points = points(pointsValue);
    ImageValue imageValue = image(image, true);
    GeneratedKeyHolder key = new GeneratedKeyHolder();
    jdbc.update(connection -> {
      PreparedStatement statement = connection.prepareStatement("INSERT INTO ranking_rewards(owner_username,reward_name,required_points,image_data,image_content_type,enabled) VALUES(?,?,?,?,?,?)", new String[]{"id"});
      statement.setString(1, owner); statement.setString(2, name); statement.setInt(3, points);
      statement.setBytes(4, imageValue == null ? null : imageValue.data()); statement.setString(5, imageValue == null ? null : imageValue.contentType());
      statement.setBoolean(6, enabledValue == null || enabledValue); return statement;
    }, key);
    return rewardById(owner, Objects.requireNonNull(key.getKey()).longValue());
  }

  @Transactional
  public RankingRewardPayload.Reward updateReward(String owner, long id, String nameValue, Integer pointsValue, Boolean enabledValue, MultipartFile image) {
    rewardById(owner, id);
    String name = text(nameValue, "奖品名称", 100);
    int points = points(pointsValue);
    ImageValue imageValue = image(image, false);
    int changed = imageValue == null
      ? jdbc.update("UPDATE ranking_rewards SET reward_name=?,required_points=?,enabled=?,updated_at=CURRENT_TIMESTAMP(6) WHERE id=? AND owner_username=?", name, points, enabledValue == null || enabledValue, id, owner)
      : jdbc.update("UPDATE ranking_rewards SET reward_name=?,required_points=?,enabled=?,image_data=?,image_content_type=?,updated_at=CURRENT_TIMESTAMP(6) WHERE id=? AND owner_username=?", name, points, enabledValue == null || enabledValue, imageValue.data(), imageValue.contentType(), id, owner);
    if (changed == 0) throw notFound("奖品不存在");
    return rewardById(owner, id);
  }

  @Transactional
  public void deleteReward(String owner, long id) {
    if (jdbc.update("DELETE FROM ranking_rewards WHERE id=? AND owner_username=?", id, owner) == 0) throw notFound("奖品不存在");
  }

  public RankingRewardPayload.RewardImage rewardImage(String owner, long id) {
    List<RankingRewardPayload.RewardImage> values = jdbc.query("SELECT image_data,image_content_type FROM ranking_rewards WHERE id=? AND owner_username=? AND image_data IS NOT NULL",
      (rs, n) -> new RankingRewardPayload.RewardImage(rs.getBytes(1), rs.getString(2)), id, owner);
    if (values.isEmpty()) throw notFound("奖品图片不存在");
    return values.getFirst();
  }

  public RankingRewardPayload.RewardImage publicRewardImage(long id) {
    String owner = publicOwner();
    List<RankingRewardPayload.RewardImage> values = jdbc.query("SELECT image_data,image_content_type FROM ranking_rewards WHERE id=? AND owner_username=? AND enabled=TRUE AND image_data IS NOT NULL",
      (rs, n) -> new RankingRewardPayload.RewardImage(rs.getBytes(1), rs.getString(2)), id, owner);
    if (values.isEmpty()) throw notFound("奖品图片不存在");
    return values.getFirst();
  }

  public List<RankingRewardPayload.Redemption> redemptions(String owner) {
    return jdbc.query("SELECT id,student_id,student_name,reward_id,reward_name,points_spent,status,redeemed_at,fulfilled_at,fulfilled_by FROM ranking_reward_redemptions WHERE owner_username=? ORDER BY redeemed_at DESC,id DESC",
      (rs, n) -> new RankingRewardPayload.Redemption(rs.getLong(1), rs.getString(2), rs.getString(3), nullableLong(rs, 4), rs.getString(5), rs.getInt(6), rs.getString(7), instant(rs.getTimestamp(8)), instant(rs.getTimestamp(9)), rs.getString(10)), owner);
  }

  @Transactional
  public RankingRewardPayload.Redemption createRedemption(String owner, RankingRewardPayload.RedemptionRequest request) {
    if (request == null) throw invalid("兑换信息不能为空");
    String studentId = text(request.studentId(), "学员 ID", 100);
    List<String> names = jdbc.query("SELECT student_name FROM ranking_students WHERE owner_username=? AND student_id=? ORDER BY updated_at DESC LIMIT 1 FOR UPDATE", (rs, n) -> rs.getString(1), owner, studentId);
    if (names.isEmpty()) throw notFound("学员不存在");
    List<RewardValue> rewardValues = jdbc.query("SELECT id,reward_name,required_points,enabled FROM ranking_rewards WHERE id=? AND owner_username=? FOR UPDATE",
      (rs, n) -> new RewardValue(rs.getLong(1), rs.getString(2), rs.getInt(3), rs.getBoolean(4)), request.rewardId(), owner);
    if (rewardValues.isEmpty()) throw notFound("奖品不存在");
    RewardValue reward = rewardValues.getFirst();
    if (!reward.enabled()) throw invalid("该奖品已停用");
    Integer earned = jdbc.queryForObject("SELECT COALESCE(SUM(total_points),0) FROM ranking_lesson_results WHERE owner_username=? AND student_id=?", Integer.class, owner, studentId);
    Integer spent = jdbc.queryForObject("SELECT COALESCE(SUM(points_spent),0) FROM ranking_reward_redemptions WHERE owner_username=? AND student_id=?", Integer.class, owner, studentId);
    int available = Math.max(0, Objects.requireNonNullElse(earned, 0) - Objects.requireNonNullElse(spent, 0));
    if (available < reward.points()) throw invalid("可用积分不足，当前可用 " + available + " 分");
    GeneratedKeyHolder key = new GeneratedKeyHolder();
    jdbc.update(connection -> {
      PreparedStatement statement = connection.prepareStatement("INSERT INTO ranking_reward_redemptions(owner_username,student_id,student_name,reward_id,reward_name,points_spent) VALUES(?,?,?,?,?,?)", new String[]{"id"});
      statement.setString(1, owner); statement.setString(2, studentId); statement.setString(3, names.getFirst()); statement.setLong(4, reward.id()); statement.setString(5, reward.name()); statement.setInt(6, reward.points()); return statement;
    }, key);
    return redemptionById(owner, Objects.requireNonNull(key.getKey()).longValue());
  }

  @Transactional
  public RankingRewardPayload.Redemption setFulfilled(String owner, long id, boolean fulfilled, String actor) {
    int changed = fulfilled
      ? jdbc.update("UPDATE ranking_reward_redemptions SET status='FULFILLED',fulfilled_at=CURRENT_TIMESTAMP(6),fulfilled_by=? WHERE id=? AND owner_username=?", actor, id, owner)
      : jdbc.update("UPDATE ranking_reward_redemptions SET status='PENDING',fulfilled_at=NULL,fulfilled_by=NULL WHERE id=? AND owner_username=?", id, owner);
    if (changed == 0) throw notFound("兑换记录不存在");
    return redemptionById(owner, id);
  }

  private RankingRewardPayload.Reward rewardById(String owner, long id) {
    List<RankingRewardPayload.Reward> values = jdbc.query("SELECT id,reward_name,required_points,enabled,image_data IS NOT NULL,created_at,updated_at FROM ranking_rewards WHERE id=? AND owner_username=?",
      (rs, n) -> reward(rs.getLong(1), rs.getString(2), rs.getInt(3), rs.getBoolean(4), rs.getBoolean(5), instant(rs.getTimestamp(6)), instant(rs.getTimestamp(7)), false), id, owner);
    if (values.isEmpty()) throw notFound("奖品不存在"); return values.getFirst();
  }

  private RankingRewardPayload.Reward reward(long id, String name, int points, boolean enabled, boolean hasImage, Instant createdAt, Instant updatedAt, boolean publicImage) {
    String imagePath = publicImage ? "/api/public/rankings/rewards/" : "/api/rankings/admin/rewards/";
    return new RankingRewardPayload.Reward(id, name, points, enabled, hasImage, hasImage ? imagePath + id + "/image?v=" + (updatedAt == null ? 0 : updatedAt.toEpochMilli()) : null, createdAt, updatedAt);
  }

  private String publicOwner() { return jdbc.queryForObject("SELECT username FROM users ORDER BY is_admin DESC,id LIMIT 1", String.class); }

  private RankingRewardPayload.Redemption redemptionById(String owner, long id) {
    return redemptions(owner).stream().filter(value -> value.id() == id).findFirst().orElseThrow(() -> notFound("兑换记录不存在"));
  }

  private ImageValue image(MultipartFile file, boolean optional) {
    if (file == null || file.isEmpty()) return null;
    if (file.getSize() > MAX_IMAGE_BYTES) throw invalid("奖品图片不能超过 2 MB");
    String type = Objects.toString(file.getContentType(), "").toLowerCase(Locale.ROOT);
    if (!IMAGE_TYPES.contains(type)) throw invalid("奖品图片仅支持 PNG、JPG、WebP 或 GIF");
    try { return new ImageValue(file.getBytes(), type); } catch (IOException error) { throw invalid("奖品图片读取失败"); }
  }

  private int points(Integer value) { if (value == null || value <= 0 || value > 1_000_000) throw invalid("所需积分必须是 1 至 1000000 的整数"); return value; }
  private String text(String value, String label, int max) { String result = value == null ? "" : value.trim(); if (result.isEmpty()) throw invalid(label + "不能为空"); if (result.length() > max) throw invalid(label + "不能超过 " + max + " 个字符"); return result; }
  private String optionalText(String value, String label, int max) { String result = value == null ? "" : value.trim(); if (result.length() > max) throw invalid(label + "不能超过 " + max + " 个字符"); return result; }
  private Instant instant(Timestamp value) { return value == null ? null : value.toInstant(); }
  private Long nullableLong(java.sql.ResultSet rs, int column) throws java.sql.SQLException { long value = rs.getLong(column); return rs.wasNull() ? null : value; }
  private ResponseStatusException invalid(String message) { return new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, message); }
  private ResponseStatusException notFound(String message) { return new ResponseStatusException(HttpStatus.NOT_FOUND, message); }
  private record ImageValue(byte[] data, String contentType) {}
  private record RewardValue(long id, String name, int points, boolean enabled) {}
}
