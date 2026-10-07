package cn.codedog.service;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PasswordRecoveryCipher {
  private static final String PREFIX = "v1:";
  private static final int NONCE_BYTES = 12;
  private final String encodedKey;
  private final SecureRandom random = new SecureRandom();

  public PasswordRecoveryCipher(@Value("${codedog.password-recovery-key:}") String encodedKey) {
    this.encodedKey = encodedKey == null ? "" : encodedKey.trim();
  }

  public boolean configured() { return !encodedKey.isEmpty(); }

  public String encrypt(String plaintext, String context) {
    if (plaintext == null || plaintext.isEmpty()) throw new IllegalArgumentException("password is required");
    try {
      byte[] nonce = new byte[NONCE_BYTES];
      random.nextBytes(nonce);
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(Cipher.ENCRYPT_MODE, key(), new GCMParameterSpec(128, nonce));
      cipher.updateAAD(context.getBytes(StandardCharsets.UTF_8));
      byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
      return PREFIX + Base64.getEncoder().encodeToString(ByteBuffer.allocate(nonce.length + encrypted.length).put(nonce).put(encrypted).array());
    } catch (GeneralSecurityException error) {
      throw unavailable("密码加密失败", error);
    }
  }

  public String decrypt(String value, String context) {
    if (value == null || value.isBlank()) return null;
    if (!value.startsWith(PREFIX)) throw unavailable("密码密文版本不受支持", null);
    try {
      byte[] packed = Base64.getDecoder().decode(value.substring(PREFIX.length()));
      if (packed.length <= NONCE_BYTES) throw new GeneralSecurityException("invalid ciphertext");
      byte[] nonce = java.util.Arrays.copyOfRange(packed, 0, NONCE_BYTES);
      byte[] encrypted = java.util.Arrays.copyOfRange(packed, NONCE_BYTES, packed.length);
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, nonce));
      cipher.updateAAD(context.getBytes(StandardCharsets.UTF_8));
      return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
    } catch (GeneralSecurityException | IllegalArgumentException error) {
      throw unavailable("密码无法解密，请检查恢复密钥", error);
    }
  }

  private SecretKeySpec key() {
    if (!configured()) throw unavailable("服务器尚未配置密码恢复密钥", null);
    try {
      byte[] value = Base64.getDecoder().decode(encodedKey);
      if (value.length != 32) throw new IllegalArgumentException("key must contain 32 bytes");
      return new SecretKeySpec(value, "AES");
    } catch (IllegalArgumentException error) {
      throw unavailable("密码恢复密钥必须是 Base64 编码的 32 字节密钥", error);
    }
  }

  private ResponseStatusException unavailable(String message, Throwable cause) {
    return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, message, cause);
  }
}
