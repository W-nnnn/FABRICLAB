package com.fabriclab.store.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Service;

/**
 * 会话级 CSRF 令牌（AC-31、NFR-04）：写请求必须携带与会话一致的令牌。
 * 令牌由服务端生成并保存在会话中，页面仅经隐藏字段携带；不匹配即拒绝。
 */
@Service
public class CsrfService {

    public static final String SESSION_KEY = "storeCsrfToken";
    public static final String PARAM_NAME = "_csrf";

    /** 取当前会话令牌，不存在则生成并保存。 */
    public String token(HttpSession session) {
        Object existing = session.getAttribute(SESSION_KEY);
        if (existing instanceof String value && !value.isBlank()) {
            return value;
        }
        String token = java.util.UUID.randomUUID().toString();
        session.setAttribute(SESSION_KEY, token);
        return token;
    }

    /** 校验 POST 携带令牌与会话令牌一致；不一致抛 CsrfViolationException。 */
    public void verify(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        Object expected = session == null ? null : session.getAttribute(SESSION_KEY);
        String provided = request.getParameter(PARAM_NAME);
        if (!(expected instanceof String value) || provided == null || !provided.equals(value)) {
            throw new com.fabriclab.store.exception.CsrfViolationException();
        }
    }
}
