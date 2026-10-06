package com.fjz.imgbed.config;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.server.ResponseStatusException;

/**
 * SPA 路由回退：前端使用 history 模式，浏览器直达 /upload、/share/{code} 等深层路由时，
 * 后端没有对应的 Controller，需要回退到 index.html，交给前端路由接管。
 *
 * <p>匹配规则：只匹配「不含文件扩展名」的路径（一层或两层），
 * 静态资源（/assets/xxx.js）带点号不会被这里拦截，仍走默认静态资源处理；
 * /api/** 由具体 Controller 精确匹配优先处理，只有未命中时才落到这里并返回 404。</p>
 */
@Controller
public class SpaForwardController {

    @RequestMapping({"/", "/{path:[^.]*}", "/{path:[^.]*}/{code:[^.]*}"})
    public String forward(HttpServletRequest request) {
        String uri = request.getRequestURI();
        // /api/xxx 是后端接口；单独的 /api 是前端「API 接口」页面，需正常回退到 index.html
        if (uri.startsWith("/api/")) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "接口不存在");
        }
        return "forward:/index.html";
    }
}
