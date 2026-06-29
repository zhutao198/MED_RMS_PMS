# Med-RMS API 限流接入指南

> 状态：永久化指南（实际集成按需启用）

## 目标

防止 API 滥用 / 暴力破解 / 限流 DDoS 攻击。

## 推荐方案：Bucket4j + Redis 分布式限流

### 1. 加依赖（pom.xml）
```xml
<dependency>
    <groupId>com.bucket4j</groupId>
    <artifactId>bucket4j-core</artifactId>
    <version>8.10.1</version>
</dependency>
<dependency>
    <groupId>com.bucket4j</groupId>
    <artifactId>bucket4j-redis</artifactId>
    <version>8.10.1</version>
</dependency>
```

### 2. 限流配置（application.yml）
```yaml
med-rms:
  ratelimit:
    enabled: true
    default:
      capacity: 100    # 桶容量
      refill-tokens: 100  # 每分钟补充
      refill-period-seconds: 60
    auth:
      capacity: 10     # 登录端点更严格
      refill-tokens: 10
      refill-period-seconds: 60
```

### 3. 限流 Filter
```java
@Component
public class RateLimitFilter extends OncePerRequestFilter {
    private final RateLimiter rateLimiter;  // Bucket4j + Redis

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain) {
        String clientIp = req.getHeader("X-Forwarded-For");
        if (clientIp == null) clientIp = req.getRemoteAddr();

        // 登录端点用更严格限流
        boolean isAuth = req.getRequestURI().startsWith("/api/auth/login");
        Bucket bucket = isAuth
            ? buckets.computeIfAbsent(clientIp + ":auth", k -> createAuthBucket())
            : buckets.computeIfAbsent(clientIp, k -> createDefaultBucket());

        if (bucket.tryConsume(1)) {
            chain.doFilter(req, res);
        } else {
            res.setStatus(429);
            res.setHeader("Retry-After", "60");
            res.getWriter().write("{\"code\":\"RATE001\",\"message\":\"请求过于频繁\"}");
        }
    }
}
```

### 4. 注册 Filter
```java
@Configuration
public class WebConfig implements WebMvcConfigurer {
    @Bean
    public FilterRegistrationBean<RateLimitFilter> rateLimitFilter(RateLimitFilter f) {
        FilterRegistrationBean<RateLimitFilter> reg = new FilterRegistrationBean<>(f);
        reg.addUrlPatterns("/api/*");
        reg.setOrder(1);  // 在 Security Filter 之后
        return reg;
    }
}
```

### 5. 单元测试
```java
@Test
void rateLimit_exceeded() {
    // 模拟 110 个请求
    for (int i = 0; i < 100; i++) {
        filter.doFilter(req, res, chain);
    }
    // 第 101 个应被拒
    filter.doFilter(req, res, chain);
    assertEquals(429, res.getStatus());
}
```

## 推荐限流档位

| 端点 | 容量 | 补充速率 | 理由 |
|------|------|----------|------|
| `/api/auth/login` | 10 | 10/分钟 | 防暴力破解 |
| `/api/auth/refresh` | 30 | 30/分钟 | 正常用户够用 |
| 其他 API | 100 | 100/分钟 | 正常用户感知不到 |
| 静态资源 | 1000 | 1000/分钟 | 不限流 |

## Redis 分布式

Bucket4j-Redis 多个实例共享限流配额：
- 单实例限流：Bucket4j in-memory
- 多实例：Bucket4j + Redis（Lua 脚本原子操作）

## 告警

限流触发时：
- 写日志（INFO 级别，userId + path + 限流档位）
- 触发 Prometheus counter（`rate_limit_exceeded_total{path="/api/auth/login"}`）
- 可选：Sentry 上报异常 IP

## 何时启用

- 生产部署到公网（必须）
- 内部部署（可选）
- 当前 v1.56 沙箱就绪，**可暂缓启用**

## 已知限制

- 简单 IP 限流可能被 NAT 误伤（共享 IP 真实用户都被限）
- 高级方案：基于 userId + IP 双维度 + 行为分析（异常）
- 分布式限流需要 Redis（已配），额外运维
