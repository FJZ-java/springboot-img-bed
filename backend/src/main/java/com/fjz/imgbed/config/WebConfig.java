package com.fjz.imgbed.config;

import com.fjz.imgbed.auth.AuthInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;

    /** 允许的开发来源（逗号分隔），生产环境通过 CORS_ALLOWED_ORIGINS 覆盖为真实域名 */
    @Value("${imgbed.cors.allowed-origins}")
    private String[] allowedOrigins;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/api/**")
                // 游客上传接口免登录（真上传但不写入记录）；首页统计接口公开
                // /api/share/* 为分享链接公开读取（创建/取消走 /api/share/record/**，仍需登录）
                // /api/carousel 为启用中轮播图的公开读取（管理走 /api/admin/carousel，仍需登录）
                .excludePathPatterns(
                        "/api/auth/**", "/api/health", "/api/guest/**", "/api/stats",
                        "/api/share/*", "/api/carousel");
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                // 本机任意端口（Vite 端口被占用时会自动 +1，如 5173 → 5174），
                // 生产环境请用 CORS_ALLOWED_ORIGINS 指定真实域名，不要开放通配符
                .allowedOriginPatterns(allowedOrigins)
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }
}
