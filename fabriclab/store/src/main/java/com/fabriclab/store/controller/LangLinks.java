package com.fabriclab.store.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.ui.Model;

/**
 * 头部语言切换所需的两项模型属性：当前语言，以及去掉 lang 之后的当前地址。
 * 正常页面由 {@link CurrentUserAdvice} 填充；异常页由 {@link GlobalExceptionHandler} 直接渲染，
 * 此时 @ModelAttribute 的模型属性不保留，因此两处共用本工具。
 */
final class LangLinks {

    private LangLinks() {
    }

    static void fill(Model model, HttpServletRequest request) {
        model.addAttribute("lang", LocaleContextHolder.getLocale().getLanguage());
        String query = request.getQueryString();
        String rest = query == null ? ""
                : query.replaceAll("(^|&)lang=[^&]*", "").replaceAll("^&", "");
        model.addAttribute("langHref",
                request.getRequestURI() + "?" + (rest.isEmpty() ? "" : rest + "&"));
    }
}
