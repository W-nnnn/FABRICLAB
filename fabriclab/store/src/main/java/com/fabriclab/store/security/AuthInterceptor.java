package com.fabriclab.store.security;

import com.fabriclab.store.entity.StoreUser;
import com.fabriclab.store.mapper.StoreUserMapper;
import com.fabriclab.store.model.CurrentUser;
import com.fabriclab.store.service.CurrentUserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.servlet.HandlerInterceptor;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * 受保护入口访问检查（FR-U-02）：未登录跳登录页（携带站内返回地址）；
 * 已登录者每次请求复核数据库中的角色与启用状态——会话内身份不足以放行，
 * 登录后被禁用或改角色的账号在下一次受保护请求即被拦截并失效会话。
 * 数据归属校验由 CurrentUserService.requireOwner 在各业务入口执行。
 */
public class AuthInterceptor implements HandlerInterceptor {

    private final StoreUserMapper storeUserMapper;
    private final CurrentUserService currentUserService;

    public AuthInterceptor(StoreUserMapper storeUserMapper, CurrentUserService currentUserService) {
        this.storeUserMapper = storeUserMapper;
        this.currentUserService = currentUserService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        HttpSession session = request.getSession(false);
        CurrentUser current = currentUserService.from(session);
        if (current == null) {
            response.sendRedirect(redirectTarget(request));
            return false;
        }
        StoreUser user = storeUserMapper.selectById(current.id());
        boolean usable = user != null
                && "CUSTOMER".equals(user.getRole())
                && "ENABLED".equals(user.getStatus());
        if (!usable) {
            session.invalidate();
            response.sendRedirect(redirectTarget(request));
            return false;
        }
        return true;
    }

    private String redirectTarget(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String query = request.getQueryString();
        String returnTo = query == null || query.isBlank() ? uri : uri + "?" + query;
        return "/signon?returnTo=" + URLEncoder.encode(returnTo, StandardCharsets.UTF_8);
    }
}
