package com.fjz.imgbed.storage.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fjz.imgbed.common.BizException;
import com.fjz.imgbed.github.GitHubClient;
import com.fjz.imgbed.record.mapper.UploadRecordMapper;
import com.fjz.imgbed.storage.entity.StorageRepo;
import com.fjz.imgbed.storage.mapper.StorageRepoMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 存储仓库配置管理：新增 / 编辑 / 启停 / 同步统计 / 随机挑选。
 *
 * <p>上传时由 {@link #pickForUpload()} 按权重随机挑一个启用中的仓库，
 * 从而实现「多仓库分流」，避免单一仓库体积膨胀或触发 GitHub 限制。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StorageRepoService {

    private final StorageRepoMapper repoMapper;
    private final UploadRecordMapper recordMapper;
    private final GitHubClient gitHubClient;

    /** 全局默认配置，用于首次启动自动播种 */
    @Value("${imgbed.github.owner}")
    private String defaultOwner;
    @Value("${imgbed.github.repo}")
    private String defaultRepo;
    @Value("${imgbed.github.branch:main}")
    private String defaultBranch;
    @Value("${imgbed.github.dir-prefix:images}")
    private String defaultDirPrefix;

    public List<StorageRepo> listAll() {
        return repoMapper.selectList(new LambdaQueryWrapper<StorageRepo>()
                .orderByAsc(StorageRepo::getId));
    }

    public StorageRepo get(Long id) {
        StorageRepo repo = repoMapper.selectById(id);
        if (repo == null) {
            throw new BizException(404, "仓库配置不存在");
        }
        return repo;
    }

    /** 新增仓库：先向 GitHub 校验可访问并拉取统计，再落库 */
    public StorageRepo create(StorageRepo input) {
        validate(input);
        StorageRepo repo = new StorageRepo();
        repo.setName(input.getName().trim());
        repo.setOwner(input.getOwner().trim());
        repo.setRepo(input.getRepo().trim());
        repo.setBranch(blankToNull(input.getBranch()));
        repo.setDirPrefix(input.getDirPrefix() == null || input.getDirPrefix().isBlank()
                ? "images" : GitHubClient.normalizeDir(input.getDirPrefix()));
        repo.setToken(blankToNull(input.getToken()));
        repo.setEnabled(input.getEnabled() == null ? 1 : input.getEnabled());
        repo.setWeight(input.getWeight() == null || input.getWeight() < 1 ? 1 : input.getWeight());
        repo.setRemark(blankToNull(input.getRemark()));
        repo.setFileCount(0L);
        repo.setTotalSize(0L);
        repo.setDiskSize(0L);
        repo.setCreateTime(LocalDateTime.now());

        // 分支留空时直接用 GitHub 返回的默认分支
        GitHubClient.RepoMeta meta = gitHubClient.fetchRepo(repo);
        if (repo.getBranch() == null) {
            repo.setBranch(meta.defaultBranch());
        }
        repo.setDiskSize(meta.sizeKb());
        repoMapper.insert(repo);
        sync(repo.getId());
        return get(repo.getId());
    }

    public StorageRepo update(StorageRepo input) {
        StorageRepo repo = get(input.getId());
        if (input.getName() != null && !input.getName().isBlank()) repo.setName(input.getName().trim());
        if (input.getBranch() != null) repo.setBranch(blankToNull(input.getBranch()));
        if (input.getDirPrefix() != null && !input.getDirPrefix().isBlank()) {
            repo.setDirPrefix(GitHubClient.normalizeDir(input.getDirPrefix()));
        }
        if (input.getToken() != null) repo.setToken(blankToNull(input.getToken()));
        if (input.getEnabled() != null) repo.setEnabled(input.getEnabled());
        if (input.getWeight() != null && input.getWeight() >= 1) repo.setWeight(input.getWeight());
        if (input.getRemark() != null) repo.setRemark(blankToNull(input.getRemark()));
        repoMapper.updateById(repo);
        return get(repo.getId());
    }

    /** 同步统计：文件数、总字节、GitHub 仓库体积 */
    public StorageRepo sync(Long id) {
        StorageRepo repo = get(id);
        GitHubClient.RepoMeta meta = gitHubClient.fetchRepo(repo);
        GitHubClient.TreeStats stats = gitHubClient.stats(repo);
        repo.setDiskSize(meta.sizeKb());
        repo.setFileCount(stats.files());
        repo.setTotalSize(stats.size());
        repo.setLastSyncTime(LocalDateTime.now());
        repoMapper.updateById(repo);
        return get(id);
    }

    /** 删除仓库配置：仍有图片记录的仓库不允许删除，避免图片变成孤儿无法清理 */
    public void delete(Long id) {
        StorageRepo repo = get(id);
        Long used = recordMapper.selectCount(new LambdaQueryWrapper<com.fjz.imgbed.record.entity.UploadRecord>()
                .eq(com.fjz.imgbed.record.entity.UploadRecord::getRepoId, id));
        if (used != null && used > 0) {
            throw new BizException("该仓库下仍有 " + used + " 张图片记录，请先删除这些图片再移除仓库");
        }
        repoMapper.deleteById(id);
    }

    /** 按权重随机挑一个启用中的仓库；没有任何配置时回退到全局默认配置 */
    public StorageRepo pickForUpload() {
        List<StorageRepo> enabled = listAll().stream()
                .filter(r -> r.getEnabled() != null && r.getEnabled() == 1)
                .toList();
        if (enabled.isEmpty()) {
            return fallbackRepo();
        }
        if (enabled.size() == 1) {
            return enabled.get(0);
        }
        int totalWeight = enabled.stream().mapToInt(r -> Math.max(1, r.getWeight() == null ? 1 : r.getWeight())).sum();
        int roll = ThreadLocalRandom.current().nextInt(totalWeight) + 1;
        int acc = 0;
        for (StorageRepo r : enabled) {
            acc += Math.max(1, r.getWeight() == null ? 1 : r.getWeight());
            if (roll <= acc) return r;
        }
        return enabled.get(enabled.size() - 1);
    }

    /** 仓库管理页浏览用：优先启用的第一个，其次任意第一个，最后回退全局配置 */
    public StorageRepo defaultForBrowse() {
        List<StorageRepo> all = listAll();
        if (!all.isEmpty()) {
            return all.stream().filter(r -> r.getEnabled() != null && r.getEnabled() == 1)
                    .findFirst().orElse(all.get(0));
        }
        return fallbackRepo();
    }

    /** 数据库还没有任何配置时（老版本升级），用 application.yml 的全局配置兜底 */
    private StorageRepo fallbackRepo() {
        StorageRepo repo = new StorageRepo();
        repo.setId(0L);
        repo.setName("默认仓库");
        repo.setOwner(defaultOwner);
        repo.setRepo(defaultRepo);
        repo.setBranch(defaultBranch);
        repo.setDirPrefix(defaultDirPrefix);
        repo.setEnabled(1);
        repo.setWeight(1);
        return repo;
    }

    /** 首次启动播种：把 yml 里的默认仓库写进配置表，保证老数据无缝可用 */
    public void seedDefaultIfEmpty() {
        if (repoMapper.selectCount(null) > 0) {
            return;
        }
        try {
            StorageRepo repo = new StorageRepo();
            repo.setName("默认仓库");
            repo.setOwner(defaultOwner);
            repo.setRepo(defaultRepo);
            repo.setBranch(defaultBranch);
            repo.setDirPrefix(GitHubClient.normalizeDir(defaultDirPrefix));
            repo.setEnabled(1);
            repo.setWeight(1);
            repo.setRemark("由 application.yml 自动导入");
            repo.setCreateTime(LocalDateTime.now());
            GitHubClient.RepoMeta meta = gitHubClient.fetchRepo(repo);
            repo.setDiskSize(meta.sizeKb());
            repoMapper.insert(repo);
            sync(repo.getId());
            log.info("已自动导入默认存储仓库：{}/{}", defaultOwner, defaultRepo);
        } catch (Exception e) {
            // 播种失败不阻塞启动：上传时仍会走 fallbackRepo()
            log.warn("导入默认存储仓库失败（可稍后在「存储配置」手动添加）：{}", e.getMessage());
        }
    }

    private void validate(StorageRepo input) {
        if (input == null) throw new BizException("参数不能为空");
        if (input.getOwner() == null || input.getOwner().isBlank()) throw new BizException("请填写仓库拥有者 owner");
        if (input.getRepo() == null || input.getRepo().isBlank()) throw new BizException("请填写仓库名 repo");
        String name = input.getName() == null || input.getName().isBlank()
                ? input.getOwner() + "/" + input.getRepo() : input.getName().trim();
        input.setName(name);
        Long dup = repoMapper.selectCount(new LambdaQueryWrapper<StorageRepo>()
                .eq(StorageRepo::getOwner, input.getOwner().trim())
                .eq(StorageRepo::getRepo, input.getRepo().trim()));
        if (dup != null && dup > 0) {
            throw new BizException("该仓库已添加过了：" + input.getOwner() + "/" + input.getRepo());
        }
    }

    /** 汇总所有启动仓库的统计（首页公开数据用） */
    public List<StorageRepo> enabledList() {
        return new ArrayList<>(listAll().stream()
                .filter(r -> r.getEnabled() != null && r.getEnabled() == 1)
                .toList());
    }

    private String blankToNull(String s) {
        if (s == null || s.isBlank()) return null;
        return s.trim();
    }
}
