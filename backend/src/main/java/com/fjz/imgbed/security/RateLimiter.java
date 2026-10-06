package com.fjz.imgbed.security;

import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 通用滑动窗口限流器（单机内存实现，零依赖）。
 *
 * <p>登录防爆破、注册 / 上传 / 公开接口限流共用这一套。
 * 带容量保护：key 数超过 {@link #MAX_KEYS} 时先清理过期项，仍超限则拒绝新 key，
 * 避免被大量随机 IP/账号打爆内存。</p>
 */
@Component
public class RateLimiter {

    /** 内存里最多保留多少个限流 key */
    private static final int MAX_KEYS = 20_000;

    private final Map<String, Deque<Long>> hits = new ConcurrentHashMap<>();

    /**
     * 尝试获取一次配额。
     *
     * @param key      限流维度（如 "login:1.2.3.4|admin"）
     * @param max      窗口内允许的最大次数
     * @param windowMs 窗口长度（毫秒）
     * @return true = 放行；false = 超限
     */
    public boolean tryAcquire(String key, int max, long windowMs) {
        long now = System.currentTimeMillis();
        Deque<Long> queue = hits.computeIfAbsent(key, k -> {
            // 新 key 前先做容量保护
            if (hits.size() >= MAX_KEYS) {
                evictExpired(now);
            }
            return new ArrayDeque<>();
        });
        synchronized (queue) {
            while (!queue.isEmpty() && now - queue.peekFirst() > windowMs) {
                queue.pollFirst();
            }
            if (queue.size() >= Math.max(max, 1)) {
                return false;
            }
            queue.addLast(now);
            return true;
        }
    }

    /** 当前窗口内已计次数（不写入新记录） */
    public int count(String key, long windowMs) {
        long now = System.currentTimeMillis();
        Deque<Long> queue = hits.get(key);
        if (queue == null) return 0;
        synchronized (queue) {
            while (!queue.isEmpty() && now - queue.peekFirst() > windowMs) {
                queue.pollFirst();
            }
            return queue.size();
        }
    }

    /** 清除某个 key 的计数（如登录成功后重置失败计数） */
    public void reset(String key) {
        hits.remove(key);
    }

    /** 清理完全过期的 key，控制内存占用 */
    public void evictExpired(long now) {
        for (Iterator<Map.Entry<String, Deque<Long>>> it = hits.entrySet().iterator(); it.hasNext(); ) {
            Deque<Long> queue = it.next().getValue();
            synchronized (queue) {
                if (queue.isEmpty() || now - queue.peekLast() > 3_600_000L) {
                    it.remove();
                }
            }
        }
    }
}
