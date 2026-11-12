package com.fabriclab.store.controller;

import com.fabriclab.store.model.CurrentUser;
import com.fabriclab.store.security.CsrfService;
import com.fabriclab.store.service.CurrentUserService;
import jakarta.servlet.http.HttpSession;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * 全局模型填充：CSRF 令牌与当前登录身份。
 * 模板据此渲染导航登录态与写表单隐藏字段。
 */
@ControllerAdvice
public class CurrentUserAdvice {

    private final CsrfService csrfService;
    private final CurrentUserService currentUserService;

    public CurrentUserAdvice(CsrfService csrfService, CurrentUserService currentUserService) {
        this.csrfService = csrfService;
        this.currentUserService = currentUserService;
    }

    @ModelAttribute
    public void populate(Model model, HttpSession session) {
        if (session != null) {
            model.addAttribute("csrfToken", csrfService.token(session));
            CurrentUser current = currentUserService.from(session);
            if (current != null) {
                model.addAttribute("currentUser", current);
            }
        }
    }
}
