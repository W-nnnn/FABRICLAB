package com.fabriclab.store.controller;

import com.fabriclab.store.mapper.StoreUserMapper;
import com.fabriclab.store.model.CurrentUser;
import com.fabriclab.store.service.AuthService;
import com.fabriclab.store.service.BrandService;
import com.fabriclab.store.service.CurrentUserService;
import com.fabriclab.store.security.CsrfService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/** 登录页：渲染、登录态跳转、成功重定向白名单、失败回显与密码不回显。 */
@WebMvcTest(SignonController.class)
class SignonControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private CurrentUserService currentUserService;

    @MockitoBean
    private BrandService brandService;

    @MockitoBean
    private CsrfService csrfService;

    @MockitoBean
    private StoreUserMapper storeUserMapper;

    @BeforeEach
    void stubCommon() {
        lenient().when(brandService.brand()).thenReturn("FABRICLAB");
        lenient().when(brandService.tagline()).thenReturn("未染");
        lenient().when(csrfService.token(any())).thenReturn("token-x");
        lenient().when(currentUserService.from(any())).thenReturn(null);
    }

    @Test
    void signonPageRendersFormWithoutForbiddenEntries() throws Exception {
        mockMvc.perform(get("/signon"))
                .andExpect(status().isOk())
                .andExpect(view().name("signon"))
                .andExpect(content().string(containsString("账号登录")))
                .andExpect(content().string(containsString("token-x")))
                .andExpect(content().string(not(containsString("找回密码"))))
                .andExpect(content().string(not(containsString("第三方登录"))))
                .andExpect(content().string(not(containsString("记住登录"))))
                .andExpect(content().string(not(containsString("注册"))));
    }

    @Test
    void loggedInUserIsRedirectedHome() throws Exception {
        when(currentUserService.from(any())).thenReturn(new CurrentUser(7L, "alice", "林晓"));
        mockMvc.perform(get("/signon")).andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/main"));
    }

    @Test
    void successRedirectsToValidatedReturnPath() throws Exception {
        when(authService.login(eq("alice"), any(), any())).thenReturn(new CurrentUser(7L, "alice", "林晓"));
        when(authService.safeReturnPath(null)).thenReturn("/main");
        when(authService.safeReturnPath("/orders")).thenReturn("/orders");
        when(authService.safeReturnPath("//evil.example")).thenReturn("/main");

        mockMvc.perform(post("/signon").param("username", "alice").param("password", "pw"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/main"));
        mockMvc.perform(post("/signon").param("username", "alice").param("password", "pw")
                        .param("returnTo", "/orders"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/orders"));
        mockMvc.perform(post("/signon").param("username", "alice").param("password", "pw")
                        .param("returnTo", "//evil.example"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/main"));
    }

    @Test
    void failureShowsUnifiedMessageWithUsernameButNoPassword() throws Exception {
        when(authService.login(eq("alice"), any(), any()))
                .thenThrow(new com.fabriclab.store.exception.BusinessException(AuthService.UNIFIED_FAILURE));
        when(authService.safeReturnPath(null)).thenReturn("/main");

        mockMvc.perform(post("/signon").param("username", "alice").param("password", "secret-pw"))
                .andExpect(status().isOk())
                .andExpect(view().name("signon"))
                .andExpect(content().string(containsString("账号或密码错误，或账号不可用")))
                .andExpect(content().string(containsString("value=\"alice\"")))
                .andExpect(content().string(not(containsString("secret-pw"))));
    }
}
