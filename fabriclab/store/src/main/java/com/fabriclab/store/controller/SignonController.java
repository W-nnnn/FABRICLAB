package com.fabriclab.store.controller;

import com.fabriclab.store.exception.BusinessException;
import com.fabriclab.store.model.CurrentUser;
import com.fabriclab.store.security.AuthInterceptor;
import com.fabriclab.store.service.AuthService;
import com.fabriclab.store.service.BrandService;
import com.fabriclab.store.service.CartService;
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
 *
 * <p>未登录时点的「加入购物袋」会在登录成功后补做，再落到购物袋页，
 * 不让拦截把一次加购吞掉。</p>
 */
@Controller
public class SignonController {

    /** 必填项未填时的提示：与「账号或密码错误」区分，避免让顾客以为自己输错了。 */
    static final String MISSING_CREDENTIALS = "请输入账号和密码";

    private final AuthService authService;
    private final CurrentUserService currentUserService;
    private final BrandService brandService;
    private final CartService cartService;

    public SignonController(AuthService authService, CurrentUserService currentUserService,
                            BrandService brandService, CartService cartService) {
        this.authService = authService;
        this.currentUserService = currentUserService;
        this.brandService = brandService;
        this.cartService = cartService;
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
        String target = authService.safeReturnPath(returnTo);
        if (isBlank(username) || isBlank(password)) {
            model.addAttribute("error", MISSING_CREDENTIALS);
            if (!isBlank(username)) {
                model.addAttribute("inputUsername", username.trim());
            }
            model.addAttribute("returnTo", target);
            return "signon";
        }
        try {
            CurrentUser user = authService.login(username, password, request);
            if (resumePendingAdd(user, request)) {
                return "redirect:/cart";
            }
            return "redirect:" + target;
        } catch (BusinessException e) {
            model.addAttribute("error", e.getMessage());
            model.addAttribute("inputUsername", username.trim());
            model.addAttribute("returnTo", target);
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

    /** 登录前被拦下的加购：登录成功后补做一次；失败则留给返回页展示商品当前状态。 */
    private boolean resumePendingAdd(CurrentUser user, HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return false;
        }
        Object pending = session.getAttribute(AuthInterceptor.PENDING_CART_ADD);
        if (pending == null) {
            return false;
        }
        session.removeAttribute(AuthInterceptor.PENDING_CART_ADD);
        String[] parts = String.valueOf(pending).split("\\|", 2);
        try {
            cartService.addToCart(user.id(), parts[0], parts.length > 1 ? parts[1] : null);
            return true;
        } catch (BusinessException e) {
            return false;
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private void fillCommon(Model model) {
        model.addAttribute("brand", brandService.brand());
        model.addAttribute("tagline", brandService.tagline());
    }
}
