package com.fjz.imgbed.carousel.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("carousel_slide")
public class CarouselSlide {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 轮播图地址（图床 CDN 链接或任意外链） */
    private String imageUrl;

    /** 展示标题（叠加在图片左下角） */
    private String title;

    /** 点击跳转链接（可空，空则点击无动作） */
    private String linkUrl;

    /** 排序，小的在前 */
    private Integer sortOrder;

    /** 1 启用 / 0 停用，停用的不会出现在广告位 */
    private Integer enabled = 1;

    private LocalDateTime createTime;
}
