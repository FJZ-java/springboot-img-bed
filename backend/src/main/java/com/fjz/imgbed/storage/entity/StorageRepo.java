package com.fjz.imgbed.storage.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 存储仓库配置：一张表管理多个 GitHub 仓库，上传时随机挑一个启用中的仓库落盘。
 */
@Data
@TableName("storage_repo")
public class StorageRepo {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 展示名，如「主仓库」 */
    private String name;

    private String owner;

    private String repo;

    private String branch;

    /** 仓库内存储目录前缀 */
    private String dirPrefix;

    /** 单独的 Token，留空表示使用全局 Token */
    private String token;

    /** 1 启用（参与随机上传）/ 0 停用 */
    private Integer enabled;

    /** 权重，越大被随机到的概率越高 */
    private Integer weight;

    /** 仓库内文件总数（同步时统计） */
    private Long fileCount;

    /** 仓库内文件总字节（同步时统计） */
    private Long totalSize;

    /** GitHub 返回的仓库占用体积（KB） */
    private Long diskSize;

    private String remark;

    private LocalDateTime lastSyncTime;

    private LocalDateTime createTime;
}
