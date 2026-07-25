package com.zhutao.medrms.admin.service;

import com.zhutao.medrms.admin.domain.entity.User;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * v1.47 BUG #107/108 修复：JWT 双令牌（access/refresh）+ 黑名单机制
 * - access  短时（2h）用于业务接口
 * - refresh 长时（7d）只能用于换新 access
 * - 黑名单：登出/改密后失效令牌，TTL 至过期
 *
 * v1.79 R223.1：JWT 密钥轮换 — 删除源码默认值，强制环境变量注入；
 *                启动期校验：非 dev/test profile 下使用默认密钥直接启动失败。
 */
@Service
@RequiredArgsConstructor
public class JwtService {

    /**
     * 默认密钥仅供「dev / test」profile 下 Spring 启动兜底使用，单元测试
     * （new JwtService(...)）也可直接使用。
     *
     * 生产部署务必通过环境变量 JWT_SECRET 或 med-rms.jwt.secret 注入 ≥32 字符
     * 的强随机密钥；若仍使用默认值，@PostConstruct 会直接拒绝启动。
     */
    private static final String DEFAULT_DEV_SECRET =
        "MedRMS-Dev-Test-Only-Secret-Do-Not-Use-In-Production-32chars";

    @Value("${med-rms.jwt.secret}")
    private String secret = DEFAULT_DEV_SECRET;

    private static final long ACCESS_EXPIRATION_MS = 2 * 60 * 60 * 1000L;     // 2 小时
    private static final long REFRESH_EXPIRATION_MS = 7 * 24 * 60 * 60 * 1000L; // 7 天

    public static final String TOKEN_TYPE_ACCESS = "access";
    public static final String TOKEN_TYPE_REFRESH = "refresh";

    private final PermissionService permissionService;
    private final Environment environment;

    // tokenJti -> 过期时间（黑名单）
    private final Map<String, Long> blacklistedJti = new ConcurrentHashMap<>();

    /**
     * R223.1：启动期密钥校验。
     * 1) 密钥 ≥32 字符（HS512 最低要求）
     * 2) 非 dev / test profile 下，使用默认密钥直接拒绝启动
     */
    @PostConstruct
    void validateSecret() {
        if (secret == null || secret.length() < 32) {
            throw new IllegalStateException(
                "med-rms.jwt.secret 必须 ≥32 字符（HS512 要求），当前长度=" +
                (secret == null ? "null" : String.valueOf(secret.length())));
        }
        String[] activeProfiles = environment.getActiveProfiles();
        boolean isDevProfile = activeProfiles.length == 0 ||
            Arrays.asList(activeProfiles).contains("dev") ||
            Arrays.asList(activeProfiles).contains("test");
        if (!isDevProfile && DEFAULT_DEV_SECRET.equals(secret)) {
            throw new IllegalStateException(
                "生产环境禁止使用默认 JWT 密钥！请通过环境变量 JWT_SECRET 或 " +
                "med-rms.jwt.secret 注入强随机密钥（≥32 字符）。" +
                "当前激活 profile=" + Arrays.toString(activeProfiles));
        }
        System.out.println("[JwtService] 密钥校验通过 profile=" +
            Arrays.toString(activeProfiles) + ", secret 前缀=" +
            (secret.length() > 8 ? secret.substring(0, 8) + "..." : secret));
    }

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String generateAccessToken(User user) {
        return buildToken(user, TOKEN_TYPE_ACCESS, ACCESS_EXPIRATION_MS);
    }

    public String generateRefreshToken(User user) {
        return buildToken(user, TOKEN_TYPE_REFRESH, REFRESH_EXPIRATION_MS);
    }

    /**
     * v1.47 BUG #108 修复：保留旧 generateToken（生成 access）以兼容既有调用
     */
    public String generateToken(User user) {
        return generateAccessToken(user);
    }

    private String buildToken(User user, String tokenType, long expirationMs) {
        List<String> roleCodes = permissionService.getUserRoleCodes(user.getId());
        Set<String> permCodes = permissionService.getUserPermCodes(user.getId());

        String jti = UUID.randomUUID().toString();
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", user.getId());
        claims.put("username", user.getUsername());
        claims.put("realName", user.getRealName());
        claims.put("role", user.getRole() != null ? user.getRole() : "USER");
        claims.put("roles", roleCodes);
        claims.put("permissions", permCodes);
        claims.put("tokenType", tokenType);
        claims.put("jti", jti);

        return Jwts.builder()
                .claims(claims)
                .subject(String.valueOf(user.getId()))
                .id(jti)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expirationMs))
                .signWith(getSigningKey())
                .compact();
    }

    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public Long extractUserId(String token) {
        Claims claims = parseToken(token);
        return Long.valueOf(claims.getSubject());
    }

    public String extractUsername(String token) {
        Claims claims = parseToken(token);
        return claims.get("username", String.class);
    }

    public String extractJti(String token) {
        Claims claims = parseToken(token);
        return claims.getId();
    }

    public String extractTokenType(String token) {
        Claims claims = parseToken(token);
        Object t = claims.get("tokenType");
        return t == null ? TOKEN_TYPE_ACCESS : t.toString();
    }

    public Date extractExpiration(String token) {
        return parseToken(token).getExpiration();
    }

    @SuppressWarnings("unchecked")
    public Set<String> extractPermissions(Claims claims) {
        Object perms = claims.get("permissions");
        if (perms instanceof java.util.Collection<?> coll) {
            Set<String> result = new HashSet<>();
            for (Object o : coll) {
                if (o != null) result.add(o.toString());
            }
            return result;
        }
        return Set.of();
    }

    public boolean isTokenExpired(String token) {
        try {
            Claims claims = parseToken(token);
            return claims.getExpiration().before(new Date());
        } catch (ExpiredJwtException e) {
            return true;
        }
    }

    /**
     * v1.47 BUG #108 修复：登出/改密时把 jti 加入黑名单，验证时拒绝
     */
    public void blacklist(String token) {
        try {
            Claims claims = parseToken(token);
            String jti = claims.getId();
            long ttl = claims.getExpiration().getTime() - System.currentTimeMillis();
            if (ttl > 0 && jti != null) {
                blacklistedJti.put(jti, claims.getExpiration().getTime());
            }
        } catch (Exception e) {
            // 过期 token 无需再黑名单
        }
    }

    public boolean isBlacklisted(String token) {
        try {
            Claims claims = parseToken(token);
            String jti = claims.getId();
            if (jti == null) return false;
            Long exp = blacklistedJti.get(jti);
            if (exp == null) return false;
            if (exp < System.currentTimeMillis()) {
                blacklistedJti.remove(jti);
                return false;
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public long getAccessExpirationMs() {
        return ACCESS_EXPIRATION_MS;
    }

    public long getRefreshExpirationMs() {
        return REFRESH_EXPIRATION_MS;
    }
}
