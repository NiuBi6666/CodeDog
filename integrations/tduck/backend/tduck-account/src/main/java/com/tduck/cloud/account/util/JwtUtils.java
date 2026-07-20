package com.tduck.cloud.account.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import cn.hutool.core.util.ObjectUtil;
import com.tduck.cloud.envconfig.constant.ConfigConstants;
import com.tduck.cloud.envconfig.entity.SysEnvConfigEntity;
import com.tduck.cloud.envconfig.service.SysEnvConfigService;
import org.springframework.beans.factory.annotation.Autowired;

import javax.annotation.PostConstruct;
import java.util.HashMap;
import java.util.Map;
import java.util.Date;

/**
 * jwt工具类
 *
 * @author Mark sunlightcs@gmail.com
 */
@ConfigurationProperties(prefix = "platform.jwt")
@Component
@Slf4j
@Data
public class JwtUtils {

    private String secret;
    private long expire;
    private String header;

    @Autowired
    private SysEnvConfigService sysEnvConfigService;

    @PostConstruct
    public void init() {
        if (StrUtil.isBlank(secret)) {
            try {
                String dbSecret = null;
                SysEnvConfigEntity configEntity = sysEnvConfigService.getByKey(ConfigConstants.JWT_ENV_CONFIG);
                if (ObjectUtil.isNotNull(configEntity) && ObjectUtil.isNotNull(configEntity.getEnvValue())) {
                    dbSecret = (String) configEntity.getEnvValue().get("secret");
                }

                if (StrUtil.isBlank(dbSecret)) {
                    dbSecret = IdUtil.fastSimpleUUID() + IdUtil.fastSimpleUUID();
                    SysEnvConfigEntity newConfig = new SysEnvConfigEntity();
                    newConfig.setEnvKey(ConfigConstants.JWT_ENV_CONFIG);
                    Map<String, Object> envValue = new HashMap<>();
                    envValue.put("secret", dbSecret);
                    newConfig.setEnvValue(envValue);
                    sysEnvConfigService.saveConfig(newConfig);
                    log.warn("已在数据库中自动生成并保存了新的高强度随机 JWT 密钥。");
                }

                this.secret = dbSecret;
            } catch (Exception e) {
                // 回退至内存随机密钥
                this.secret = IdUtil.fastSimpleUUID() + IdUtil.fastSimpleUUID();
                log.error("从数据库初始化 JWT 密钥失败，已回退为内存随机密钥。", e);
            }
        }
    }

    /**
     * 生成jwt token
     */
    public String generateToken(long userId) {
        Date nowDate = new Date();
        //过期时间
        Date expireDate = new Date(nowDate.getTime() + expire * 1000);

        return Jwts.builder()
                .setHeaderParam("typ", "JWT")
                .setSubject(String.valueOf(userId))
                .setId(IdUtil.fastSimpleUUID())
                .setIssuedAt(nowDate)
                .setExpiration(expireDate)
                .signWith(SignatureAlgorithm.HS512, secret)
                .compact();
    }

    public Claims getClaimByToken(String token) {
        try {
            return Jwts.parser()
                    .setSigningKey(secret)
                    .parseClaimsJws(token)
                    .getBody();
        } catch (Exception e) {
            log.debug("validate is token com.tduck.cloud.wx.mp.error ", e);
            return null;
        }
    }

    /**
     * token是否过期
     *
     * @return true：过期
     */
    public boolean isTokenExpired(Date expiration) {
        return expiration.before(new Date());
    }




}
