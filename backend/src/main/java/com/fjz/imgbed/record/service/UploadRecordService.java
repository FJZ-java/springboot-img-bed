package com.fjz.imgbed.record.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fjz.imgbed.common.BizException;
import com.fjz.imgbed.github.GitHubService;
import com.fjz.imgbed.record.entity.UploadRecord;
import com.fjz.imgbed.record.mapper.UploadRecordMapper;
import com.fjz.imgbed.share.service.ShareService;
import com.fjz.imgbed.upload.ImageUploadService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class UploadRecordService {

    private final UploadRecordMapper recordMapper;
    private final ImageUploadService imageUploadService;
    private final GitHubService gitHubService;
    private final ShareService shareService;

    public UploadRecord upload(Long userId, MultipartFile file) {
        ImageUploadService.StoredImage img = imageUploadService.store(file);

        UploadRecord record = new UploadRecord();
        record.setUserId(userId);
        record.setFileName(img.fileName());
        record.setOriginName(img.originName());
        record.setGithubPath(img.githubPath());
        record.setSha(img.sha());
        record.setCdnUrl(img.cdnUrl());
        record.setRepoId(img.repoId());
        record.setRepoName(img.repoName());
        record.setSize(img.size());
        record.setContentType(img.contentType());
        record.setCreateTime(LocalDateTime.now());
        recordMapper.insert(record);
        return record;
    }

    public Page<UploadRecord> page(Long userId, int page, int size) {
        Page<UploadRecord> p = new Page<>(Math.max(page, 1), Math.min(Math.max(size, 1), 50));
        return recordMapper.selectPage(p, new LambdaQueryWrapper<UploadRecord>()
                .eq(UploadRecord::getUserId, userId)
                .orderByDesc(UploadRecord::getCreateTime));
    }

    public void delete(Long userId, Long recordId) {
        UploadRecord record = recordMapper.selectById(recordId);
        if (record == null || !record.getUserId().equals(userId)) {
            throw new BizException(404, "记录不存在");
        }
        // 先删 GitHub（失败则整体中止，数据库不删），再删本地记录
        // 注意：必须回到图片当初存入的那个仓库，多仓库场景下不能随手挑一个
        gitHubService.deleteRecordFile(record);
        recordMapper.deleteById(recordId);
        // 记录没了，分享链接也一起清掉
        shareService.deleteByRecordId(recordId);
    }

    /**
     * 批量删除：逐张删除，单张失败不影响其余（避免一张卡住导致整批回滚）。
     *
     * @return { deleted: 成功数, failed: [{id, name, message}] }
     */
    public Map<String, Object> deleteBatch(Long userId, List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new BizException("请先选择要删除的图片");
        }
        // 去重，避免同一 id 重复处理
        List<Long> unique = new ArrayList<>(new java.util.LinkedHashSet<>(ids));
        if (unique.size() > 200) {
            throw new BizException("单次最多删除 200 张");
        }
        int deleted = 0;
        List<Map<String, Object>> failed = new ArrayList<>();
        for (Long id : unique) {
            if (id == null) continue;
            UploadRecord record = recordMapper.selectById(id);
            try {
                delete(userId, id);
                deleted++;
            } catch (Exception e) {
                log.warn("批量删除失败 id={}: {}", id, e.getMessage());
                Map<String, Object> f = new LinkedHashMap<>();
                f.put("id", id);
                f.put("name", record == null ? String.valueOf(id) : record.getOriginName());
                f.put("message", e.getMessage());
                failed.add(f);
            }
        }
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("deleted", deleted);
        res.put("failed", failed);
        return res;
    }
}
