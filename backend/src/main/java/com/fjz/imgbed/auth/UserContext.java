package com.fjz.imgbed.auth;

/**
 * 当前请求用户上下文（ThreadLocal），由 AuthInterceptor 写入与清理。
 */
public final class UserContext {

    public record CurrentUser(Long userId, String username) {}

    private static final ThreadLocal<CurrentUser> HOLDER = new ThreadLocal<>();

    private UserContext() {}

    public static void set(CurrentUser user) {
        HOLDER.set(user);
    }

    public static CurrentUser get() {
        return HOLDER.get();
    }

    public static Long requireUserId() {
        CurrentUser u = HOLDER.get();
        if (u == null) {
            throw new IllegalStateException("当前线程无登录用户");
        }
        return u.userId();
    }

    public static void clear() {
        HOLDER.remove();
    }
}
