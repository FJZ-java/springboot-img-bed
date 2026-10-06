package com.fjz.imgbed.guest;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 游客上传的频率限制（单机内存实现，够用且零依赖）。
 * 默认每 IP 每小时 30 次，可通过 imgbed.guest.max-per-hour 调整。
 */
@Slf4j
@Component
public class GuestRateLimiter {

    private static final long WINDOW_MS = 60 * 60 * 1000L;

    private final int maxPerHour;
    private final Map<String, Deque<Long>> hits = new ConcurrentHashMap<>();

    public GuestRateLimiter(@Value("${imgbed.guest.max-per-hour:30}") int maxPerHour) {
        this.maxPerHour = Math.max(maxPerHour, 1);
    }

    /** 返回 true 表示允许本次上传 */
    public boolean tryAcquire(String ip) {
        long now = System.currentTimeMillis();
        Deque<Long> queue = hits.computeIfAbsent(ip, k -> new ArrayDeque<>());
        synchronized (queue) {
            while (!queue.isEmpty() && now - queue.peekFirst() > WINDOW_MS) {
                queue.pollFirst();
            }
            if (queue.size() >= maxPerHour) {
                return false;
            }
            queue.addLast(now);
            return true;
        }
    }

    /** 顺手清理过期条目，避免长期运行内存增长 */
    public void evictExpired() {
        long now = System.currentTimeMillis();
        for (Iterator<Map.Entry<String, Deque<Long>>> it = hits.entrySet().iterator(); it.hasNext(); ) {
            Deque<Long> queue = it.next().getValue();
            synchronized (queue) {
                if (queue.isEmpty() || now - queue.peekLast() > WINDOW_MS) {
                    it.remove();
                }
            }
        }
    }

    public int getMaxPerHour() {
        return maxPerHour;
    }
}
