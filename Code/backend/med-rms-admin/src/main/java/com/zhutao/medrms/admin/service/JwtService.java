package com.zhutao.medrms.admin.service;

import com.zhutao.medrms.admin.domain.entity.User;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
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
 */
@Service
@RequiredArgsConstructor
public class JwtService {

    private static final String SECRET = "MedRMS-Secret-Key-For-JWT-Token-Generation-2024-MedRMS-Secret-Key-For-JWT";
    private static final long ACCESS_EXPIRATION_MS = 2 * 60 * 60 * 1000L;     // 2 小时
    private static final long REFRESH_EXPIRATION_MS = 7 * 24 * 60 * 60 * 1000L; // 7 天

    public static final String TOKEN_TYPE_ACCESS = "access";
    public static final String TOKEN_TYPE_REFRESH = "refresh";

    private final PermissionService permissionService;

    // tokenJti -> 过期时间（黑名单）
    private final Map<String, Long> blacklistedJti = new ConcurrentHashMap<>();

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
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
