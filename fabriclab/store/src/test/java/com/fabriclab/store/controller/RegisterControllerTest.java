package com.fabriclab.store.controller;

import com.fabriclab.store.entity.StoreUser;
import com.fabriclab.store.exception.BusinessException;
import com.fabriclab.store.service.BrandService;
import com.fabriclab.store.service.CaptchaService;
import com.fabriclab.store.service.RegistrationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import com.fabriclab.store.mapper.StoreUserMapper;
import com.fabriclab.store.security.CsrfService;
import com.fabriclab.store.service.CurrentUserService;

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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/** 注册页：渲染、成功、失败重渲与验证码刷新、密码不回显。 */
@WebMvcTest(RegisterController.class)
class RegisterControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BrandService brandService;

    @MockitoBean
    private RegistrationService registrationService;

    @MockitoBean
    private CaptchaService captchaService;


    @MockitoBean
    private CsrfService csrfService;

    @MockitoBean
    private CurrentUserService currentUserService;

    @MockitoBean
    private StoreUserMapper storeUserMapper;

    @BeforeEach
    void stubCommon() {
        lenient().when(csrfService.token(any())).thenReturn("token-x");
        lenient().when(currentUserService.from(any())).thenReturn(null);
        lenient().when(brandService.brand()).thenReturn("FABRICLAB");
        lenient().when(brandService.tagline()).thenReturn("未染");
        lenient().when(captchaService.generate())
                .thenReturn(new CaptchaService.Captcha("token-1", "AB2C"));
    }

    private StoreUser user(String username) {
        StoreUser user = new StoreUser();
        user.setId(99L);
        user.setUsername(username);
        user.setRole("CUSTOMER");
        user.setStatus("ENABLED");
        return user;
    }

    @Test
    void registerPageShowsCaptchaAndForm() throws Exception {
        mockMvc.perform(get("/register"))
                .andExpect(status().isOk())
                .andExpect(view().name("register"))
                .andExpect(content().string(containsString("会员注册")))
                .andExpect(content().string(containsString("AB2C")))
                .andExpect(content().string(containsString("token-1")))
                .andExpect(content().string(containsString("创建账号")))
                .andExpect(content().string(not(containsString("找回密码"))))
                .andExpect(content().string(not(containsString("第三方登录"))));
    }

    @Test
    void successfulRegistrationShowsUsernameWithoutPassword() throws Exception {
        when(registrationService.register(eq("newuser"), any(), any(), any(), any()))
                .thenReturn(user("newuser"));

        mockMvc.perform(post("/register")
                        .param("username", "newuser")
                        .param("password", "abcd1234")
                        .param("confirm", "abcd1234")
                        .param("captchaToken", "token-1")
                        .param("captchaInput", "AB2C"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("注册成功")))
                .andExpect(content().string(containsString("newuser")))
                .andExpect(content().string(not(containsString("abcd1234"))));
    }

    @Test
    void failedRegistrationRerendersFormWithFreshCaptchaAndUsernameEcho() throws Exception {
        when(registrationService.register(eq("alice"), any(), any(), any(), any()))
                .thenThrow(new BusinessException("用户名已被使用"));

        mockMvc.perform(post("/register")
                        .param("username", "alice")
                        .param("password", "abcd1234")
                        .param("confirm", "abcd1234")
                        .param("captchaToken", "token-1")
                        .param("captchaInput", "AB2C"))
                .andExpect(status().isOk())
                .andExpect(view().name("register"))
                .andExpect(content().string(containsString("用户名已被使用")))
                .andExpect(content().string(containsString("value=\"alice\"")))
                .andExpect(content().string(containsString("token-1")))
                .andExpect(content().string(not(containsString("abcd1234"))));
    }

    @Test
    void blankUsernameRejectedInline() throws Exception {
        when(registrationService.register(any(), any(), any(), any(), any()))
                .thenThrow(new BusinessException("用户名不能为空"));

        mockMvc.perform(post("/register"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("用户名不能为空")));
    }
}
