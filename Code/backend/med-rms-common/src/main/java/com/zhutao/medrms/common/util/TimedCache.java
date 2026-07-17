package com.zhutao.medrms.common.util;

import java.util.Map;
import java.util.concurrent.*;
import java.util.function.Supplier;

public class TimedCache<K, V> {

    private final Map<K, CacheEntry<V>> cache = new ConcurrentHashMap<>();
    private final long ttlMillis;
    private final ScheduledExecutorService cleaner;

    public TimedCache(long ttlMillis) {
        this.ttlMillis = ttlMillis;
        this.cleaner = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "timed-cache-cleaner");
            t.setDaemon(true);
            return t;
        });
        this.cleaner.scheduleAtFixedRate(this::evictExpired, ttlMillis, ttlMillis, TimeUnit.MILLISECONDS);
    }

    public V get(K key, Supplier<V> loader) {
        CacheEntry<V> entry = cache.get(key);
        if (entry != null && !entry.isExpired()) {
            return entry.value;
        }
        V value = loader.get();
        cache.put(key, new CacheEntry<>(value, System.currentTimeMillis() + ttlMillis));
        return value;
    }

    public void invalidate(K key) {
        cache.remove(key);
    }

    public void invalidateAll() {
        cache.clear();
    }

    private void evictExpired() {
        long now = System.currentTimeMillis();
        cache.entrySet().removeIf(e -> e.getValue().isExpired(now));
    }

    private static class CacheEntry<V> {
        final V value;
        final long expiresAt;

        CacheEntry(V value, long expiresAt) {
            this.value = value;
            this.expiresAt = expiresAt;
        }

        boolean isExpired() {
            return System.currentTimeMillis() > expiresAt;
        }

        boolean isExpired(long now) {
            return now > expiresAt;
        }
    }
}
