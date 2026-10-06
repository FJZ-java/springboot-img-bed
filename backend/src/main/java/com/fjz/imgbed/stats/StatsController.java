package com.fjz.imgbed.stats;

import com.fjz.imgbed.common.Result;
import com.fjz.imgbed.github.GitHubService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 公开统计接口（首页展示用），结果缓存 5 分钟，避免频繁打 GitHub API。
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class StatsController {

    private static final long TTL_MS = 5 * 60 * 1000L;

    private final GitHubService gitHubService;
    private volatile GitHubService.RepoStats cache;
    private volatile long cachedAt = 0L;

    @GetMapping("/stats")
    public Result<Map<String, Object>> stats() {
        GitHubService.RepoStats s = cache;
        if (s == null || System.currentTimeMillis() - cachedAt > TTL_MS) {
            s = gitHubService.stats();
            cache = s;
            cachedAt = System.currentTimeMillis();
        }
        return Result.ok(Map.of(
                "images", s.images(),
                "size", s.size()));
    }
}
