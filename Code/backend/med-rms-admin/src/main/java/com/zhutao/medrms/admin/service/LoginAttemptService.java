package com.zhutao.medrms.admin.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class LoginAttemptService {

    private static final String FAIL_PREFIX = "login:fail:";
    private static final String LOCK_PREFIX = "login:lock:";
    private static final int MAX_ATTEMPTS = 10;
    private static final long LOCK_DURATION_MINUTES = 30;
    private static final long FAIL_TTL_MINUTES = 30;

    private final StringRedisTemplate redisTemplate;

    public boolean isLocked(String username) {
        Boolean locked = redisTemplate.hasKey(LOCK_PREFIX + username);
        if (Boolean.TRUE.equals(locked)) {
            Long ttl = redisTemplate.getExpire(LOCK_PREFIX + username, TimeUnit.SECONDS);
            log.warn("账号已锁定: username={}, remainingLockSeconds={}", username, ttl);
            return true;
        }
        return false;
    }

    public long getRemainingLockSeconds(String username) {
        Long ttl = redisTemplate.getExpire(LOCK_PREFIX + username, TimeUnit.SECONDS);
        return ttl != null && ttl > 0 ? ttl : 0;
    }

    public void recordFailedAttempt(String username) {
        String key = FAIL_PREFIX + username;
        Long attempts = redisTemplate.opsForValue().increment(key);
        if (attempts == null) attempts = 1L;

        if (attempts == 1) {
            redisTemplate.expire(key, FAIL_TTL_MINUTES, TimeUnit.MINUTES);
        }

        log.warn("登录失败: username={}, attempts={}/{}", username, attempts, MAX_ATTEMPTS);

        if (attempts >= MAX_ATTEMPTS) {
            String lockKey = LOCK_PREFIX + username;
            redisTemplate.opsForValue().set(lockKey, "1", LOCK_DURATION_MINUTES, TimeUnit.MINUTES);
            redisTemplate.delete(key);
            log.warn("账号已达最大失败次数，已锁定: username={}, duration={}min", username, LOCK_DURATION_MINUTES);
        }
    }

    public void resetAttempts(String username) {
        redisTemplate.delete(FAIL_PREFIX + username);
        redisTemplate.delete(LOCK_PREFIX + username);
    }
}
