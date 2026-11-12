package com.fabriclab.admin.security;

import com.fabriclab.admin.entity.StoreUser;
import com.fabriclab.admin.mapper.StoreUserMapper;
import com.fabriclab.admin.model.AdminUser;
import com.fabriclab.admin.service.AdminSessionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.servlet.HandlerInterceptor;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * 后台统一访问检查（FR-A-01）：/admin/** 全部入口先过此关。
 * 未登录跳登录页并携带站内返回地址；已登录者每次请求复核数据库中的角色与启用状态——
 * 会话内身份不足以放行，登录后被停用或改角色、被删除的账号在下一次请求即被拦截并失效会话。
 * 顾客账号即使持有后台会话 Cookie 也无法通过角色复核。
 */
public class AdminAuthInterceptor implements HandlerInterceptor {

    private final StoreUserMapper storeUserMapper;
    private final AdminSessionService adminSessionService;

    public AdminAuthInterceptor(StoreUserMapper storeUserMapper, AdminSessionService adminSessionService) {
        this.storeUserMapper = storeUserMapper;
        this.adminSessionService = adminSessionService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        HttpSession session = request.getSession(false);
        AdminUser admin = adminSessionService.from(session);
        if (admin == null) {
            response.sendRedirect(redirectTarget(request));
            return false;
        }
        StoreUser user = storeUserMapper.selectById(admin.id());
        boolean usable = user != null
                && "ADMIN".equals(user.getRole())
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
        return "/admin/signon?returnTo=" + URLEncoder.encode(returnTo, StandardCharsets.UTF_8);
    }
}
