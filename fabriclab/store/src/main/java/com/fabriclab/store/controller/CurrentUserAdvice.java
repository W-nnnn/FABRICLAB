package com.fabriclab.store.controller;

import com.fabriclab.store.model.CurrentUser;
import com.fabriclab.store.security.CsrfService;
import com.fabriclab.store.service.CartService;
import com.fabriclab.store.service.CurrentUserService;
import jakarta.servlet.http.HttpSession;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * 全局模型填充：CSRF 令牌、当前登录身份与购物车徽标。
 * 徽标为本人数据库购物车数量总和（含失效条目），游客不注入、模板按 0 展示。
 */
@ControllerAdvice
public class CurrentUserAdvice {

    private final CsrfService csrfService;
    private final CurrentUserService currentUserService;
    private final CartService cartService;

    public CurrentUserAdvice(CsrfService csrfService, CurrentUserService currentUserService,
                             CartService cartService) {
        this.csrfService = csrfService;
        this.currentUserService = currentUserService;
        this.cartService = cartService;
    }

    @ModelAttribute
    public void populate(Model model, HttpSession session) {
        if (session != null) {
            model.addAttribute("csrfToken", csrfService.token(session));
            CurrentUser current = currentUserService.from(session);
            if (current != null) {
                model.addAttribute("currentUser", current);
                model.addAttribute("cartBadge", cartService.badge(current.id()));
            }
        }
    }
}
