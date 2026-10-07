package cn.codedog.service;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PasswordRecoveryCipherTest {
  private static final String KEY = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";
  private static final String OTHER_KEY = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=";

  @Test
  void encryptsAndDecryptsWithAuthenticatedContext() {
    PasswordRecoveryCipher cipher = new PasswordRecoveryCipher(KEY);
    String encrypted = cipher.encrypt("student-secret", "student:Liam:6105387");

    assertThat(encrypted).startsWith("v1:").doesNotContain("student-secret");
    assertThat(cipher.decrypt(encrypted, "student:Liam:6105387")).isEqualTo("student-secret");
    assertThatThrownBy(() -> cipher.decrypt(encrypted, "student:Liam:another"))
      .isInstanceOf(ResponseStatusException.class)
      .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode().value()).isEqualTo(503));
    assertThatThrownBy(() -> new PasswordRecoveryCipher(OTHER_KEY).decrypt(encrypted, "student:Liam:6105387"))
      .isInstanceOf(ResponseStatusException.class);
  }

  @Test
  void rejectsMissingOrInvalidKeys() {
    assertThatThrownBy(() -> new PasswordRecoveryCipher("").encrypt("secret", "student:Liam:1"))
      .isInstanceOf(ResponseStatusException.class)
      .satisfies(error -> assertThat(((ResponseStatusException) error).getReason()).contains("尚未配置"));
    assertThatThrownBy(() -> new PasswordRecoveryCipher("bm90LTMyLWJ5dGVz").encrypt("secret", "student:Liam:1"))
      .isInstanceOf(ResponseStatusException.class)
      .satisfies(error -> assertThat(((ResponseStatusException) error).getReason()).contains("32 字节"));
  }
}
