package com.fabriclab.store.security;

import com.fabriclab.store.entity.StoreUser;
import com.fabriclab.store.mapper.StoreUserMapper;
import com.fabriclab.store.service.CurrentUserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 受保护入口访问检查（FR-U-02、AC-07）：
 * 未登录跳登录页；会话内身份每次请求对照数据库的角色与启用状态。
 */
class AuthInterceptorTest {

    private StoreUserMapper storeUserMapper;
    private AuthInterceptor interceptor;
    private final CurrentUserService currentUserService = new CurrentUserService();

    @BeforeEach
    void setUp() {
        storeUserMapper = Mockito.mock(StoreUserMapper.class);
        interceptor = new AuthInterceptor(storeUserMapper, currentUserService);
    }

    private MockHttpServletRequest requestWithSession(Long userId) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/cart");
        if (userId != null) {
            MockHttpSession session = new MockHttpSession();
            session.setAttribute(CurrentUserService.KEY_USER_ID, userId);
            session.setAttribute(CurrentUserService.KEY_USERNAME, "alice");
            session.setAttribute(CurrentUserService.KEY_DISPLAY_NAME, "林晓");
            request.setSession(session);
        }
        return request;
    }

    private StoreUser dbUser(String role, String status) {
        StoreUser user = new StoreUser();
        user.setId(7L);
        user.setUsername("alice");
        user.setRole(role);
        user.setStatus(status);
        return user;
    }

    private String redirectOf(MockHttpServletRequest request, HttpServletResponse response) {
        assertTrue(response.isCommitted());
        return ((MockHttpServletResponse) response).getRedirectedUrl();
    }

    @Test
    void anonymousIsRedirectedToSignonWithReturnPath() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/cart?keyword=x");
        MockHttpServletResponse response = new MockHttpServletResponse();
        boolean pass = interceptor.preHandle(request, response, new Object());
        assertFalse(pass);
        assertEquals("/signon?returnTo=%2Fcart%3Fkeyword%3Dx", redirectOf(request, response));
        Mockito.verifyNoInteractions(storeUserMapper);
    }

    @Test
    void loggedInEnabledCustomerPasses() throws Exception {
        when(storeUserMapper.selectById(7L)).thenReturn(dbUser("CUSTOMER", "ENABLED"));
        MockHttpServletRequest request = requestWithSession(7L);
        assertTrue(interceptor.preHandle(request, new MockHttpServletResponse(), new Object()));
    }

    @Test
    void disabledAfterLoginIsBlockedAndSessionInvalidated() throws Exception {
        when(storeUserMapper.selectById(7L)).thenReturn(dbUser("CUSTOMER", "DISABLED"));
        MockHttpServletRequest request = requestWithSession(7L);
        MockHttpSession session = (MockHttpSession) request.getSession();
        MockHttpServletResponse response = new MockHttpServletResponse();
        assertFalse(interceptor.preHandle(request, response, new Object()));
        assertTrue(session.isInvalid());
        assertEquals("/signon?returnTo=%2Fcart", redirectOf(request, response));
    }

    @Test
    void roleChangedAfterLoginIsBlocked() throws Exception {
        when(storeUserMapper.selectById(7L)).thenReturn(dbUser("ADMIN", "ENABLED"));
        MockHttpServletRequest request = requestWithSession(7L);
        MockHttpSession session = (MockHttpSession) request.getSession();
        MockHttpServletResponse response = new MockHttpServletResponse();
        assertFalse(interceptor.preHandle(request, response, new Object()));
        assertTrue(session.isInvalid());
    }

    @Test
    void deletedAccountIsBlocked() throws Exception {
        when(storeUserMapper.selectById(7L)).thenReturn(null);
        MockHttpServletRequest request = requestWithSession(7L);
        MockHttpServletResponse response = new MockHttpServletResponse();
        assertFalse(interceptor.preHandle(request, response, new Object()));
        assertTrue(response.isCommitted());
    }
}
