package com.fjz.imgbed.github;

import com.fjz.imgbed.common.BizException;
import com.fjz.imgbed.record.entity.UploadRecord;
import com.fjz.imgbed.storage.entity.StorageRepo;
import com.fjz.imgbed.storage.service.StorageRepoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * GitHub 操作门面：屏蔽「多仓库」的复杂度，对业务层保持原来的单一调用方式。
 *
 * <p>上传时从 {@link StorageRepoService#pickForUpload()} 随机取一个启用中的仓库；
 * 删除时按记录上保存的 repoId 回到原仓库，避免删错仓库或 sha 不匹配。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GitHubService {

    private final StorageRepoService repoService;
    private final GitHubClient client;

    /** 随机挑一个启用中的仓库上传，返回 {path, sha, cdnUrl} */
    public UploadResult upload(String relativePath, byte[] content) {
        StorageRepo repo = repoService.pickForUpload();
        GitHubClient.UploadResult r = client.upload(repo, relativePath, content);
        return new UploadResult(r.path(), r.sha(), r.cdnUrl(), repo.getId(), label(repo));
    }

    /** 上传并附带仓库信息（上传记录需要落库） */
    public StorageRepo currentTarget() {
        return repoService.pickForUpload();
    }

    /** 删除某条记录对应的远端文件 */
    public void deleteRecordFile(UploadRecord record) {
        StorageRepo repo = resolveRepo(record.getRepoId());
        client.delete(repo, record.getGithubPath(), record.getSha());
    }

    /** 浏览默认仓库的目录 */
    public List<GitHubClient.FileItem> list(String path) {
        return client.list(repoService.defaultForBrowse(), path);
    }

    /** 浏览指定仓库的目录 */
    public List<GitHubClient.FileItem> list(Long repoId, String path) {
        return client.list(resolveRepo(repoId), path);
    }

    /** 删除指定仓库的文件（仓库管理页用） */
    public void delete(Long repoId, String path, String sha) {
        client.delete(resolveRepo(repoId), path, sha);
    }

    /** 汇总所有启用仓库的统计（首页公开数据） */
    public RepoStats stats() {
        long images = 0;
        long size = 0;
        for (StorageRepo repo : repoService.enabledList()) {
            GitHubClient.TreeStats s = client.stats(repo);
            images += s.files();
            size += s.size();
        }
        return new RepoStats(images, size);
    }

    /** 指定仓库的统计 */
    public GitHubClient.TreeStats stats(Long repoId) {
        return client.stats(resolveRepo(repoId));
    }

    public String cdnUrl(String fullPath) {
        return client.cdnUrl(repoService.defaultForBrowse(), fullPath);
    }

    private StorageRepo resolveRepo(Long repoId) {
        if (repoId == null || repoId <= 0) {
            return repoService.defaultForBrowse();
        }
        try {
            return repoService.get(repoId);
        } catch (BizException e) {
            log.warn("仓库配置 {} 已不存在，回退到默认仓库", repoId);
            return repoService.defaultForBrowse();
        }
    }

    public static String label(StorageRepo repo) {
        return repo.getOwner() + "/" + repo.getRepo();
    }

    /* ---- 兼容旧调用：默认仓库信息 ---- */
    public String getOwner() { return repoService.defaultForBrowse().getOwner(); }
    public String getRepo() { return repoService.defaultForBrowse().getRepo(); }
    public String getBranch() { return client.branchOf(repoService.defaultForBrowse()); }
    public String getDirPrefix() { return repoService.defaultForBrowse().getDirPrefix(); }

    public record UploadResult(String path, String sha, String cdnUrl, Long repoId, String repoName) {}

    public record RepoStats(long images, long size) {}
}
