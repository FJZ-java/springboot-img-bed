package com.fjz.imgbed.security;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 取真实客户端 IP 的工具（限流维度用）。
 */
public final class ClientIps {

    private ClientIps() {
    }

    /** 优先取反向代理头，取不到用直连地址 */
    public static String of(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            int comma = forwarded.indexOf(',');
            return (comma > 0 ? forwarded.substring(0, comma) : forwarded).trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
    }
}
