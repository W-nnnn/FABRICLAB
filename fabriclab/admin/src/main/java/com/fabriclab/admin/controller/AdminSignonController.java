package com.fabriclab.admin.controller;

import com.fabriclab.admin.exception.BusinessException;
import com.fabriclab.admin.model.AdminUser;
import com.fabriclab.admin.service.AdminAuthService;
import com.fabriclab.admin.service.AdminSessionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 后台登录与退出（FR-A-01）：/admin/signon 登录页，POST + CSRF 退出。
 * 失败统一提示且账号可回显、口令不回显；成功跳转后台内返回页或控制台。
 * 退出使后台会话失效，返回登录页；不影响用户端会话与数据库购物车。
 */
@Controller
public class AdminSignonController {

    private final AdminAuthService adminAuthService;
    private final AdminSessionService adminSessionService;

    public AdminSignonController(AdminAuthService adminAuthService, AdminSessionService adminSessionService) {
        this.adminAuthService = adminAuthService;
        this.adminSessionService = adminSessionService;
    }

    @GetMapping("/admin/signon")
    public String form(@RequestParam(required = false) String returnTo, Model model, HttpSession session) {
        if (adminSessionService.from(session) != null) {
            return "redirect:" + AdminAuthService.CONSOLE_PATH;
        }
        model.addAttribute("returnTo", adminAuthService.safeReturnPath(returnTo));
        return "signon";
    }

    @PostMapping("/admin/signon")
    public String submit(@RequestParam(required = false) String username,
                         @RequestParam(required = false) String password,
                         @RequestParam(required = false) String returnTo,
                         HttpServletRequest request, Model model) {
        try {
            AdminUser admin = adminAuthService.login(username, password, request);
            return "redirect:" + adminAuthService.safeReturnPath(returnTo);
        } catch (BusinessException e) {
            model.addAttribute("error", e.getMessage());
            if (username != null) {
                model.addAttribute("inputUsername", username.trim());
            }
            model.addAttribute("returnTo", adminAuthService.safeReturnPath(returnTo));
            return "signon";
        }
    }

    @PostMapping("/admin/signout")
    public String signout(HttpSession session) {
        if (session != null) {
            session.invalidate();
        }
        return "redirect:/admin/signon";
    }
}
