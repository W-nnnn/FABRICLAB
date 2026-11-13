package com.fabriclab.store.controller;

import com.fabriclab.store.config.ContentI18n;
import com.fabriclab.store.model.CurrentUser;
import com.fabriclab.store.security.CsrfService;
import com.fabriclab.store.service.CartService;
import com.fabriclab.store.service.CurrentUserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * 全局模型填充：CSRF 令牌、当前登录身份、购物车徽标与数据库内容翻译器。
 * 徽标为本人数据库购物车数量总和（含失效条目），游客不注入、模板按 0 展示。
 */
@ControllerAdvice
public class CurrentUserAdvice {

    private final CsrfService csrfService;
    private final CurrentUserService currentUserService;
    private final CartService cartService;
    // 无依赖无状态，直接实例化；@WebMvcTest 切片不扫描 @Component
    private final ContentI18n contentI18n = new ContentI18n();

    public CurrentUserAdvice(CsrfService csrfService, CurrentUserService currentUserService,
                             CartService cartService) {
        this.csrfService = csrfService;
        this.currentUserService = currentUserService;
        this.cartService = cartService;
    }

    @ModelAttribute
    public void populate(Model model, HttpSession session, HttpServletRequest request) {
        // 头部 EN / 中文 切换按钮据此高亮当前语言，取自会话语言而非请求头
        LangLinks.fill(model, request);
        // 头部系列线激活项：由请求路径与 category 参数推导，控制器无需各自传入
        model.addAttribute("navActive", navActive(request));
        // 数据库内容（分类/商品名等）翻译器：模板以 ${tr.t(...)} 调用
        model.addAttribute("tr", contentI18n);
        if (session != null) {
            model.addAttribute("csrfToken", csrfService.token(session));
            CurrentUser current = currentUserService.from(session);
            if (current != null) {
                model.addAttribute("currentUser", current);
                model.addAttribute("cartBadge", cartService.badge(current.id()));
            }
        }
    }

    /** 系列线激活项：/category?category=N 映射到五个系列线，其余页面不高亮。 */
    private String navActive(HttpServletRequest request) {
        if (!"/category".equals(request.getServletPath())) {
            return null;
        }
        String c = request.getParameter("category");
        if (c == null) {
            return null;
        }
        return switch (c) {
            case "1" -> "commute";
            case "2" -> "leisure";
            case "3" -> "outdoor";
            case "4" -> "occasion";
            case "5" -> "home";
            default -> null;
        };
    }
}
