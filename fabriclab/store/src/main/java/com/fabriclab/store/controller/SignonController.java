package com.fabriclab.store.controller;

import com.fabriclab.store.exception.BusinessException;
import com.fabriclab.store.model.CurrentUser;
import com.fabriclab.store.service.AuthService;
import com.fabriclab.store.service.BrandService;
import com.fabriclab.store.service.CurrentUserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 登录与退出（FR-U-01）：/signon 登录页，POST + CSRF 退出。
 * 失败统一提示且用户名可回显、密码不回显；成功跳转站内返回页或首页。
 * 退出使本端会话（含临时确认）失效，返回公开页；数据库购物车不受影响。
 */
@Controller
public class SignonController {

    private final AuthService authService;
    private final CurrentUserService currentUserService;
    private final BrandService brandService;

    public SignonController(AuthService authService, CurrentUserService currentUserService,
                            BrandService brandService) {
        this.authService = authService;
        this.currentUserService = currentUserService;
        this.brandService = brandService;
    }

    @GetMapping("/signon")
    public String form(@RequestParam(required = false) String returnTo, Model model,
                       HttpSession session) {
        fillCommon(model);
        if (currentUserService.from(session) != null) {
            return "redirect:/main";
        }
        model.addAttribute("returnTo", authService.safeReturnPath(returnTo));
        return "signon";
    }

    @PostMapping("/signon")
    public String submit(@RequestParam(required = false) String username,
                         @RequestParam(required = false) String password,
                         @RequestParam(required = false) String returnTo,
                         HttpServletRequest request, Model model) {
        fillCommon(model);
        try {
            CurrentUser user = authService.login(username, password, request);
            return "redirect:" + authService.safeReturnPath(returnTo);
        } catch (BusinessException e) {
            model.addAttribute("error", e.getMessage());
            if (username != null) {
                model.addAttribute("inputUsername", username.trim());
            }
            model.addAttribute("returnTo", authService.safeReturnPath(returnTo));
            return "signon";
        }
    }

    @PostMapping("/signout")
    public String signout(HttpSession session) {
        if (session != null) {
            session.invalidate();
        }
        return "redirect:/main";
    }

    private void fillCommon(Model model) {
        model.addAttribute("brand", brandService.brand());
        model.addAttribute("tagline", brandService.tagline());
    }
}
