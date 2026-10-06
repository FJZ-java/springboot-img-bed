package com.fjz.imgbed.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 轻量级 SQLite 结构迁移：老库升级时补齐新增列。
 *
 * <p>schema.sql 只能建新表（CREATE TABLE IF NOT EXISTS），
 * 已有表加列需要 ALTER TABLE，重复执行会报错，所以这里先查 PRAGMA 再决定。</p>
 */
@Slf4j
@Component
@Order(0)
@RequiredArgsConstructor
public class SchemaMigrator implements ApplicationRunner {

    private final JdbcTemplate jdbc;

    @Override
    public void run(ApplicationArguments args) {
        addColumnIfMissing("upload_record", "repo_id", "INTEGER");
        addColumnIfMissing("upload_record", "repo_name", "TEXT");
        // 老库补「账号状态」列，已有账号默认正常
        addColumnIfMissing("user", "status", "INTEGER DEFAULT 1");
        // 老库补「API 密钥」列（首次查看时懒生成）
        addColumnIfMissing("user", "api_key", "TEXT");
        addIndexIfMissing("idx_user_api_key", "CREATE UNIQUE INDEX IF NOT EXISTS idx_user_api_key ON user(api_key)");
    }

    private void addIndexIfMissing(String name, String ddl) {
        try {
            jdbc.execute(ddl);
        } catch (Exception e) {
            log.warn("创建索引 {} 失败：{}", name, e.getMessage());
        }
    }

    private void addColumnIfMissing(String table, String column, String type) {
        try {
            Set<String> cols = new HashSet<>();
            // PRAGMA table_info 返回 cid/name/type/...，这里只取 name 列
            List<Map<String, Object>> info = jdbc.queryForList("PRAGMA table_info(" + table + ")");
            for (Map<String, Object> r : info) {
                Object name = r.get("name");
                if (name != null) cols.add(String.valueOf(name));
            }
            if (cols.contains(column)) return;
            jdbc.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + type);
            log.info("已为 {} 补充列 {}", table, column);
        } catch (Exception e) {
            log.warn("检查/补充列 {}.{} 失败：{}", table, column, e.getMessage());
        }
    }
}
