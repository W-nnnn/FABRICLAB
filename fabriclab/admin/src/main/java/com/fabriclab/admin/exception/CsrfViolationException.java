package com.fabriclab.admin.exception;

/** 无效表单防护请求：POST 缺少或不匹配 CSRF 令牌（AC-31、NFR-04）。 */
public class CsrfViolationException extends RuntimeException {

    public CsrfViolationException() {
        super("无效的表单防护请求");
    }
}
