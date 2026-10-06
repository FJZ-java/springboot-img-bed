package com.fjz.imgbed.record.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("upload_record")
public class UploadRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    /** 存储文件名（UUID 重命名后） */
    private String fileName;

    /** 原始文件名 */
    private String originName;

    /** 仓库内完整路径 */
    private String githubPath;

    /** GitHub 文件 sha（删除时需要） */
    private String sha;

    /** jsDelivr CDN 地址 */
    private String cdnUrl;

    /** 实际存储的仓库（storage_repo.id），删除时要回到同一个仓库 */
    private Long repoId;

    /** 仓库快照名 owner/repo，仓库配置被删后也能看出图片来自哪里 */
    private String repoName;

    private Long size;

    private String contentType;

    private LocalDateTime createTime;
}
