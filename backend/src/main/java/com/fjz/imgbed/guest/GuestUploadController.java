package com.fjz.imgbed.guest;

import com.fjz.imgbed.common.BizException;
import com.fjz.imgbed.common.Result;
import com.fjz.imgbed.security.ClientIps;
import com.fjz.imgbed.upload.ImageUploadService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 游客上传：无需登录即可体验，图片真实上传到 GitHub 仓库并返回 CDN 链接，
 * 但不写入上传记录（登录后才会有「我的上传记录」）。
 */
@RestController
@RequestMapping("/api/guest")
@RequiredArgsConstructor
public class GuestUploadController {

    private final ImageUploadService imageUploadService;
    private final GuestRateLimiter rateLimiter;

    @Value("${imgbed.guest.enabled:true}")
    private boolean enabled;

    @PostMapping("/upload")
    public Result<GuestUploadResult> upload(@RequestParam("file") MultipartFile file,
                                            HttpServletRequest request) {
        if (!enabled) {
            throw new BizException(403, "游客上传已关闭，请登录后上传");
        }
        String ip = ClientIps.of(request);
        if (!rateLimiter.tryAcquire(ip)) {
            throw new BizException(429, "游客上传太频繁啦，请稍后再试，或登录后继续");
        }
        rateLimiter.evictExpired();
        ImageUploadService.StoredImage img = imageUploadService.store(file);
        return Result.ok(new GuestUploadResult(img.cdnUrl(), img.githubPath(), img.originName(), img.size()));
    }

    public record GuestUploadResult(String cdnUrl, String path, String originName, long size) {
    }
}
