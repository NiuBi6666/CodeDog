package cn.codedog.controller;

import cn.codedog.service.AuditService;
import com.fasterxml.jackson.databind.ObjectMapper;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@RestController
public class QuestionnaireSsoController {
    private final ObjectMapper objectMapper;
    private final AuditService audit;
    private final String secret;
    private final String ssoUrl;

    public QuestionnaireSsoController(ObjectMapper objectMapper, AuditService audit,
                                      @Value("${codedog.questionnaire.sso-secret:}") String secret,
                                      @Value("${codedog.questionnaire.sso-url:https://www.codedog.online/tduck-api/codedog/sso}") String ssoUrl) {
        this.objectMapper = objectMapper;
        this.audit = audit;
        this.secret = secret;
        this.ssoUrl = ssoUrl;
    }

    @GetMapping("/api/questionnaire/sso")
    public ResponseEntity<Void> redirect(Principal principal, HttpServletRequest request) throws Exception {
        if (secret.length() < 32 || !ssoUrl.startsWith("https://")) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "问卷单点登录尚未配置");
        }

        long now = Instant.now().getEpochSecond();
        String username = principal.getName();
        String localPart = username.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]", "-");
        if (localPart.isBlank()) localPart = "admin";

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("iss", "codedog");
        payload.put("aud", "tduck");
        payload.put("sub", username);
        payload.put("email", localPart + "@codedog.local");
        payload.put("iat", now);
        payload.put("exp", now + 60);
        payload.put("nonce", UUID.randomUUID().toString());

        String encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(objectMapper.writeValueAsBytes(payload));
        String token = encoded + "." + sign(encoded);
        String location = UriComponentsBuilder.fromUriString(ssoUrl)
            .queryParam("token", token).build().encode().toUriString();

        audit.record("questionnaire_sso", request);
        return ResponseEntity.status(HttpStatus.FOUND)
            .location(URI.create(location))
            .cacheControl(CacheControl.noStore())
            .build();
    }

    private String sign(String value) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
    }
}
