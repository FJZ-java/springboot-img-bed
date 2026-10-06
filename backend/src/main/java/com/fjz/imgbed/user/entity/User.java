package com.fjz.imgbed.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("user")
public class User {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String username;

    /** BCrypt 哈希，绝不返回给前端 */
    private String password;

    private String nickname;

    /** 账号状态：1 正常、0 封禁。封禁后无法登录，已签发的令牌也立即失效 */
    private Integer status = 1;

    /** API 密钥（img_sk_ 前缀 + 随机串），用于接口调用认证，绝不公开展示给他人 */
    private String apiKey;

    private LocalDateTime createTime;

    public boolean isBanned() {
        return status == null || status == 0;
    }
}
