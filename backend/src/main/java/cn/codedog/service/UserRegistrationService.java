package cn.codedog.service;

import cn.codedog.dao.UserRepository;
import cn.codedog.model.User;
import cn.codedog.security.PermissionCatalog;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Map;

@Service
public class UserRegistrationService {
    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final PasswordRecoveryCipher recovery;
    private final AuditService audit;
    public UserRegistrationService(UserRepository users, PasswordEncoder encoder, PasswordRecoveryCipher recovery, AuditService audit) {
        this.users=users; this.encoder=encoder; this.recovery=recovery; this.audit=audit;
    }
    @Transactional
    public User create(String usernameValue, String password, String confirmation, HttpServletRequest request, boolean managed) {
        if (!password.equals(confirmation))
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "两次输入的密码不一致");
        String username=usernameValue.trim();
        if (users.existsByUsernameIgnoreCase(username))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "用户名已存在");
        User user=new User();
        user.setUsername(username);
        user.setPasswordHash(encoder.encode(password));
        user.setPasswordCiphertext(recovery.encrypt(password, "user:"+username.toLowerCase(java.util.Locale.ROOT)));
        user.setAdmin(false);
        user.setPermissions(new LinkedHashSet<>(PermissionCatalog.DEFAULT_PERMISSIONS));
        user.setCreatedAt(Instant.now()); user.setUpdatedAt(Instant.now());
        try { users.saveAndFlush(user); }
        catch (DataIntegrityViolationException error) {
            audit.record((managed ? "user_create_failed:" : "registration_failed:")+username,request);
            throw new ResponseStatusException(HttpStatus.CONFLICT,"用户名或教师 ID 已存在");
        }
        audit.change("TEACHER",username,Map.of(),Map.of("username",username,"teacherId",user.getTeacherPublicId(),
            "admin",false,"permissions",user.getPermissions()));
        if (managed) {
            audit.detail(Map.of("source","management","defaultPermissions",user.getPermissions()));
        }
        audit.record((managed ? "user_created:" : "registration_succeeded:")+username,request);
        if (managed) audit.event("account","create","user_created");
        return user;
    }
}
