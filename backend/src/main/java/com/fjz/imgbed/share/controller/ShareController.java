package com.fjz.imgbed.share.controller;

import com.fjz.imgbed.auth.UserContext;
import com.fjz.imgbed.common.BizException;
import com.fjz.imgbed.common.Result;
import com.fjz.imgbed.security.ClientIps;
import com.fjz.imgbed.security.RateLimiter;
import com.fjz.imgbed.share.service.ShareService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 分享链接接口。
 *
 * <p>{@code GET /api/share/{code}} 为公开接口（WebConfig 已放行），任何人拿到链接都能查看，
 * 带 IP 限流防止恶意刷浏览量；创建 / 取消分享需要登录，且只能操作自己的记录。</p>
 */
@RestController
@RequestMapping("/api/share")
@RequiredArgsConstructor
public class ShareController {

    private final ShareService shareService;
    private final RateLimiter rateLimiter;

    /** 公开接口同一 IP 每分钟最多访问次数（默认 60） */
    @Value("${imgbed.security.public-max-per-minute:60}")
    private int publicMaxPerMinute;

    /** 公开读取分享内容（限流，防刷浏览量与拖库） */
    @GetMapping("/{code}")
    public Result<Map<String, Object>> detail(@PathVariable String code,
                                              HttpServletRequest request) {
        if (!rateLimiter.tryAcquire("public:share:" + ClientIps.of(request), publicMaxPerMinute, 60_000L)) {
            throw new BizException(429, "访问太频繁啦，请稍后再试");
        }
        return Result.ok(shareService.detail(code));
    }

    /** 为某条上传记录生成分享链接（幂等：已有则直接返回） */
    @PostMapping("/record/{id}")
    public Result<Map<String, Object>> create(@PathVariable Long id) {
        return Result.ok(shareService.create(UserContext.requireUserId(), id));
    }

    /** 取消分享 */
    @DeleteMapping("/record/{id}")
    public Result<Void> cancel(@PathVariable Long id) {
        shareService.cancel(UserContext.requireUserId(), id);
        return Result.ok();
    }

    /** 查询某条记录当前的分享链接（无则 data 为 null） */
    @GetMapping("/record/{id}")
    public Result<Map<String, Object>> currentOf(@PathVariable Long id) {
        return Result.ok(shareService.currentOf(id));
    }
}
