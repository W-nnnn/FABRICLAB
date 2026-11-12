package com.fabriclab.admin.controller;

import com.fabriclab.admin.model.AdminUser;
import com.fabriclab.admin.security.CsrfService;
import com.fabriclab.admin.service.AdminSessionService;
import com.fabriclab.admin.service.BrandService;
import jakarta.servlet.http.HttpSession;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/** 全局模型填充：CSRF 令牌、品牌信息与当前登录管理员身份（未登录不注入身份）。 */
@ControllerAdvice
public class AdminModelAdvice {

    private final CsrfService csrfService;
    private final AdminSessionService adminSessionService;
    private final BrandService brandService;

    public AdminModelAdvice(CsrfService csrfService, AdminSessionService adminSessionService,
                           BrandService brandService) {
        this.csrfService = csrfService;
        this.adminSessionService = adminSessionService;
        this.brandService = brandService;
    }

    @ModelAttribute
    public void populate(Model model, HttpSession session) {
        model.addAttribute("brand", brandService.brand());
        model.addAttribute("tagline", brandService.tagline());
        model.addAttribute("consoleName", brandService.consoleName());
        if (session != null) {
            model.addAttribute("csrfToken", csrfService.token(session));
            AdminUser admin = adminSessionService.from(session);
            if (admin != null) {
                model.addAttribute("currentAdmin", admin);
                model.addAttribute("signinAt", adminSessionService.signinAt(session));
            }
        }
    }
}
