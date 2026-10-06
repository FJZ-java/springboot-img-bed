package com.fjz.imgbed.stats;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fjz.imgbed.auth.AdminGuard;
import com.fjz.imgbed.common.Result;
import com.fjz.imgbed.record.entity.UploadRecord;
import com.fjz.imgbed.record.mapper.UploadRecordMapper;
import com.fjz.imgbed.share.entity.ShareLink;
import com.fjz.imgbed.share.mapper.ShareLinkMapper;
import com.fjz.imgbed.storage.entity.StorageRepo;
import com.fjz.imgbed.storage.mapper.StorageRepoMapper;
import com.fjz.imgbed.user.entity.User;
import com.fjz.imgbed.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * 管理员数据统计（仅管理员可见）。
 *
 * <p>顶部核心指标 + 图表所需的原始数据一次性返回，前端用 ECharts 渲染：
 * 上传趋势、仓库容量分布、用户贡献排行、账号状态、图片格式分布。</p>
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminStatsController {

    private final AdminGuard adminGuard;
    private final UserMapper userMapper;
    private final UploadRecordMapper recordMapper;
    private final ShareLinkMapper shareLinkMapper;
    private final StorageRepoMapper storageRepoMapper;

    /** 管理页统计：days = 趋势图回溯天数（7 / 14 / 30） */
    @GetMapping("/stats")
    public Result<Map<String, Object>> stats(@RequestParam(defaultValue = "30") int days) {
        adminGuard.requireAdmin();
        int span = Math.max(7, Math.min(90, days));

        List<UploadRecord> records = recordMapper.selectList(new LambdaQueryWrapper<>());
        List<User> users = userMapper.selectList(new LambdaQueryWrapper<>());
        List<ShareLink> shares = shareLinkMapper.selectList(new LambdaQueryWrapper<>());

        long totalSize = records.stream().mapToLong(r -> r.getSize() == null ? 0L : r.getSize()).sum();
        List<Map<String, Object>> trend = buildTrend(records, span);
        long todayUploads = trend.isEmpty() ? 0 : (long) trend.get(trend.size() - 1).get("count");
        long weekUploads = trend.subList(Math.max(0, trend.size() - 7), trend.size()).stream()
                .mapToLong(m -> ((Number) m.get("count")).longValue()).sum();

        long totalImages = records.size();
        long shareViews = shares.stream().mapToLong(s -> s.getViewCount() == null ? 0L : s.getViewCount()).sum();
        long activeShares = shares.stream().filter(s -> s.getStatus() != null && s.getStatus() == 1).count();

        Map<String, Object> overview = new LinkedHashMap<>();
        overview.put("images", totalImages);
        overview.put("totalSize", totalSize);
        overview.put("avgSize", totalImages == 0 ? 0 : totalSize / totalImages);
        overview.put("users", users.size());
        overview.put("admins", users.stream().filter(u -> adminGuard.isAdmin(u.getUsername())).count());
        overview.put("banned", users.stream().filter(u -> u.isBanned()).count());
        overview.put("shareLinks", activeShares);
        overview.put("shareViews", shareViews);
        overview.put("todayUploads", todayUploads);
        overview.put("weekUploads", weekUploads);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("overview", overview);
        data.put("trend", trend);
        data.put("repos", groupRepo(records));
        data.put("topUsers", topUsers(records, users));
        data.put("accountStatus", accountStatus(users));
        data.put("formats", groupFormat(records));
        data.put("days", span);
        return Result.ok(data);
    }

    /** 近 span 天每日上传量与体积（补齐没有上传的空白天） */
    private List<Map<String, Object>> buildTrend(List<UploadRecord> records, int span) {
        LocalDate today = LocalDate.now();
        TreeSet<String> order = new TreeSet<>();
        for (int i = span - 1; i >= 0; i--) {
            order.add(today.minusDays(i).toString());
        }
        Map<String, long[]> bucket = new LinkedHashMap<>();
        order.forEach(d -> bucket.put(d, new long[] { 0L, 0L }));
        for (UploadRecord r : records) {
            long[] arr = bucket.get(dayOf(r.getCreateTime()));
            if (arr == null) {
                continue;
            }
            arr[0]++;
            arr[1] += r.getSize() == null ? 0 : r.getSize();
        }
        List<Map<String, Object>> list = new ArrayList<>();
        for (Map.Entry<String, long[]> e : bucket.entrySet()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("date", e.getKey());
            m.put("count", e.getValue()[0]);
            m.put("size", e.getValue()[1]);
            list.add(m);
        }
        return list;
    }

    /** 各仓库的图片数与占用体积（早期记录没有仓库字段，按 repo_id 回查，查不到归为「历史数据」） */
    private List<Map<String, Object>> groupRepo(List<UploadRecord> records) {
        Map<Long, String> repoNames = new HashMap<>();
        for (StorageRepo rp : storageRepoMapper.selectList(new LambdaQueryWrapper<>())) {
            repoNames.put(rp.getId(), (rp.getOwner() == null ? "" : rp.getOwner()) + "/" + rp.getRepo());
        }
        Map<String, long[]> bucket = new LinkedHashMap<>();
        for (UploadRecord r : records) {
            String name = r.getRepoName();
            if (name == null || name.isBlank()) {
                name = (r.getRepoId() != null ? repoNames.get(r.getRepoId()) : null) == null
                        ? "历史数据"
                        : repoNames.get(r.getRepoId());
            }
            long[] arr = bucket.computeIfAbsent(name, k -> new long[] { 0L, 0L });
            arr[0]++;
            arr[1] += r.getSize() == null ? 0 : r.getSize();
        }
        return bucket.entrySet().stream()
                .map(e -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("name", e.getKey());
                    m.put("count", e.getValue()[0]);
                    m.put("size", e.getValue()[1]);
                    return m;
                })
                .sorted(Comparator.comparingLong((Map<String, Object> m) -> (Long) m.get("size")).reversed())
                .collect(Collectors.toList());
    }

    /** 上传量最高的若干用户 */
    private List<Map<String, Object>> topUsers(List<UploadRecord> records, List<User> users) {
        Map<Long, String> names = users.stream().collect(Collectors.toMap(User::getId, u -> u.getUsername(), (a, b) -> a));
        Map<Long, long[]> bucket = new LinkedHashMap<>();
        for (UploadRecord r : records) {
            if (r.getUserId() == null) {
                continue;
            }
            long[] arr = bucket.computeIfAbsent(r.getUserId(), k -> new long[] { 0L, 0L });
            arr[0]++;
            arr[1] += r.getSize() == null ? 0 : r.getSize();
        }
        return bucket.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue()[0], a.getValue()[0]))
                .limit(8)
                .map(e -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("name", names.getOrDefault(e.getKey(), ("#" + e.getKey())));
                    m.put("count", e.getValue()[0]);
                    m.put("size", e.getValue()[1]);
                    return m;
                })
                .collect(Collectors.toList());
    }

    /** 账号状态分布：正常 / 封禁 */
    private List<Map<String, Object>> accountStatus(List<User> users) {
        long normal = users.stream().filter(u -> !u.isBanned()).count();
        long banned = users.size() - normal;
        List<Map<String, Object>> list = new ArrayList<>();
        list.add(row("正常", normal));
        list.add(row("封禁", banned));
        return list;
    }

    /** 图片格式分布（由 content-type 推导：image/png -> PNG） */
    private List<Map<String, Object>> groupFormat(List<UploadRecord> records) {
        Map<String, Long> bucket = new LinkedHashMap<>();
        for (UploadRecord r : records) {
            String ct = r.getContentType();
            String key;
            if (ct == null || ct.isBlank()) {
                key = "未知";
            } else {
                String tail = ct.contains("/") ? ct.substring(ct.lastIndexOf('/') + 1) : ct;
                key = tail.toUpperCase(Locale.ROOT);
                if (key.contains("+")) {
                    key = key.substring(0, key.indexOf('+'));
                }
            }
            bucket.merge(key, 1L, Long::sum);
        }
        return bucket.entrySet().stream()
                .map(e -> row(e.getKey(), e.getValue()))
                .sorted((a, b) -> ((Long) b.get("value")).compareTo((Long) a.get("value")))
                .collect(Collectors.toList());
    }

    private Map<String, Object> row(String name, long value) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", name);
        m.put("value", value);
        return m;
    }

    /** 统一把 LocalDateTime / Timestamp / 字符串都洗成 yyyy-MM-dd */
    private String dayOf(Object v) {
        if (v == null) {
            return "";
        }
        String s = String.valueOf(v).replace('T', ' ').trim();
        return s.length() >= 10 ? s.substring(0, 10) : s;
    }
}
