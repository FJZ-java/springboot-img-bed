package com.fjz.imgbed.github;

import com.fjz.imgbed.auth.AdminGuard;
import com.fjz.imgbed.common.Result;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * GitHub 仓库管理：浏览目录、删除文件、查看仓库信息。
 *
 * <p>支持通过 repoId 指定要操作的仓库（多仓库配置），不传则用默认仓库。</p>
 */
@RestController
@RequestMapping("/api/github")
@RequiredArgsConstructor
public class GitHubController {

    private final GitHubService gitHubService;
    private final AdminGuard adminGuard;

    /** 仓库信息 */
    @GetMapping("/info")
    public Result<Map<String, String>> info() {
        return Result.ok(Map.of(
                "owner", gitHubService.getOwner(),
                "repo", gitHubService.getRepo(),
                "branch", gitHubService.getBranch(),
                "dirPrefix", gitHubService.getDirPrefix()));
    }

    /** 列出目录内容，path 为空时列根存储目录 —— 仅管理员 */
    @GetMapping("/files")
    public Result<List<GitHubClient.FileItem>> files(@RequestParam(required = false) String path,
                                                     @RequestParam(required = false) Long repoId) {
        adminGuard.requireAdmin();
        return Result.ok(repoId == null ? gitHubService.list(path) : gitHubService.list(repoId, path));
    }

    /** 删除仓库文件 —— 仅管理员 */
    @DeleteMapping("/file")
    public Result<Void> delete(@Validated @RequestBody DeleteReq req) {
        adminGuard.requireAdmin();
        gitHubService.delete(req.getRepoId(), req.getPath(), req.getSha());
        return Result.ok();
    }

    @Data
    public static class DeleteReq {
        @NotBlank(message = "path 不能为空")
        private String path;
        @NotBlank(message = "sha 不能为空")
        private String sha;
        /** 可空：为空表示默认仓库 */
        private Long repoId;
    }
}
