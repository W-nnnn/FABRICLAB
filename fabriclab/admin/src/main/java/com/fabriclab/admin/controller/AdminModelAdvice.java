package com.fabriclab.admin.controller;

import com.fabriclab.admin.model.AdminUser;
import com.fabriclab.admin.security.CsrfService;
import com.fabriclab.admin.service.AdminSessionService;
import com.fabriclab.admin.service.BrandService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

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
    public void populate(Model model, HttpSession session, HttpServletRequest request) {
        model.addAttribute("brand", brandService.brand());
        model.addAttribute("tagline", brandService.tagline());
        model.addAttribute("consoleName", brandService.consoleName());

        boolean isEn = "en".equals(LocaleContextHolder.getLocale().getLanguage());
        model.addAttribute("isEn", isEn);
        model.addAttribute("langZhUrl", langUrl(request, "zh"));
        model.addAttribute("langEnUrl", langUrl(request, "en"));
        if (request != null) {
            model.addAttribute("kw", request.getParameter("kw"));
        }
        if (session != null) {
            model.addAttribute("csrfToken", csrfService.token(session));
            AdminUser admin = adminSessionService.from(session);
            if (admin != null) {
                model.addAttribute("currentAdmin", admin);
                model.addAttribute("signinAt", adminSessionService.signinAt(session));
            }
        }
    }

    /** 语言切换保留当前路径与查询条件，只替换 lang 参数。 */
    private String langUrl(HttpServletRequest request, String lang) {
        if (request == null) {
            return "/admin/console?lang=" + lang;
        }
        Map<String, String> params = new LinkedHashMap<>();
        String query = request.getQueryString();
        if (query != null) {
            for (String pair : query.split("&")) {
                int i = pair.indexOf('=');
                String key = i < 0 ? pair : pair.substring(0, i);
                if (key.isEmpty() || "lang".equals(key)) {
                    continue;
                }
                params.put(key, i < 0 ? "" : pair.substring(i + 1));
            }
        }
        params.put("lang", lang);
        String q = params.entrySet().stream()
                .map(e -> e.getKey() + "=" + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));
        return request.getRequestURI() + "?" + q;
    }
}
