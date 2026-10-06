package com.fjz.imgbed.user.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fjz.imgbed.auth.AdminGuard;
import com.fjz.imgbed.auth.UserContext;
import com.fjz.imgbed.common.BizException;
import com.fjz.imgbed.common.Result;
import com.fjz.imgbed.record.entity.UploadRecord;
import com.fjz.imgbed.record.mapper.UploadRecordMapper;
import com.fjz.imgbed.user.entity.User;
import com.fjz.imgbed.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 用户管理（仅管理员）。
 *
 * <p>提供用户列表、新增用户、修改昵称、重置密码、删除用户能力。
 * 删除用户仅删除账号与其上传记录元数据，GitHub 仓库中的文件不受影响。</p>
 */
@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private final AdminGuard adminGuard;
    private final UserMapper userMapper;
    private final UploadRecordMapper recordMapper;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    /** 用户列表（含图片数、是否管理员） */
    @GetMapping
    public Result<List<Map<String, Object>>> list() {
        adminGuard.requireAdmin();
        List<User> users = userMapper.selectList(
                new LambdaQueryWrapper<User>().orderByAsc(User::getId));

        // 每个用户的上传图片数
        List<UploadRecord> records = recordMapper.selectList(
                new LambdaQueryWrapper<UploadRecord>()
                        .select(UploadRecord::getUserId, UploadRecord::getId));
        Map<Long, Long> imageCount = records.stream()
                .collect(Collectors.groupingBy(UploadRecord::getUserId, Collectors.counting()));

        List<Map<String, Object>> data = users.stream().map(u -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", u.getId());
            m.put("username", u.getUsername());
            m.put("nickname", u.getNickname() == null ? "" : u.getNickname());
            m.put("admin", adminGuard.isAdmin(u.getUsername()));
            m.put("status", u.getStatus() == null ? 1 : u.getStatus());
            m.put("banned", u.isBanned());
            m.put("imageCount", imageCount.getOrDefault(u.getId(), 0L));
            m.put("createTime", u.getCreateTime() == null ? "" : String.valueOf(u.getCreateTime()).replace('T', ' '));
            return m;
        }).collect(Collectors.toList());
        return Result.ok(data);
    }

    /** 新增用户 */
    @PostMapping
    public Result<Void> create(@RequestBody Map<String, String> body) {
        adminGuard.requireAdmin();
        String username = trim(body.get("username"));
        String password = trim(body.get("password"));
        String nickname = trim(body.get("nickname"));
        if (username == null || !username.matches("^[a-zA-Z0-9_]{3,20}$")) {
            throw new BizException("用户名需为 3-20 位字母、数字或下划线");
        }
        if (password == null || password.length() < 6 || password.length() > 32) {
            throw new BizException("密码长度需为 6-32 位");
        }
        Long count = userMapper.selectCount(
                new LambdaQueryWrapper<User>().eq(User::getUsername, username));
        if (count != null && count > 0) {
            throw new BizException("用户名已存在");
        }
        User user = new User();
        user.setUsername(username);
        user.setPassword(encoder.encode(password));
        user.setNickname(nickname == null || nickname.isBlank() ? username : nickname);
        user.setStatus(1);
        user.setCreateTime(LocalDateTime.now());
        userMapper.insert(user);
        return Result.ok();
    }

    /** 封禁 / 解封账号
     *
     * <p>{@code status} 传 0 封禁，传 1 解封。封禁后该用户无法登录，
     * 已签发的令牌在下一次请求时也会立即失效。</p>
     */
    @PutMapping("/{id}/status")
    public Result<Void> updateStatus(@PathVariable Long id, @RequestBody Map<String, Integer> body) {
        adminGuard.requireAdmin();
        Integer status = body.get("status");
        if (status == null || (status != 0 && status != 1)) {
            throw new BizException("status 只能是 0（封禁）或 1（解封）");
        }
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new BizException(404, "用户不存在");
        }
        UserContext.CurrentUser current = UserContext.get();
        if (current != null && current.userId().equals(id)) {
            throw new BizException("不能封禁当前登录的账号");
        }
        if (status == 0 && (user.isBanned() || adminGuard.isAdmin(user.getUsername()))) {
            // 已封禁 / 管理员：管理员封禁管理员容易把站点锁死，禁止
            throw new BizException("不能封禁管理员账号");
        }
        if (status == 1 && !user.isBanned()) {
            return Result.ok();
        }
        user.setStatus(status);
        userMapper.updateById(user);
        return Result.ok();
    }

    /** 修改昵称 / 重置密码 */
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody Map<String, String> body) {
        adminGuard.requireAdmin();
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new BizException(404, "用户不存在");
        }
        String nickname = trim(body.get("nickname"));
        if (nickname != null && !nickname.isBlank()) {
            user.setNickname(nickname);
        }
        String password = trim(body.get("password"));
        if (password != null && !password.isBlank()) {
            if (password.length() < 6 || password.length() > 32) {
                throw new BizException("密码长度需为 6-32 位");
            }
            user.setPassword(encoder.encode(password));
        }
        userMapper.updateById(user);
        return Result.ok();
    }

    /** 删除用户（同时清理其上传记录元数据；不能删除自己与其他管理员） */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        adminGuard.requireAdmin();
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new BizException(404, "用户不存在");
        }
        UserContext.CurrentUser current = UserContext.get();
        if (current != null && current.userId().equals(id)) {
            throw new BizException("不能删除当前登录的账号");
        }
        if (adminGuard.isAdmin(user.getUsername())) {
            throw new BizException("不能删除管理员账号");
        }
        recordMapper.delete(new LambdaQueryWrapper<UploadRecord>().eq(UploadRecord::getUserId, id));
        userMapper.deleteById(id);
        return Result.ok();
    }

    private String trim(String s) {
        return s == null ? null : s.trim();
    }
}
