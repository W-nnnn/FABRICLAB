package com.fabriclab.store.controller;

import com.fabriclab.store.entity.StoreUser;
import com.fabriclab.store.exception.BusinessException;
import com.fabriclab.store.service.BrandService;
import com.fabriclab.store.service.CaptchaService;
import com.fabriclab.store.service.RegistrationService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 注册页（指导书「注册 / 验证码」兼容实现）：GET 整页刷新，POST 表单提交。
 * 失败时重新渲染表单并发放新验证码：用户名可回显，密码与验证码不回显。
 * 注册成功不做自动登录（登录功能在 T8 开放）；页面不出现找回密码与第三方登录。
 */
@Controller
public class RegisterController {

    private final BrandService brandService;
    private final RegistrationService registrationService;
    private final CaptchaService captchaService;

    public RegisterController(BrandService brandService,
                              RegistrationService registrationService,
                              CaptchaService captchaService) {
        this.brandService = brandService;
        this.registrationService = registrationService;
        this.captchaService = captchaService;
    }

    @GetMapping("/register")
    public String form(Model model) {
        fillCommon(model);
        addCaptcha(model);
        return "register";
    }

    @PostMapping("/register")
    public String submit(@RequestParam(required = false) String username,
                         @RequestParam(required = false) String password,
                         @RequestParam(required = false) String confirm,
                         @RequestParam(required = false) String captchaToken,
                         @RequestParam(required = false) String captchaInput,
                         Model model) {
        fillCommon(model);
        try {
            StoreUser user = registrationService.register(username, password, confirm, captchaToken, captchaInput);
            model.addAttribute("registeredUsername", user.getUsername());
        } catch (BusinessException e) {
            model.addAttribute("error", e.getMessage());
            if (username != null) {
                model.addAttribute("inputUsername", username.trim());
            }
        }
        addCaptcha(model);
        return "register";
    }

    private void fillCommon(Model model) {
        model.addAttribute("brand", brandService.brand());
        model.addAttribute("tagline", brandService.tagline());
    }

    private void addCaptcha(Model model) {
        model.addAttribute("captcha", captchaService.generate());
    }
}
