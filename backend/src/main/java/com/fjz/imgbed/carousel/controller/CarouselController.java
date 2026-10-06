package com.fjz.imgbed.carousel.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fjz.imgbed.auth.AdminGuard;
import com.fjz.imgbed.carousel.entity.CarouselSlide;
import com.fjz.imgbed.carousel.mapper.CarouselSlideMapper;
import com.fjz.imgbed.common.BizException;
import com.fjz.imgbed.common.Result;
import com.fjz.imgbed.security.ClientIps;
import com.fjz.imgbed.security.RateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 轮播广告管理。
 *
 * <p>{@code GET /api/carousel} 公开（WebConfig 放行），只返回启用中的轮播图，
 * 供上传页等广告位展示；增删改走 {@code /api/admin/carousel}，仅管理员可用。</p>
 */
@RestController
@RequiredArgsConstructor
public class CarouselController {

    private final AdminGuard adminGuard;
    private final CarouselSlideMapper mapper;
    private final RateLimiter rateLimiter;

    /** 公开接口同一 IP 每分钟最多访问次数 */
    @Value("${imgbed.security.public-max-per-minute:60}")
    private int publicMaxPerMinute;

    /** 公开：启用中的轮播图（按 sort_order 升序，带 IP 限流） */
    @GetMapping("/api/carousel")
    public Result<List<CarouselSlide>> publicList(HttpServletRequest request) {
        if (!rateLimiter.tryAcquire("public:carousel:" + ClientIps.of(request), publicMaxPerMinute, 60_000L)) {
            throw new BizException(429, "访问太频繁啦，请稍后再试");
        }
        List<CarouselSlide> list = mapper.selectList(new LambdaQueryWrapper<CarouselSlide>()
                .eq(CarouselSlide::getEnabled, 1)
                .orderByAsc(CarouselSlide::getSortOrder)
                .orderByAsc(CarouselSlide::getId));
        // 公开接口不暴露管理字段
        list.forEach(s -> s.setCreateTime(null));
        return Result.ok(list);
    }

    /** 管理：全部轮播图（含停用） */
    @GetMapping("/api/admin/carousel")
    public Result<List<CarouselSlide>> list() {
        adminGuard.requireAdmin();
        return Result.ok(mapper.selectList(new LambdaQueryWrapper<CarouselSlide>()
                .orderByAsc(CarouselSlide::getSortOrder)
                .orderByAsc(CarouselSlide::getId)));
    }

    /** 管理：新增轮播图 */
    @PostMapping("/api/admin/carousel")
    public Result<CarouselSlide> create(@RequestBody Map<String, Object> body) {
        adminGuard.requireAdmin();
        CarouselSlide slide = new CarouselSlide();
        apply(slide, body, true);
        slide.setCreateTime(LocalDateTime.now());
        mapper.insert(slide);
        return Result.ok(slide);
    }

    /** 管理：编辑轮播图 */
    @PutMapping("/api/admin/carousel/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        adminGuard.requireAdmin();
        CarouselSlide slide = mapper.selectById(id);
        if (slide == null) {
            throw new BizException(404, "轮播图不存在");
        }
        apply(slide, body, false);
        mapper.updateById(slide);
        return Result.ok();
    }

    /** 管理：删除轮播图（只删配置，图床里的图片文件不受影响） */
    @DeleteMapping("/api/admin/carousel/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        adminGuard.requireAdmin();
        CarouselSlide slide = mapper.selectById(id);
        if (slide == null) {
            throw new BizException(404, "轮播图不存在");
        }
        mapper.deleteById(id);
        return Result.ok();
    }

    /** 把请求体里的字段套到实体上；create=true 时强制校验图片地址 */
    private void apply(CarouselSlide slide, Map<String, Object> body, boolean create) {
        String imageUrl = text(body.get("imageUrl"));
        if (create && (imageUrl == null || imageUrl.isBlank())) {
            throw new BizException("请提供图片地址");
        }
        if (imageUrl != null && !imageUrl.isBlank()) {
            slide.setImageUrl(checkUrl(imageUrl, "图片地址"));
        }
        if (body.containsKey("title")) {
            String title = text(body.get("title"));
            if (title != null && title.length() > 100) {
                throw new BizException("标题最长 100 个字符");
            }
            slide.setTitle(title);
        }
        if (body.containsKey("linkUrl")) {
            String linkUrl = text(body.get("linkUrl"));
            slide.setLinkUrl(linkUrl == null || linkUrl.isBlank() ? linkUrl : checkUrl(linkUrl, "跳转链接"));
        }
        if (body.get("sortOrder") instanceof Number n) {
            slide.setSortOrder(n.intValue());
        }
        Object enabled = body.get("enabled");
        if (enabled != null) {
            slide.setEnabled(toInt(enabled, 1));
        } else if (create) {
            slide.setEnabled(1);
        }
    }

    /** URL 安全校验：只允许 http/https，防 javascript: 等伪协议注入 */
    private String checkUrl(String url, String label) {
        if (url.length() > 500) {
            throw new BizException(label + "最长 500 个字符");
        }
        String lower = url.toLowerCase(java.util.Locale.ROOT);
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) {
            throw new BizException(label + "必须以 http:// 或 https:// 开头");
        }
        return url;
    }

    private String text(Object v) {
        return v == null ? null : String.valueOf(v).trim();
    }

    private int toInt(Object v, int fallback) {
        if (v instanceof Boolean b) {
            return b ? 1 : 0;
        }
        if (v instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(v));
        } catch (Exception e) {
            return fallback;
        }
    }
}
