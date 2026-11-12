package com.fabriclab.admin.security;

import com.fabriclab.admin.exception.CsrfViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** 后台会话级 CSRF 令牌：生成、复用、校验（AC-31、NFR-04）。 */
class CsrfServiceTest {

    private CsrfService service;

    @BeforeEach
    void setUp() {
        service = new CsrfService();
    }

    @Test
    void tokenIsCreatedOnceAndReusedPerSession() {
        MockHttpSession session = new MockHttpSession();
        String token = service.token(session);
        assertEquals(token, service.token(session));
        assertEquals(token, session.getAttribute(CsrfService.SESSION_KEY));
        assertEquals("adminCsrfToken", CsrfService.SESSION_KEY, "后台令牌键须与用户端命名空间分离");
    }

    @Test
    void differentSessionsGetDifferentTokens() {
        assertNotEquals(service.token(new MockHttpSession()), service.token(new MockHttpSession()));
    }

    @Test
    void matchingTokenPasses() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/admin/signout");
        MockHttpSession session = new MockHttpSession();
        request.setSession(session);
        request.setParameter(CsrfService.PARAM_NAME, service.token(session));
        assertDoesNotThrow(() -> service.verify(request));
    }

    @Test
    void missingSessionOrTokenOrMismatchThrows() {
        CsrfViolationException thrown = assertThrows(CsrfViolationException.class,
                () -> service.verify(new MockHttpServletRequest("POST", "/admin/signout")));
        assertEquals("无效的表单防护请求", thrown.getMessage());

        MockHttpServletRequest noToken = new MockHttpServletRequest("POST", "/admin/signout");
        MockHttpSession session = new MockHttpSession();
        noToken.setSession(session);
        service.token(session);
        assertThrows(CsrfViolationException.class, () -> service.verify(noToken));

        MockHttpServletRequest mismatch = new MockHttpServletRequest("POST", "/admin/signout");
        mismatch.setSession(session);
        mismatch.setParameter(CsrfService.PARAM_NAME, "other-token");
        assertThrows(CsrfViolationException.class, () -> service.verify(mismatch));
    }
}
