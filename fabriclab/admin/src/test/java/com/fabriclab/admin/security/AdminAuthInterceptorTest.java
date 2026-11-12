package com.fabriclab.admin.security;

import com.fabriclab.admin.entity.StoreUser;
import com.fabriclab.admin.mapper.StoreUserMapper;
import com.fabriclab.admin.service.AdminSessionService;
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
import static org.mockito.Mockito.when;

/**
 * 后台统一访问检查（FR-A-01）：未登录跳登录页；
 * 会话内身份每次请求对照数据库复核角色 ADMIN 与启用状态。
 */
class AdminAuthInterceptorTest {

    private StoreUserMapper storeUserMapper;
    private AdminAuthInterceptor interceptor;
    private final AdminSessionService adminSessionService = new AdminSessionService();

    @BeforeEach
    void setUp() {
        storeUserMapper = Mockito.mock(StoreUserMapper.class);
        interceptor = new AdminAuthInterceptor(storeUserMapper, adminSessionService);
    }

    private MockHttpServletRequest request(Long adminId) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/admin/console");
        if (adminId != null) {
            MockHttpSession session = new MockHttpSession();
            session.setAttribute(AdminSessionService.KEY_ADMIN_ID, adminId);
            session.setAttribute(AdminSessionService.KEY_ADMIN_USERNAME, "admin");
            session.setAttribute(AdminSessionService.KEY_ADMIN_DISPLAY_NAME, "平台管理员");
            request.setSession(session);
        }
        return request;
    }

    private StoreUser dbUser(String role, String status) {
        StoreUser user = new StoreUser();
        user.setId(4L);
        user.setUsername("admin");
        user.setRole(role);
        user.setStatus(status);
        return user;
    }

    @Test
    void anonymousIsRedirectedToSignonWithReturnPath() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/admin/orders?status=PAID");
        MockHttpServletResponse response = new MockHttpServletResponse();
        assertFalse(interceptor.preHandle(request, response, new Object()));
        assertTrue(response.isCommitted());
        assertEquals("/admin/signon?returnTo=%2Fadmin%2Forders%3Fstatus%3DPAID",
                response.getRedirectedUrl());
        Mockito.verifyNoInteractions(storeUserMapper);
    }

    @Test
    void enabledAdminPasses() throws Exception {
        when(storeUserMapper.selectById(4L)).thenReturn(dbUser("ADMIN", "ENABLED"));
        assertTrue(interceptor.preHandle(request(4L), new MockHttpServletResponse(), new Object()));
    }

    @Test
    void disabledAfterLoginIsBlockedAndSessionInvalidated() throws Exception {
        when(storeUserMapper.selectById(4L)).thenReturn(dbUser("ADMIN", "DISABLED"));
        MockHttpServletRequest request = request(4L);
        MockHttpSession session = (MockHttpSession) request.getSession();
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertFalse(interceptor.preHandle(request, response, new Object()));
        assertTrue(session.isInvalid(), "停用后后台会话必须失效");
        assertEquals("/admin/signon?returnTo=%2Fadmin%2Fconsole", response.getRedirectedUrl());
    }

    @Test
    void customerRoleOnAdminSessionIsBlocked() throws Exception {
        when(storeUserMapper.selectById(4L)).thenReturn(dbUser("CUSTOMER", "ENABLED"));
        MockHttpServletRequest request = request(4L);
        MockHttpSession session = (MockHttpSession) request.getSession();
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertFalse(interceptor.preHandle(request, response, new Object()));
        assertTrue(session.isInvalid());
    }

    @Test
    void deletedAccountIsBlocked() throws Exception {
        when(storeUserMapper.selectById(4L)).thenReturn(null);
        MockHttpServletResponse response = new MockHttpServletResponse();
        assertFalse(interceptor.preHandle(request(4L), response, new Object()));
        assertTrue(response.isCommitted());
    }
}
