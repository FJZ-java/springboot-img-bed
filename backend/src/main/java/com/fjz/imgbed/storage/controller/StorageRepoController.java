package com.fjz.imgbed.storage.controller;

import com.fjz.imgbed.auth.AdminGuard;
import com.fjz.imgbed.common.Result;
import com.fjz.imgbed.storage.entity.StorageRepo;
import com.fjz.imgbed.storage.service.StorageRepoService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 存储配置（管理员）：管理多个 GitHub 仓库，上传时按权重随机分流。
 *
 * <p>出于安全考虑，Token 不会回传给前端，只返回掩码形式；
 * 前端若原样回传掩码，后端会自动忽略，不会覆盖真实 Token。</p>
 */
@RestController
@RequestMapping("/api/admin/repos")
@RequiredArgsConstructor
public class StorageRepoController {

    private final StorageRepoService repoService;
    private final AdminGuard adminGuard;

    /** 仓库列表 + 汇总统计 */
    @GetMapping
    public Result<Map<String, Object>> list() {
        adminGuard.requireAdmin();
        List<StorageRepo> repos = repoService.listAll();
        long enabled = repos.stream().filter(r -> Integer.valueOf(1).equals(r.getEnabled())).count();
        long files = repos.stream().mapToLong(r -> r.getFileCount() == null ? 0 : r.getFileCount()).sum();
        long size = repos.stream().mapToLong(r -> r.getTotalSize() == null ? 0 : r.getTotalSize()).sum();
        long disk = repos.stream().mapToLong(r -> r.getDiskSize() == null ? 0 : r.getDiskSize()).sum();

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("repos", repos.stream().map(StorageRepoController::toView).toList());
        data.put("summary", Map.of(
                "total", repos.size(),
                "enabled", enabled,
                "files", files,
                "size", size,
                "diskSize", disk));
        return Result.ok(data);
    }

    /** 新增仓库（会自动向 GitHub 校验并同步统计） */
    @PostMapping
    public Result<Map<String, Object>> create(@RequestBody StorageRepo input) {
        adminGuard.requireAdmin();
        return Result.ok(toView(repoService.create(input)));
    }

    /** 编辑仓库配置（名称 / 分支 / 目录 / 权重 / 启停 / 备注 / Token） */
    @PutMapping("/{id}")
    public Result<Map<String, Object>> update(@PathVariable Long id, @RequestBody StorageRepo input) {
        adminGuard.requireAdmin();
        input.setId(id);
        return Result.ok(toView(repoService.update(input)));
    }

    /** 同步 GitHub 上的文件数与体积 */
    @PostMapping("/{id}/sync")
    public Result<Map<String, Object>> sync(@PathVariable Long id) {
        adminGuard.requireAdmin();
        return Result.ok(toView(repoService.sync(id)));
    }

    /** 删除仓库配置 */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        adminGuard.requireAdmin();
        repoService.delete(id);
        return Result.ok();
    }

    private static Map<String, Object> toView(StorageRepo r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", r.getId());
        m.put("name", r.getName());
        m.put("owner", r.getOwner());
        m.put("repo", r.getRepo());
        m.put("branch", r.getBranch());
        m.put("dirPrefix", r.getDirPrefix());
        m.put("enabled", r.getEnabled());
        m.put("weight", r.getWeight());
        m.put("fileCount", r.getFileCount());
        m.put("totalSize", r.getTotalSize());
        m.put("diskSize", r.getDiskSize());
        m.put("remark", r.getRemark());
        m.put("lastSyncTime", r.getLastSyncTime() == null ? "" : r.getLastSyncTime().toString());
        m.put("createTime", r.getCreateTime() == null ? "" : r.getCreateTime().toString());
        m.put("hasToken", r.getToken() != null && !r.getToken().isBlank());
        m.put("tokenMasked", mask(r.getToken()));
        m.put("fullName", r.getOwner() + "/" + r.getRepo());
        return m;
    }

    private static String mask(String token) {
        if (token == null || token.isBlank()) return "";
        String t = token.trim();
        if (t.length() <= 8) return "****";
        return t.substring(0, 4) + "****" + t.substring(t.length() - 4);
    }
}
