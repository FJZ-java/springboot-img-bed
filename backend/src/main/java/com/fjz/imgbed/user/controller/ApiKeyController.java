package com.fjz.imgbed.user.controller;

import com.fjz.imgbed.auth.UserContext;
import com.fjz.imgbed.common.BizException;
import com.fjz.imgbed.common.Result;
import com.fjz.imgbed.user.entity.User;
import com.fjz.imgbed.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.SecureRandom;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 个人 API 密钥：每个用户一把，用于程序化接口调用（X-API-Key 头认证）。
 *
 * <p>安全设计：<b>完整密钥只在「生成」那一次的响应里返回，之后任何接口只给掩码</b>。
 * 因此密钥不会自动生成（注册 / 管理员建号都不带），需要用户到「API 接口」页主动生成；
 * 生成后前端只展示一次并提示保存，一旦关闭就无法再从服务端取回完整值，泄露后只能重置。</p>
 */
@RestController
@RequestMapping("/api/apikey")
@RequiredArgsConstructor
public class ApiKeyController {

    private static final String PREFIX = "img_sk_";
    private static final String HEX = "0123456789abcdef";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserMapper userMapper;

    /** 查看自己的 API Key 状态：只返回「是否已生成」与掩码，绝不回传完整密钥 */
    @GetMapping
    public Result<Map<String, Object>> view() {
        User user = currentUser();
        return Result.ok(statusInfo(user));
    }

    /** 生成（或重置）API Key：旧 key 立即失效；完整 key 仅在本响应中出现一次，请立即保存 */
    @PostMapping("/generate")
    public Result<Map<String, Object>> generate() {
        User user = currentUser();
        String key = newKey();
        user.setApiKey(key);
        userMapper.updateById(user);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("apiKey", key);
        m.put("masked", mask(key));
        return Result.ok(m);
    }

    private User currentUser() {
        User user = userMapper.selectById(UserContext.requireUserId());
        if (user == null) {
            throw new BizException(401, "登录状态已失效，请重新登录");
        }
        return user;
    }

    /** 状态信息：hasKey 表示是否已生成，masked 为脱敏后的掩码（无 key 时为 null） */
    private Map<String, Object> statusInfo(User user) {
        boolean has = user.getApiKey() != null && !user.getApiKey().isBlank();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("hasKey", has);
        m.put("masked", has ? mask(user.getApiKey()) : null);
        return m;
    }

    /** 脱敏展示：前缀 + 后 4 位 */
    private String mask(String key) {
        return PREFIX + "••••••••••••••••" + key.substring(key.length() - 4);
    }

    /** img_sk_ + 48 位随机十六进制（192 bit 熵，不可猜测） */
    private String newKey() {
        StringBuilder sb = new StringBuilder(PREFIX);
        for (int i = 0; i < 48; i++) {
            sb.append(HEX.charAt(RANDOM.nextInt(HEX.length())));
        }
        return sb.toString();
    }
}
