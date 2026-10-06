package com.fjz.imgbed.record.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fjz.imgbed.auth.UserContext;
import com.fjz.imgbed.common.BizException;
import com.fjz.imgbed.common.Result;
import com.fjz.imgbed.record.entity.UploadRecord;
import com.fjz.imgbed.record.service.UploadRecordService;
import com.fjz.imgbed.security.RateLimiter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/records")
@RequiredArgsConstructor
public class UploadRecordController {

    private final UploadRecordService recordService;
    private final RateLimiter rateLimiter;

    /** 登录用户每小时最多上传张数（默认 200，防刷仓库） */
    @Value("${imgbed.security.upload-max-per-hour:200}")
    private int uploadMaxPerHour;

    /** 上传图片（multipart，字段名 file） */
    @PostMapping("/upload")
    public Result<UploadRecord> upload(@RequestParam("file") MultipartFile file) {
        Long userId = UserContext.requireUserId();
        if (!rateLimiter.tryAcquire("upload:user:" + userId, uploadMaxPerHour, 3_600_000L)) {
            throw new BizException(429, "上传太频繁啦，每小时最多 " + uploadMaxPerHour + " 张，请稍后再试");
        }
        return Result.ok(recordService.upload(userId, file));
    }

    /** 分页查询当前用户上传记录 */
    @GetMapping
    public Result<Page<UploadRecord>> page(@RequestParam(defaultValue = "1") int page,
                                           @RequestParam(defaultValue = "12") int size) {
        return Result.ok(recordService.page(UserContext.requireUserId(), page, size));
    }

    /** 删除记录（同时删除 GitHub 仓库文件） */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        recordService.delete(UserContext.requireUserId(), id);
        return Result.ok();
    }

    /** 批量删除：body = { ids: [1,2,3] }，逐张删除，失败项单独返回 */
    @PostMapping("/batch-delete")
    public Result<Map<String, Object>> batchDelete(@RequestBody Map<String, Object> body) {
        Object raw = body == null ? null : body.get("ids");
        List<Long> ids = new java.util.ArrayList<>();
        if (raw instanceof List<?> list) {
            for (Object o : list) {
                if (o instanceof Number n) ids.add(n.longValue());
                else if (o != null) {
                    try {
                        ids.add(Long.parseLong(String.valueOf(o).trim()));
                    } catch (NumberFormatException ignored) { /* 跳过非法 id */ }
                }
            }
        }
        return Result.ok(recordService.deleteBatch(UserContext.requireUserId(), ids));
    }
}
