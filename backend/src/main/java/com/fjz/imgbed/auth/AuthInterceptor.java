package com.fjz.imgbed.auth;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fjz.imgbed.common.Result;
import com.fjz.imgbed.user.entity.User;
import com.fjz.imgbed.user.mapper.UserMapper;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;

/**
 * 认证拦截器：支持两种方式——
 * <ol>
 *   <li>{@code Authorization: Bearer <JWT>}（前端页面登录后使用）</li>
 *   <li>{@code X-API-Key: <apiKey>}（程序化接口调用，API 页可查看/重置）</li>
 * </ol>
 * 无论哪种方式，都会实时校验账号是否被封禁。
 */
@Component
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {

    private final JwtUtil jwtUtil;
    private final UserMapper userMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // Tomcat 线程池会复用线程：每次进来先清掉上一个请求可能残留的上下文
        UserContext.clear();

        // 方式一：JWT
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            try {
                Claims claims = jwtUtil.parse(header.substring(7));
                UserContext.set(new UserContext.CurrentUser(
                        Long.valueOf(claims.getSubject()),
                        claims.get("username", String.class)));
                return checkActor(response, Long.valueOf(claims.getSubject()));
            } catch (Exception ignored) {
                // 令牌非法或过期，走 401 分支
            }
        }

        // 方式二：API Key（接口调用）
        String apiKey = request.getHeader("X-API-Key");
        if (apiKey != null && !apiKey.isBlank()) {
            User actor = userMapper.selectOne(new LambdaQueryWrapper<User>()
                    .eq(User::getApiKey, apiKey.trim())
                    .last("LIMIT 1"));
            if (actor != null) {
                UserContext.set(new UserContext.CurrentUser(actor.getId(), actor.getUsername()));
                return checkActor(response, actor.getId());
            }
            writeJson(response, 401, "API Key 无效，请检查后重试");
            return false;
        }

        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(objectMapper.writeValueAsString(Result.error(401, "未登录或登录已过期")));
        return false;
    }

    /** 令牌/密钥有效不代表账号还在用：封禁后立即失效 */
    private boolean checkActor(HttpServletResponse response, Long userId) throws Exception {
        User actor = userMapper.selectById(userId);
        if (actor == null) {
            UserContext.clear();
            writeJson(response, 401, "登录状态已失效，请重新登录");
            return false;
        }
        if (actor.isBanned()) {
            UserContext.clear();
            writeJson(response, 403, "该账号已被封禁");
            return false;
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        UserContext.clear();
    }

    private void writeJson(HttpServletResponse response, int status, String msg) throws Exception {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(objectMapper.writeValueAsString(Result.error(status, msg)));
    }
}
