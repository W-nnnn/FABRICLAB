package com.fabriclab.store.security;

import com.fabriclab.store.entity.StoreUser;
import com.fabriclab.store.mapper.StoreUserMapper;
import com.fabriclab.store.model.CurrentUser;
import com.fabriclab.store.service.AuthService;
import com.fabriclab.store.service.CurrentUserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.servlet.HandlerInterceptor;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * 受保护入口访问检查（FR-U-02）：未登录跳登录页（携带站内返回地址）；
 * 已登录者每次请求复核数据库中的角色与启用状态——会话内身份不足以放行，
 * 登录后被禁用或改角色的账号在下一次受保护请求即被拦截并失效会话。
 * 数据归属校验由 CurrentUserService.requireOwner 在各业务入口执行。
 *
 * <p>返回地址只对 GET / HEAD 取当前地址：POST 端点（如 /cart/add）没有可回跳的页面，
 * 直接回跳会撞出 405。这类请求改为记住意图（待加购的商品与数量），登录成功后补做，
 * 回落到来源页或购物袋，保证「未登录加购」不会丢件。</p>
 */
public class AuthInterceptor implements HandlerInterceptor {

    /** 登录后补做的加购意图：值为 {@code productId|quantity}。 */
    public static final String PENDING_CART_ADD = "fl.pendingCartAdd";

    private static final String CART_ADD_PATH = "/cart/add";

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
        String returnTo = isReadable(request) ? currentAddress(request) : addressAfterLogin(request);
        return "/signon?returnTo=" + URLEncoder.encode(
                AuthService.stripPathParameters(returnTo), StandardCharsets.UTF_8);
    }

    private boolean isReadable(HttpServletRequest request) {
        String method = request.getMethod();
        return "GET".equalsIgnoreCase(method) || "HEAD".equalsIgnoreCase(method);
    }

    private String currentAddress(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String query = request.getQueryString();
        return query == null || query.isBlank() ? uri : uri + "?" + query;
    }

    /**
     * 写操作不可回跳：加购记下意图，登录后补做；其余写操作回来源页，
     * 取不到来源页就回购物袋。
     */
    private String addressAfterLogin(HttpServletRequest request) {
        String path = AuthService.stripPathParameters(request.getRequestURI());
        if (CART_ADD_PATH.equals(path)) {
            String productId = request.getParameter("productId");
            if (productId != null && !productId.isBlank()) {
                String quantity = request.getParameter("quantity");
                request.getSession(true).setAttribute(PENDING_CART_ADD,
                        productId.trim() + "|" + (quantity == null ? "" : quantity.trim()));
                return "/cart";
            }
        }
        return refererPath(request);
    }

    private String refererPath(HttpServletRequest request) {
        String referer = request.getHeader("Referer");
        if (referer == null || referer.isBlank()) {
            return "/cart";
        }
        try {
            URI uri = URI.create(referer);
            String path = uri.getRawPath();
            if (path == null || !path.startsWith("/") || path.startsWith("//")) {
                return "/cart";
            }
            String query = uri.getRawQuery();
            return query == null || query.isBlank() ? path : path + "?" + query;
        } catch (IllegalArgumentException e) {
            return "/cart";
        }
    }
}
