package com.fabriclab.store.security;

import com.fabriclab.store.exception.CsrfViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** 会话级 CSRF 令牌：生成、复用、校验（AC-31、NFR-04）。 */
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
    }

    @Test
    void differentSessionsGetDifferentTokens() {
        String a = service.token(new MockHttpSession());
        String b = service.token(new MockHttpSession());
        org.junit.jupiter.api.Assertions.assertNotEquals(a, b);
    }

    @Test
    void matchingTokenPasses() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/signout");
        MockHttpSession session = new MockHttpSession();
        request.setSession(session);
        request.setParameter(CsrfService.PARAM_NAME, service.token(session));
        assertDoesNotThrow(() -> service.verify(request));
    }

    @Test
    void missingSessionOrTokenOrMismatchThrows() {
        CsrfViolationException thrown = assertThrows(CsrfViolationException.class,
                () -> service.verify(new MockHttpServletRequest("POST", "/signout")));
        assertEquals("无效的表单防护请求", thrown.getMessage());

        MockHttpServletRequest noToken = new MockHttpServletRequest("POST", "/signout");
        MockHttpSession session = new MockHttpSession();
        noToken.setSession(session);
        service.token(session);
        assertThrows(CsrfViolationException.class, () -> service.verify(noToken));

        MockHttpServletRequest mismatch = new MockHttpServletRequest("POST", "/signout");
        mismatch.setSession(session);
        mismatch.setParameter(CsrfService.PARAM_NAME, "other-token");
        assertThrows(CsrfViolationException.class, () -> service.verify(mismatch));
    }
}
