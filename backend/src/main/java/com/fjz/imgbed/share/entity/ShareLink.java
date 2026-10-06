package com.fjz.imgbed.share.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("share_link")
public class ShareLink {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 分享短码，URL 中使用 */
    private String code;

    private Long recordId;

    private Long userId;

    /** 1 有效 / 0 已取消 */
    private Integer status;

    private Long viewCount;

    private LocalDateTime createTime;
}
