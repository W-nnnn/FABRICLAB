package com.fabriclab.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fabriclab.admin.entity.StoreUser;
import com.fabriclab.admin.exception.BusinessException;
import com.fabriclab.admin.mapper.StoreUserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/** 后台登录四类失败统一提示、会话固定防护与后台内返回页白名单（FR-A-01）。 */
class AdminAuthServiceTest {

    private StoreUserMapper storeUserMapper;
    private AdminAuthService adminAuthService;
    private String hash;

    @BeforeEach
    void setUp() {
        storeUserMapper = Mockito.mock(StoreUserMapper.class);
        adminAuthService = new AdminAuthService(storeUserMapper, new AdminSessionService());
        hash = new BCryptPasswordEncoder().encode("right-pass");
    }

    private StoreUser user(String role, String status) {
        StoreUser user = new StoreUser();
        user.setId(4L);
        user.setUsername("admin");
        user.setPasswordHash(hash);
        user.setDisplayName("平台管理员");
        user.setRole(role);
        user.setStatus(status);
        return user;
    }

    private MockHttpServletRequest request() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setSession(new MockHttpSession());
        return request;
    }

    @Test
    void enabledAdminLogsInAndRegeneratesSessionId() {
        when(storeUserMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(user("ADMIN", "ENABLED"));
        MockHttpServletRequest request = request();
        String oldId = request.getSession().getId();

        var admin = adminAuthService.login("admin", "right-pass", request);

        assertEquals(4L, admin.id());
        assertEquals("admin", admin.username());
        assertEquals("平台管理员", admin.displayName());
        assertNotEquals(oldId, request.getSession().getId(), "登录成功后必须更换会话标识");
        assertEquals(4L, request.getSession().getAttribute(AdminSessionService.KEY_ADMIN_ID));
        assertEquals("admin", request.getSession().getAttribute(AdminSessionService.KEY_ADMIN_USERNAME));
        String signinAt = (String) request.getSession().getAttribute(AdminSessionService.KEY_SIGNIN_AT);
        assertNotNull(signinAt);
        assertTrue(signinAt.matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}"), "登录时间格式");
    }

    @Test
    void fourFailureModesShareUnifiedMessage() {
        // 不存在 / 口令错误 / 已停用 / 顾客账号
        when(storeUserMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(
                null, user("ADMIN", "ENABLED"), user("ADMIN", "DISABLED"), user("CUSTOMER", "ENABLED"));

        for (String password : new String[]{"right-pass", "wrong-pass", "right-pass", "right-pass"}) {
            BusinessException e = assertThrows(BusinessException.class,
                    () -> adminAuthService.login("admin", password, request()));
            assertEquals(AdminAuthService.UNIFIED_FAILURE, e.getMessage());
        }
    }

    @Test
    void customerAccountNeverGetsAdminSession() {
        when(storeUserMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(user("CUSTOMER", "ENABLED"));
        MockHttpServletRequest request = request();
        assertThrows(BusinessException.class, () -> adminAuthService.login("admin", "right-pass", request));
        assertEquals(null, request.getSession().getAttribute(AdminSessionService.KEY_ADMIN_ID));
    }

    @Test
    void missingOrOversizedCredentialsUseUnifiedMessageWithoutDbHit() {
        for (String[] pair : new String[][]{{null, "x"}, {"  ", "x"}, {"a".repeat(51), "x"},
                {"admin", null}, {"admin", ""}}) {
            BusinessException e = assertThrows(BusinessException.class,
                    () -> adminAuthService.login(pair[0], pair[1], request()));
            assertEquals(AdminAuthService.UNIFIED_FAILURE, e.getMessage());
        }
        Mockito.verifyNoInteractions(storeUserMapper);
    }

    @Test
    void usernameIsTrimmedButPasswordIsNot() {
        when(storeUserMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(user("ADMIN", "ENABLED"));
        assertEquals(4L, adminAuthService.login("  admin  ", "right-pass", request()).id());
        BusinessException e = assertThrows(BusinessException.class,
                () -> adminAuthService.login("admin", " right-pass ", request()));
        assertEquals(AdminAuthService.UNIFIED_FAILURE, e.getMessage());
    }

    @Test
    void safeReturnPathOnlyAcceptsAdminPrefixedPaths() {
        assertEquals("/admin/console", adminAuthService.safeReturnPath(null));
        assertEquals("/admin/console", adminAuthService.safeReturnPath("   "));
        assertEquals("/admin/products", adminAuthService.safeReturnPath("/admin/products"));
        assertEquals("/admin/orders?status=PAID", adminAuthService.safeReturnPath("/admin/orders?status=PAID"));
        assertEquals("/admin/console", adminAuthService.safeReturnPath("/main"));
        assertEquals("/admin/console", adminAuthService.safeReturnPath("//evil.example"));
        assertEquals("/admin/console", adminAuthService.safeReturnPath("https://evil.example"));
        assertEquals("/admin/console", adminAuthService.safeReturnPath("/admin\\evil"));
        assertEquals("/admin/console", adminAuthService.safeReturnPath("/admin/a:b"));
    }
}
