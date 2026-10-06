package com.fjz.imgbed.user.controller;

import com.fjz.imgbed.common.BizException;
import com.fjz.imgbed.common.Result;
import com.fjz.imgbed.security.ClientIps;
import com.fjz.imgbed.security.RateLimiter;
import com.fjz.imgbed.user.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;
    private final RateLimiter rateLimiter;

    /** 同一 IP+账号 连续失败上限（默认 5 次） */
    @Value("${imgbed.security.login-max-fails:5}")
    private int loginMaxFails;
    /** 失败计数窗口 / 锁定时长（分钟，默认 10） */
    @Value("${imgbed.security.login-lock-minutes:10}")
    private int loginLockMinutes;
    /** 同一 IP 每分钟最多尝试登录次数（默认 20） */
    @Value("${imgbed.security.login-ip-max-per-minute:20}")
    private int loginIpMaxPerMinute;
    /** 同一 IP 每小时最多注册账号数（默认 10） */
    @Value("${imgbed.security.register-max-per-hour:10}")
    private int registerMaxPerHour;

    @PostMapping("/register")
    public Result<Map<String, Object>> register(@Validated @RequestBody RegisterReq req,
                                                HttpServletRequest request) {
        String ip = ClientIps.of(request);
        if (!rateLimiter.tryAcquire("register:ip:" + ip, registerMaxPerHour, 3_600_000L)) {
            throw new BizException(429, "注册太频繁啦，请稍后再试");
        }
        return Result.ok(userService.register(req.getUsername(), req.getPassword(), req.getNickname()));
    }

    @PostMapping("/login")
    public Result<Map<String, Object>> login(@Validated @RequestBody LoginReq req,
                                             HttpServletRequest request) {
        String ip = ClientIps.of(request);
        // ① IP 维度：每分钟总尝试次数，挡住批量扫号
        if (!rateLimiter.tryAcquire("login:ip:" + ip, loginIpMaxPerMinute, 60_000L)) {
            throw new BizException(429, "尝试太频繁啦，请 1 分钟后再试");
        }
        // ② 账号+IP 维度：连续失败超限后锁定，挡住定向爆破
        String failKey = "login:fail:" + ip + "|" + req.getUsername();
        long lockMs = loginLockMinutes * 60_000L;
        if (rateLimiter.count(failKey, lockMs) >= loginMaxFails) {
            throw new BizException(429, "失败次数过多，账号已临时锁定，请 " + loginLockMinutes + " 分钟后再试");
        }
        try {
            Map<String, Object> result = userService.login(req.getUsername(), req.getPassword());
            rateLimiter.reset(failKey);
            return Result.ok(result);
        } catch (RuntimeException e) {
            rateLimiter.tryAcquire(failKey, Integer.MAX_VALUE, lockMs);
            throw e;
        }
    }

    @Data
    public static class RegisterReq {
        @NotBlank(message = "用户名不能为空")
        @Pattern(regexp = "^[a-zA-Z0-9_]{3,20}$", message = "用户名需为 3-20 位字母/数字/下划线")
        private String username;

        @NotBlank(message = "密码不能为空")
        @Size(min = 6, max = 32, message = "密码长度需为 6-32 位")
        private String password;

        @Size(max = 20, message = "昵称最长 20 个字符")
        private String nickname;
    }

    @Data
    public static class LoginReq {
        @NotBlank(message = "用户名不能为空")
        @Size(max = 32)
        private String username;

        @NotBlank(message = "密码不能为空")
        @Size(max = 64)
        private String password;
    }
}
