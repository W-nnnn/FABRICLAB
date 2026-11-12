package com.fabriclab.admin.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;

/** 对全部 POST 写请求校验 CSRF 令牌；GET 只读请求不校验。 */
public class CsrfInterceptor implements HandlerInterceptor {

    private final CsrfService csrfService;

    public CsrfInterceptor(CsrfService csrfService) {
        this.csrfService = csrfService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        csrfService.verify(request);
        return true;
    }
}
