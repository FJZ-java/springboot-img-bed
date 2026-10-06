package com.fjz.imgbed.common;

import lombok.Getter;

/**
 * 业务异常：携带错误码，由全局处理器转换为统一响应。
 */
@Getter
public class BizException extends RuntimeException {

    private final int code;

    public BizException(String message) {
        this(400, message);
    }

    public BizException(int code, String message) {
        super(message);
        this.code = code;
    }
}
