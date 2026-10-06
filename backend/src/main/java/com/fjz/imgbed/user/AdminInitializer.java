package com.fjz.imgbed.user;

import com.fjz.imgbed.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 启动时确保管理员账号存在。
 *
 * <p>管理员账号密码为内置固定值，无需在配置文件里维护；
 * 生产环境如需更换，用环境变量 ADMIN_USERNAME / ADMIN_PASSWORD 覆盖即可。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminInitializer implements ApplicationRunner {

    private final UserService userService;

    /** 固定管理员账号（可用环境变量 ADMIN_USERNAME 覆盖） */
    @Value("${ADMIN_USERNAME:admin}")
    private String adminUsername;

    /** 固定管理员密码（可用环境变量 ADMIN_PASSWORD 覆盖） */
    @Value("${ADMIN_PASSWORD:1740304522Zhao.}")
    private String adminPassword;

    @Override
    public void run(ApplicationArguments args) {
        if (adminPassword == null || adminPassword.isBlank()) {
            log.warn("管理员密码为空，跳过管理员引导");
            return;
        }
        String result = userService.bootstrapAdmin(adminUsername.trim(), adminPassword);
        // 仅首次创建时提示；已存在则静默，避免每次启动都刷一条无意义日志
        if ("created".equals(result)) {
            log.info("已创建管理员账号：{}", adminUsername);
        }
    }
}
