package cn.codedog.ranking;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
public record RankingPayload(String campId, String campName, List<ClassData> classes) {
  public record ClassData(String classId, String className, List<LessonData> lessons) {}
  public record LessonData(String lessonId, String lessonName, Integer lessonOrder, Instant endedAt, List<StudentResult> students) {}
  public record StudentResult(String studentId, String studentName, Double completionRate, Counts inclass, Counts homework) {}
  public record Counts(Integer total, Integer submitted, Integer passed) { public static Counts empty() { return new Counts(0, 0, 0); } }
  public record ImportSummary(long batchId, int receivedRows, int changedRows, int unchangedRows, int rejectedRows, List<RowError> errors) {}
  public record RowError(String classId, String lessonId, String studentId, String message) {}
  public record PairingCode(String code, Instant expiresAt) {}
  public record Device(long id, String deviceName, String owner, Instant createdAt, Instant lastSeenAt, boolean revoked) {}
  public record Connection(String token, long deviceId) {}
  public record Catalog(List<CampOption> camps, Instant updatedAt) {}
  public record CampOption(String id, String name, List<ClassOption> classes) {}
  public record ClassOption(String id, String name) {}
  public record Board(String campId, String campName, String classId, String className, String scope,
                      int studentCount, Instant updatedAt, LocalDate trendBaselineDate, List<Entry> rankings) {}
  public record Entry(int rank, String studentId, String studentName, String classId, String className,
                      int totalPoints, int completionPoints, int inclassPoints, int homeworkPoints,
                      int lessonCount, int level, String levelName, Instant scoreReachedAt,
                      double accuracyRate, Integer previousRank, int rankChange, String trend) {}
}
