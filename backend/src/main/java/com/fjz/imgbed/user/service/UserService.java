package com.fjz.imgbed.user.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fjz.imgbed.auth.AdminGuard;
import com.fjz.imgbed.auth.JwtUtil;
import com.fjz.imgbed.common.BizException;
import com.fjz.imgbed.user.entity.User;
import com.fjz.imgbed.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserMapper userMapper;
    private final JwtUtil jwtUtil;
    private final AdminGuard adminGuard;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    public Map<String, Object> register(String username, String password, String nickname) {
        Long count = userMapper.selectCount(
                new LambdaQueryWrapper<User>().eq(User::getUsername, username));
        if (count != null && count > 0) {
            throw new BizException("用户名已被注册");
        }
        User user = new User();
        user.setUsername(username);
        user.setPassword(encoder.encode(password));
        user.setNickname(nickname == null || nickname.isBlank() ? username : nickname);
        user.setStatus(1);
        // API Key 不再随注册自动生成：由用户到「API 接口」页手动生成，且完整密钥仅展示一次
        user.setCreateTime(LocalDateTime.now());
        userMapper.insert(user);
        return loginResult(user);
    }

    public Map<String, Object> login(String username, String password) {
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getUsername, username));
        if (user == null || !encoder.matches(password, user.getPassword())) {
            throw new BizException("用户名或密码错误");
        }
        if (user.isBanned()) {
            throw new BizException(403, "该账号已被封禁，无法登录");
        }
        return loginResult(user);
    }

    /**
     * 启动引导：确保管理员账号存在。账号密码为内置固定值，不存在才创建，
     * 已存在则保持原样（不覆盖用户可能改过的密码）。
     *
     * @return "created" / "exists" / "skipped"
     */
    public String bootstrapAdmin(String username, String rawPassword) {
        if (username == null || username.isBlank() || rawPassword == null || rawPassword.isBlank()) {
            return "skipped";
        }
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getUsername, username));
        if (user == null) {
            User admin = new User();
            admin.setUsername(username);
            admin.setPassword(encoder.encode(rawPassword));
            admin.setNickname("管理员");
            admin.setStatus(1);
            admin.setCreateTime(LocalDateTime.now());
            userMapper.insert(admin);
            return "created";
        }
        return "exists";
    }

    private Map<String, Object> loginResult(User user) {
        String token = jwtUtil.generate(user.getId(), user.getUsername());
        return Map.of(
                "token", token,
                "user", Map.of(
                        "id", user.getId(),
                        "username", user.getUsername(),
                        "nickname", user.getNickname() == null ? "" : user.getNickname(),
                        // 前端据此决定是否显示「仓库管理」入口
                        "admin", adminGuard.isAdmin(user.getUsername())));
    }
}
