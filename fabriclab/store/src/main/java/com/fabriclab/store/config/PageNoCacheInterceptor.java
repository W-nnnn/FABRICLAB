package com.fabriclab.store.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 页面响应禁用缓存：模板或静态资源版本更新后，浏览器刷新即可拿到新版，
 * 不再显示缓存的旧页面。静态资源（CSS/JS/图片）由 Spring 资源缓存策略单独控制，
 * 这里排除其路径，保留协商缓存。
 */
public class PageNoCacheInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("Pragma", "no-cache");
        return true;
    }
}
