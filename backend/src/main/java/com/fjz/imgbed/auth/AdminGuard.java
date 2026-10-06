package com.fjz.imgbed.auth;

import com.fjz.imgbed.common.BizException;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 管理员判定与校验。
 *
 * <p>管理员由环境变量 {@code ADMIN_USERNAMES} 指定（逗号分隔，默认 admin），
 * 只有管理员可以浏览 / 删除仓库文件（仓库管理页）。</p>
 */
@Getter
@Component
public class AdminGuard {

    private final Set<String> adminUsernames = new LinkedHashSet<>();

    public AdminGuard(@Value("${ADMIN_USERNAMES:admin}") String raw) {
        if (raw != null) {
            Arrays.stream(raw.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .forEach(adminUsernames::add);
        }
    }

    /** 判断某个用户名是否管理员 */
    public boolean isAdmin(String username) {
        return username != null && adminUsernames.contains(username);
    }

    /** 当前登录用户是否管理员 */
    public boolean currentIsAdmin() {
        UserContext.CurrentUser u = UserContext.get();
        return u != null && isAdmin(u.username());
    }

    /** 要求当前登录用户必须是管理员，否则抛出 403 */
    public void requireAdmin() {
        UserContext.CurrentUser u = UserContext.get();
        if (u == null) {
            throw new BizException(401, "未登录");
        }
        if (!isAdmin(u.username())) {
            throw new BizException(403, "仅管理员可访问该功能");
        }
    }
}
