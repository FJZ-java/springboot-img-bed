package com.fjz.imgbed.share.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fjz.imgbed.common.BizException;
import com.fjz.imgbed.record.entity.UploadRecord;
import com.fjz.imgbed.record.mapper.UploadRecordMapper;
import com.fjz.imgbed.share.entity.ShareLink;
import com.fjz.imgbed.share.mapper.ShareLinkMapper;
import com.fjz.imgbed.user.entity.User;
import com.fjz.imgbed.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 分享链接：把一条上传记录生成一个可公开访问的短码（/share/{code}）。
 *
 * <p>同一条记录重复点击「分享」会复用已有短码，不会无限生成垃圾数据。</p>
 */
@Service
@RequiredArgsConstructor
public class ShareService {

    private static final String CODE_CHARS = "0123456789abcdefghijklmnopqrstuvwxyz";
    private static final int CODE_LEN = 10;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final ShareLinkMapper shareMapper;
    private final UploadRecordMapper recordMapper;
    private final UserMapper userMapper;

    /** 可选的站点地址（如 https://imgbed.example.com），配置后返回的 url 为绝对地址 */
    @Value("${imgbed.share.base-url:}")
    private String baseUrl;

    /** 为当前用户的某条记录创建（或复用）分享链接 */
    public Map<String, Object> create(Long userId, Long recordId) {
        UploadRecord record = recordMapper.selectById(recordId);
        if (record == null || !record.getUserId().equals(userId)) {
            throw new BizException(404, "记录不存在");
        }
        ShareLink link = shareMapper.selectOne(new LambdaQueryWrapper<ShareLink>()
                .eq(ShareLink::getRecordId, recordId)
                .eq(ShareLink::getStatus, 1)
                .last("LIMIT 1"));
        if (link == null) {
            link = new ShareLink();
            link.setCode(newCode());
            link.setRecordId(recordId);
            link.setUserId(userId);
            link.setStatus(1);
            link.setViewCount(0L);
            link.setCreateTime(LocalDateTime.now());
            shareMapper.insert(link);
        }
        return toInfo(link, record);
    }

    /** 取消分享 */
    public void cancel(Long userId, Long recordId) {
        ShareLink link = shareMapper.selectOne(new LambdaQueryWrapper<ShareLink>()
                .eq(ShareLink::getRecordId, recordId)
                .eq(ShareLink::getUserId, userId)
                .eq(ShareLink::getStatus, 1)
                .last("LIMIT 1"));
        if (link == null) {
            throw new BizException(404, "该图片还没有分享链接");
        }
        link.setStatus(0);
        shareMapper.updateById(link);
    }

    /** 查询记录当前的有效分享链接（无则返回 null） */
    public Map<String, Object> currentOf(Long recordId) {
        ShareLink link = shareMapper.selectOne(new LambdaQueryWrapper<ShareLink>()
                .eq(ShareLink::getRecordId, recordId)
                .eq(ShareLink::getStatus, 1)
                .last("LIMIT 1"));
        if (link == null) return null;
        UploadRecord record = recordMapper.selectById(recordId);
        if (record == null) return null;
        return toInfo(link, record);
    }

    /** 读取分享内容（公开接口，无需登录） */
    public Map<String, Object> detail(String code) {
        ShareLink link = shareMapper.selectOne(new LambdaQueryWrapper<ShareLink>()
                .eq(ShareLink::getCode, code)
                .eq(ShareLink::getStatus, 1)
                .last("LIMIT 1"));
        if (link == null) {
            throw new BizException(404, "分享链接不存在或已被取消");
        }
        UploadRecord record = recordMapper.selectById(link.getRecordId());
        if (record == null) {
            throw new BizException(404, "分享的图片已被删除");
        }
        link.setViewCount(link.getViewCount() == null ? 1 : link.getViewCount() + 1);
        shareMapper.updateById(link);
        return toInfo(link, record);
    }

    /** 删除记录时同步清理分享链接 */
    public void deleteByRecordId(Long recordId) {
        shareMapper.delete(new LambdaQueryWrapper<ShareLink>().eq(ShareLink::getRecordId, recordId));
    }

    private Map<String, Object> toInfo(ShareLink link, UploadRecord record) {
        String nickname = "";
        User user = userMapper.selectById(record.getUserId());
        if (user != null && user.getNickname() != null) nickname = user.getNickname();
        Map<String, Object> info = new java.util.LinkedHashMap<>();
        info.put("code", link.getCode());
        info.put("recordId", record.getId());
        info.put("path", "/share/" + link.getCode());
        info.put("url", buildUrl(link.getCode()));
        info.put("cdnUrl", record.getCdnUrl());
        info.put("originName", record.getOriginName() == null ? "" : record.getOriginName());
        info.put("size", record.getSize() == null ? 0L : record.getSize());
        info.put("contentType", record.getContentType() == null ? "" : record.getContentType());
        info.put("createTime", String.valueOf(record.getCreateTime()));
        info.put("shareTime", String.valueOf(link.getCreateTime()));
        info.put("viewCount", link.getViewCount() == null ? 0L : link.getViewCount());
        info.put("owner", nickname);
        return info;
    }

    private String buildUrl(String code) {
        String base = baseUrl == null ? "" : baseUrl.trim().replaceAll("/+$", "");
        return base.isEmpty() ? "/share/" + code : base + "/share/" + code;
    }

    private String newCode() {
        StringBuilder sb = new StringBuilder(CODE_LEN);
        for (int i = 0; i < CODE_LEN; i++) {
            sb.append(CODE_CHARS.charAt(RANDOM.nextInt(CODE_CHARS.length())));
        }
        return sb.toString();
    }
}
