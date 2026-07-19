package com.tduck.cloud.api.web.controller;

import cn.hutool.core.util.IdUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tduck.cloud.account.constant.AccountConstants;
import com.tduck.cloud.account.entity.UserEntity;
import com.tduck.cloud.account.entity.enums.AccountChannelEnum;
import com.tduck.cloud.account.service.UserService;
import com.tduck.cloud.account.service.UserTokenService;
import com.tduck.cloud.account.util.PasswordUtils;
import com.tduck.cloud.account.vo.LoginUserVO;
import com.tduck.cloud.api.annotation.NotLogin;
import com.tduck.cloud.api.util.HttpUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Map;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/codedog")
@RequiredArgsConstructor
public class CodeDogSsoController {
    private static final String COOKIE_NAME = "TDUCK_SESSION";
    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[a-z0-9._-]+@codedog\\.local$");
    private static final Pattern NONCE_PATTERN =
            Pattern.compile("^[0-9a-fA-F-]{36}$");

    private final UserService userService;
    private final UserTokenService userTokenService;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    @Value("${codedog.sso.secret:}")
    private String secret;

    @Value("${codedog.sso.session-seconds:28800}")
    private int sessionSeconds;

    @Value("${codedog.sso.success-path:/codedog/sso/complete}")
    private String successPath;

    @GetMapping("/sso")
    @NotLogin
    public void login(@RequestParam String token, HttpServletRequest request,
                      HttpServletResponse response) throws IOException {
        try {
            SsoPayload payload = verify(token);
            consumeNonce(payload.nonce);
            UserEntity user = resolveUser(payload);
            LoginUserVO login = userService.getLoginResult(
                    user, AccountChannelEnum.EMAIL, HttpUtils.getIpAddr(request));

            setSessionCookie(response, login.getToken(), sessionSeconds);
            response.setHeader("Cache-Control", "no-store");
            response.setHeader("Referrer-Policy", "no-referrer");
            response.sendRedirect(successPath);
        } catch (SsoException exception) {
            response.sendError(exception.status, exception.getMessage());
        }
    }

    @GetMapping("/health")
    @NotLogin
    public void health(HttpServletResponse response) throws IOException {
        response.setContentType("application/json");
        response.getWriter().write("{\"status\":\"UP\"}");
    }

    @PostMapping("/logout")
    @NotLogin
    public void logout(HttpServletRequest request, HttpServletResponse response) {
        String token = readCookie(request);
        if (token != null && !token.isEmpty()) {
            userTokenService.removeToken(token);
        }
        setSessionCookie(response, "", 0);
        response.setStatus(HttpServletResponse.SC_NO_CONTENT);
        response.setHeader("Cache-Control", "no-store");
    }

    private SsoPayload verify(String token) {
        if (secret == null || secret.length() < 32) {
            throw new SsoException(HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                    "CodeDog single sign-on is not configured");
        }

        try {
            String[] parts = token == null ? new String[0] : token.split("\\.");
            if (parts.length != 2) {
                throw new IllegalArgumentException("invalid token");
            }

            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] expected = mac.doFinal(parts[0].getBytes(StandardCharsets.UTF_8));
            byte[] received = Base64.getUrlDecoder().decode(parts[1]);
            if (!MessageDigest.isEqual(expected, received)) {
                throw new IllegalArgumentException("invalid signature");
            }

            Map<String, Object> claims = objectMapper.readValue(
                    Base64.getUrlDecoder().decode(parts[0]),
                    new TypeReference<Map<String, Object>>() { });
            String issuer = stringClaim(claims, "iss");
            String audience = stringClaim(claims, "aud");
            String subject = stringClaim(claims, "sub");
            String email = stringClaim(claims, "email");
            String nonce = stringClaim(claims, "nonce");
            long issuedAt = numberClaim(claims, "iat");
            long expiresAt = numberClaim(claims, "exp");
            long now = Instant.now().getEpochSecond();

            boolean validIdentity = "codedog".equals(issuer)
                    && "tduck".equals(audience)
                    && subject.length() > 0
                    && subject.length() <= 50
                    && EMAIL_PATTERN.matcher(email).matches()
                    && NONCE_PATTERN.matcher(nonce).matches();
            boolean validTime = issuedAt <= now + 10
                    && expiresAt >= now
                    && expiresAt > issuedAt
                    && expiresAt - issuedAt <= 120;
            if (!validIdentity || !validTime) {
                throw new IllegalArgumentException("invalid claims");
            }

            return new SsoPayload(subject, email, nonce);
        } catch (SsoException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new SsoException(HttpServletResponse.SC_UNAUTHORIZED,
                    "Invalid or expired CodeDog single sign-on token");
        }
    }

    private void consumeNonce(String nonce) {
        jdbcTemplate.update("DELETE FROM codedog_sso_nonce WHERE expires_at < NOW()");
        try {
            jdbcTemplate.update(
                    "INSERT INTO codedog_sso_nonce (nonce, expires_at) VALUES (?, ?)",
                    nonce, Timestamp.from(Instant.now().plusSeconds(120)));
        } catch (DuplicateKeyException exception) {
            throw new SsoException(HttpServletResponse.SC_UNAUTHORIZED,
                    "The single sign-on token has already been used");
        }
    }

    private synchronized UserEntity resolveUser(SsoPayload payload) {
        Long mappedUserId = findMappedUser(payload.subject);
        UserEntity user = mappedUserId == null ? null : userService.getById(mappedUserId);

        if (user == null && mappedUserId != null) {
            throw new SsoException(HttpServletResponse.SC_UNAUTHORIZED,
                    "The mapped questionnaire account is unavailable");
        }

        boolean newlyLinked = mappedUserId == null;
        if (user == null) {
            user = userService.getUserByEmail(payload.email);
        }
        if (user == null) {
            UserEntity admin = userService.getById(1L);
            if (admin != null && ("admin@tduckcloud.com".equalsIgnoreCase(admin.getEmail())
                    || admin.getEmail() == null || admin.getEmail().isEmpty())) {
                user = admin;
            }
        }
        if (user != null && Boolean.TRUE.equals(user.getDeleted())) {
            throw new SsoException(HttpServletResponse.SC_UNAUTHORIZED,
                    "The questionnaire account is disabled");
        }
        if (user == null) {
            user = new UserEntity();
            user.setAvatar(AccountConstants.DEFAULT_AVATAR);
            user.setGender(0);
            user.setCreateTime(LocalDateTime.now());
        }

        user.setName(payload.subject);
        user.setEmail(payload.email);
        user.setRegChannel(AccountChannelEnum.EMAIL);
        user.setDeleted(false);
        user.setUpdateTime(LocalDateTime.now());
        if (newlyLinked) {
            user.setPasswordType(1);
            user.setPassword(PasswordUtils.encode(
                    IdUtil.fastSimpleUUID() + IdUtil.fastSimpleUUID()));
        }

        if (user.getId() == null) {
            userService.save(user);
        } else {
            userService.updateById(user);
        }

        if (newlyLinked) {
            try {
                jdbcTemplate.update(
                        "INSERT INTO codedog_sso_identity (codedog_subject, user_id, email) VALUES (?, ?, ?)",
                        payload.subject, user.getId(), payload.email);
            } catch (DuplicateKeyException exception) {
                Long existing = findMappedUser(payload.subject);
                if (existing == null || !existing.equals(user.getId())) {
                    throw new SsoException(HttpServletResponse.SC_CONFLICT,
                            "The CodeDog identity is already linked");
                }
            }
        }
        return user;
    }

    private Long findMappedUser(String subject) {
        try {
            return jdbcTemplate.queryForObject(
                    "SELECT user_id FROM codedog_sso_identity WHERE codedog_subject = ?",
                    Long.class, subject);
        } catch (EmptyResultDataAccessException exception) {
            return null;
        }
    }

    private String stringClaim(Map<String, Object> claims, String key) {
        Object value = claims.get(key);
        if (!(value instanceof String)) {
            throw new IllegalArgumentException("invalid " + key);
        }
        return (String) value;
    }

    private long numberClaim(Map<String, Object> claims, String key) {
        Object value = claims.get(key);
        if (!(value instanceof Number)) {
            throw new IllegalArgumentException("invalid " + key);
        }
        return ((Number) value).longValue();
    }

    private void setSessionCookie(HttpServletResponse response, String value, int maxAge) {
        response.addHeader("Set-Cookie", COOKIE_NAME + "=" + value
                + "; Path=/; Max-Age=" + maxAge
                + "; HttpOnly; Secure; SameSite=Strict");
    }

    private String readCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (COOKIE_NAME.equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }

    private static final class SsoPayload {
        private final String subject;
        private final String email;
        private final String nonce;

        private SsoPayload(String subject, String email, String nonce) {
            this.subject = subject;
            this.email = email;
            this.nonce = nonce;
        }
    }

    private static final class SsoException extends RuntimeException {
        private final int status;

        private SsoException(int status, String message) {
            super(message);
            this.status = status;
        }
    }
}
