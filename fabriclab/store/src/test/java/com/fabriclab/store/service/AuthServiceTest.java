package com.fabriclab.store.service;

import com.fabriclab.store.entity.StoreUser;
import com.fabriclab.store.exception.BusinessException;
import com.fabriclab.store.mapper.StoreUserMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/** 登录四态统一失败、会话固定防护与站内返回页白名单（FR-U-01）。 */
class AuthServiceTest {

    private StoreUserMapper storeUserMapper;
    private AuthService authService;
    private String hash;

    @BeforeEach
    void setUp() {
        storeUserMapper = Mockito.mock(StoreUserMapper.class);
        authService = new AuthService(storeUserMapper);
        hash = new BCryptPasswordEncoder().encode("right-pass");
    }

    private StoreUser user(String role, String status) {
        StoreUser user = new StoreUser();
        user.setId(7L);
        user.setUsername("alice");
        user.setPasswordHash(hash);
        user.setDisplayName("林晓");
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
    void enabledCustomerLogsInAndRegeneratesSessionId() {
        when(storeUserMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(user("CUSTOMER", "ENABLED"));
        MockHttpServletRequest request = request();
        String oldId = request.getSession().getId();

        var current = authService.login("alice", "right-pass", request);

        assertEquals(7L, current.id());
        assertEquals("alice", current.username());
        assertEquals("林晓", current.displayName());
        assertNotEquals(oldId, request.getSession().getId());
        assertEquals(7L, request.getSession().getAttribute(CurrentUserService.KEY_USER_ID));
        assertEquals("alice", request.getSession().getAttribute(CurrentUserService.KEY_USERNAME));
        assertEquals(CurrentUserService.credentialTag(hash),
                request.getSession().getAttribute(CurrentUserService.KEY_CREDENTIAL_TAG),
                "登录应记下口令散列指纹，供后续请求复核");
    }

    @Test
    void fourFailureModesShareUnifiedMessage() {
        when(storeUserMapper.selectOne(any(LambdaQueryWrapper.class)))
                .thenReturn(null, user("CUSTOMER", "ENABLED"), user("CUSTOMER", "DISABLED"), user("ADMIN", "ENABLED"));

        for (String password : new String[]{"right-pass", "wrong-pass", "right-pass", "right-pass"}) {
            BusinessException e = assertThrows(BusinessException.class,
                    () -> authService.login("alice", password, request()));
            assertEquals(AuthService.UNIFIED_FAILURE, e.getMessage());
        }
    }

    @Test
    void missingOrOversizedCredentialsUseUnifiedMessageWithoutDbHit() {
        for (String[] pair : new String[][]{{null, "x"}, {"  ", "x"}, {"a".repeat(51), "x"}, {"alice", null}, {"alice", ""}}) {
            BusinessException e = assertThrows(BusinessException.class,
                    () -> authService.login(pair[0], pair[1], request()));
            assertEquals(AuthService.UNIFIED_FAILURE, e.getMessage());
        }
        Mockito.verifyNoInteractions(storeUserMapper);
    }

    @Test
    void usernameIsTrimmedButPasswordIsNot() {
        when(storeUserMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(user("CUSTOMER", "ENABLED"));
        assertEquals(7L, authService.login("  alice  ", "right-pass", request()).id());
        BusinessException e = assertThrows(BusinessException.class,
                () -> authService.login("alice", " right-pass ", request()));
        assertEquals(AuthService.UNIFIED_FAILURE, e.getMessage());
    }

    @Test
    void safeReturnPathOnlyAcceptsInAppRelativePaths() {
        assertEquals("/main", authService.safeReturnPath(null));
        assertEquals("/main", authService.safeReturnPath("  "));
        assertEquals("/orders", authService.safeReturnPath("/orders"));
        assertEquals("/category?keyword=a", authService.safeReturnPath("/category?keyword=a"));
        assertEquals("/main", authService.safeReturnPath("//evil.example"));
        assertEquals("/main", authService.safeReturnPath("https://evil.example"));
        assertEquals("/main", authService.safeReturnPath("/a\\b"));
        assertEquals("/main", authService.safeReturnPath("/a:b"));
        assertTrue(authService.safeReturnPath("/cart").startsWith("/"));
    }
}
