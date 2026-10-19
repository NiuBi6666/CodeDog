package cn.codedog.controller;

import cn.codedog.model.RankingPayload;
import cn.codedog.service.StudentRankingAuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/public/rankings")
public class StudentRankingAuthController {
  private final StudentRankingAuthService auth;
  private final cn.codedog.service.StudentHonorService honors;
  public StudentRankingAuthController(StudentRankingAuthService auth,cn.codedog.service.StudentHonorService honors) { this.auth = auth; this.honors=honors; }

  @PostMapping("/student-auth/login")
  public StudentRankingAuthService.StudentSession login(@Valid @RequestBody LoginRequest body, HttpServletRequest request) {
    return body.studentId()==null?auth.login(body.phone(), body.password(), request):auth.login(body.phone(),body.password(),body.studentId(),request);
  }

  @GetMapping("/student-auth/session")
  public StudentRankingAuthService.StudentSession session(HttpServletRequest request) { return auth.current(request); }

  @PostMapping("/student-auth/change-password")
  public StudentRankingAuthService.StudentSession changePassword(@Valid @RequestBody PasswordChangeRequest body,
                                                                 HttpServletRequest request) {
    return auth.changePassword(body.oldPassword(), body.password(), request);
  }

  @PostMapping("/student-auth/logout")
  public java.util.Map<String, Boolean> logout(HttpServletRequest request) {
    auth.logout(request);
    return java.util.Map.of("ok", true);
  }

  @GetMapping("/student-board")
  public cn.codedog.service.StudentHonorService.BoardView board(HttpServletRequest request) { return honors.board(request); }

  @PostMapping("/student-honor/celebration")
  public cn.codedog.service.StudentHonorService.Celebration celebration(HttpServletRequest request) {return honors.claim(request);}

  @GetMapping("/student-opportunities")
  public RankingPayload.OpportunitySummary opportunities(HttpServletRequest request,
                                                          @RequestParam String studentId) {
    return auth.opportunities(request, studentId);
  }

  public record LoginRequest(
    @NotBlank @Pattern(regexp = "^1\\d{10}$", message = "请输入 11 位手机号") String phone,
    @NotBlank @Size(max = 72, message = "密码不能超过 72 个字符") String password, String studentId) {}

  public record PasswordChangeRequest(
    @NotBlank(message = "请输入旧密码") @Size(max = 72, message = "旧密码不能超过 72 个字符") String oldPassword,
    @NotBlank @Size(min = 6, max = 72, message = "新密码长度应为 6-72 个字符") String password) {}
}
