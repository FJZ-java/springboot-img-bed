package com.fjz.imgbed.github;

import com.fasterxml.jackson.databind.JsonNode;
import com.fjz.imgbed.common.BizException;
import com.fjz.imgbed.storage.entity.StorageRepo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 面向「单个仓库配置」的 GitHub REST 调用封装。
 *
 * <p>与 {@link GitHubService} 的区别：这里不绑定任何固定仓库，所有方法都显式接收
 * {@link StorageRepo}，因此可以同时操作多个仓库；RestClient 按 Token 缓存复用。</p>
 *
 * <p>接口文档：https://docs.github.com/rest/repos/contents</p>
 */
@Slf4j
@Component
public class GitHubClient {

    private final Map<String, RestClient> clients = new ConcurrentHashMap<>();

    private RestClient client(StorageRepo repo) {
        String token = token(repo);
        return clients.computeIfAbsent(token, t -> RestClient.builder()
                .baseUrl("https://api.github.com")
                .defaultHeader("Authorization", "Bearer " + t)
                .defaultHeader("Accept", "application/vnd.github+json")
                .defaultHeader("X-GitHub-Api-Version", "2022-11-28")
                .build());
    }

    /** 仓库专属 Token，为空时回退到全局 Token */
    private String token(StorageRepo repo) {
        if (repo != null && repo.getToken() != null && !repo.getToken().isBlank()) {
            return repo.getToken().trim();
        }
        return globalToken;
    }

    private final String globalToken;

    public GitHubClient(@org.springframework.beans.factory.annotation.Value("${imgbed.github.token}") String globalToken) {
        this.globalToken = globalToken;
        if (globalToken == null || globalToken.isBlank()) {
            log.error("GitHub Token 未配置（imgbed.github.token / 环境变量 GITHUB_TOKEN），上传与仓库管理将不可用！");
        }
    }

    /* ---------------- 元数据 ---------------- */

    /** 读取仓库元信息（默认分支、占用体积等），同时可用来校验仓库是否存在 / Token 是否有权限 */
    public RepoMeta fetchRepo(StorageRepo cfg) {
        try {
            JsonNode resp = client(cfg).get()
                    .uri("/repos/{o}/{r}", cfg.getOwner(), cfg.getRepo())
                    .retrieve()
                    .body(JsonNode.class);
            if (resp == null) {
                throw new BizException(502, "读取仓库信息失败：返回为空");
            }
            return new RepoMeta(
                    resp.path("default_branch").asText("main"),
                    resp.path("size").asLong(0),
                    resp.path("private").asBoolean(false),
                    resp.path("description").asText(""),
                    resp.path("stargazers_count").asLong(0));
        } catch (RestClientResponseException e) {
            int status = e.getStatusCode().value();
            if (status == 404) {
                throw new BizException(404, "仓库不存在，或当前 Token 没有访问权限：" + cfg.getOwner() + "/" + cfg.getRepo());
            }
            if (status == 401 || status == 403) {
                throw new BizException(403, "Token 无效或没有该仓库的权限");
            }
            log.error("读取仓库信息失败: status={}, body={}", status, e.getResponseBodyAsString());
            throw new BizException(502, "读取仓库信息失败: " + e.getStatusText());
        }
    }

    /**
     * 统计仓库文件数量与体积（一次 git trees 递归请求）。
     * 同时统计「存储目录内」和「整个仓库」两组数字。
     */
    public TreeStats stats(StorageRepo cfg) {
        String prefix = normalizeDir(cfg.getDirPrefix());
        String prefixPath = prefix.isEmpty() ? "" : prefix + "/";
        try {
            JsonNode resp = client(cfg).get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/repos/{o}/{r}/git/trees/{b}")
                            .queryParam("recursive", "1")
                            .build(cfg.getOwner(), cfg.getRepo(), branchOf(cfg)))
                    .retrieve()
                    .body(JsonNode.class);
            long files = 0;
            long size = 0;
            long dirFiles = 0;
            long dirSize = 0;
            if (resp != null && resp.has("tree")) {
                for (JsonNode n : resp.get("tree")) {
                    if (!"blob".equals(n.path("type").asText())) continue;
                    long s = n.path("size").asLong(0);
                    files++;
                    size += s;
                    String p = n.path("path").asText();
                    if (prefixPath.isEmpty() || p.startsWith(prefixPath)) {
                        dirFiles++;
                        dirSize += s;
                    }
                }
            }
            return new TreeStats(files, size, dirFiles, dirSize);
        } catch (RestClientResponseException e) {
            log.warn("统计仓库文件失败: status={}", e.getStatusCode());
            return new TreeStats(0, 0, 0, 0);
        }
    }

    /* ---------------- 文件操作 ---------------- */

    /** 上传文件到指定仓库，返回 {path, sha, cdnUrl} */
    public UploadResult upload(StorageRepo cfg, String relativePath, byte[] content) {
        String fullPath = fullPath(cfg, relativePath);
        String base64 = Base64.getEncoder().encodeToString(content);
        try {
            JsonNode resp = client(cfg).put()
                    .uri("/repos/{o}/{r}/contents/{p}", cfg.getOwner(), cfg.getRepo(), fullPath)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "message", "chore: upload " + fullPath,
                            "content", base64,
                            "branch", branchOf(cfg)))
                    .retrieve()
                    .body(JsonNode.class);
            String sha = resp != null && resp.has("content") && resp.get("content").has("sha")
                    ? resp.get("content").get("sha").asText() : null;
            return new UploadResult(fullPath, sha, cdnUrl(cfg, fullPath));
        } catch (RestClientResponseException e) {
            log.error("GitHub 上传失败: status={}, body={}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new BizException(502, "上传到 GitHub 失败: " + e.getStatusText());
        }
    }

    /** 列出仓库某目录内容（type: file/dir） */
    public List<FileItem> list(StorageRepo cfg, String path) {
        String p = (path == null || path.isBlank()) ? normalizeDir(cfg.getDirPrefix()) : path;
        try {
            JsonNode resp = client(cfg).get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/repos/{o}/{r}/contents/{p}")
                            .queryParam("ref", branchOf(cfg))
                            .build(cfg.getOwner(), cfg.getRepo(), p))
                    .retrieve()
                    .body(JsonNode.class);
            List<FileItem> items = new ArrayList<>();
            if (resp != null && resp.isArray()) {
                for (JsonNode n : resp) {
                    String type = n.path("type").asText();
                    String itemPath = n.path("path").asText();
                    items.add(new FileItem(
                            n.path("name").asText(),
                            itemPath,
                            type,
                            n.path("size").asLong(0),
                            n.path("sha").asText(null),
                            "file".equals(type) ? cdnUrl(cfg, itemPath) : null));
                }
            }
            items.sort((a, b) -> a.type().equals(b.type()) ? a.name().compareTo(b.name())
                    : ("dir".equals(a.type()) ? -1 : 1));
            return items;
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == 404) return List.of();
            log.error("GitHub 列表失败: status={}, body={}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new BizException(502, "获取仓库文件列表失败: " + e.getStatusText());
        }
    }

    /** 删除仓库文件（需要文件的 sha） */
    public void delete(StorageRepo cfg, String path, String sha) {
        if (sha == null || sha.isBlank()) {
            throw new BizException("缺少文件 sha，无法删除");
        }
        try {
            client(cfg).method(HttpMethod.DELETE)
                    .uri("/repos/{o}/{r}/contents/{p}", cfg.getOwner(), cfg.getRepo(), path)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("message", "chore: delete " + path, "sha", sha, "branch", branchOf(cfg)))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException e) {
            log.error("GitHub 删除失败: status={}, body={}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new BizException(502, "从 GitHub 删除失败: " + e.getStatusText());
        }
    }

    /* ---------------- 工具 ---------------- */

    public String cdnUrl(StorageRepo cfg, String fullPath) {
        return "https://cdn.jsdelivr.net/gh/%s/%s@%s/%s"
                .formatted(cfg.getOwner(), cfg.getRepo(), branchOf(cfg), fullPath);
    }

    public String fullPath(StorageRepo cfg, String relativePath) {
        String rel = relativePath.replaceAll("^/+", "");
        String dir = normalizeDir(cfg.getDirPrefix());
        return dir.isEmpty() ? rel : dir + "/" + rel;
    }

    public String branchOf(StorageRepo cfg) {
        if (cfg.getBranch() == null || cfg.getBranch().isBlank()) return "main";
        return cfg.getBranch().trim();
    }

    public static String normalizeDir(String dirPrefix) {
        if (dirPrefix == null) return "";
        return dirPrefix.replaceAll("^/+|/+$", "");
    }

    public record UploadResult(String path, String sha, String cdnUrl) {}

    public record FileItem(String name, String path, String type, long size, String sha, String cdnUrl) {}

    public record RepoMeta(String defaultBranch, long sizeKb, boolean privateRepo, String description, long stars) {}

    /** files/size 为整个仓库，dirFiles/dirSize 为存储目录内 */
    public record TreeStats(long files, long size, long dirFiles, long dirSize) {}
}
