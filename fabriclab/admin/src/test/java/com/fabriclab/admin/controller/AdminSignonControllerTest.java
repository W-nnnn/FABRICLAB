package com.fabriclab.admin.controller;

import com.fabriclab.admin.entity.StoreUser;
import com.fabriclab.admin.exception.BusinessException;
import com.fabriclab.admin.mapper.StoreUserMapper;
import com.fabriclab.admin.model.AdminUser;
import com.fabriclab.admin.security.CsrfService;
import com.fabriclab.admin.service.AdminAuthService;
import com.fabriclab.admin.service.AdminSessionService;
import com.fabriclab.admin.service.BrandService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.mock.web.MockHttpSession;
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

/** 后台登录页：渲染、已登录跳转、返回页白名单、失败回显与口令不回显。 */
@WebMvcTest(AdminSignonController.class)
class AdminSignonControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AdminAuthService adminAuthService;

    @MockitoBean
    private AdminSessionService adminSessionService;

    @MockitoBean
    private CsrfService csrfService;

    @MockitoBean
    private StoreUserMapper storeUserMapper;

    @MockitoBean
    private BrandService brandService;

    @BeforeEach
    void stubCommon() {
        lenient().when(brandService.brand()).thenReturn("FABRICLAB");
        lenient().when(brandService.tagline()).thenReturn("未染");
        lenient().when(brandService.consoleName()).thenReturn("管理台");
        lenient().when(csrfService.token(any())).thenReturn("token-x");
        lenient().when(adminSessionService.from(any())).thenReturn(null);
    }

    @Test
    void signonPageRendersFormWithoutForbiddenEntries() throws Exception {
        when(adminAuthService.safeReturnPath(null)).thenReturn("/admin/console");

        mockMvc.perform(get("/admin/signon"))
                .andExpect(status().isOk())
                .andExpect(view().name("signon"))
                .andExpect(content().string(containsString("管理员登录")))
                .andExpect(content().string(containsString("token-x")))
                .andExpect(content().string(containsString("/admin/signon")))
                .andExpect(content().string(not(containsString("找回口令"))))
                .andExpect(content().string(not(containsString("第三方登录"))))
                .andExpect(content().string(not(containsString("记住登录"))))
                .andExpect(content().string(not(containsString("注册"))));
    }

    @Test
    void loggedInAdminIsRedirectedToConsole() throws Exception {
        when(adminSessionService.from(any())).thenReturn(new AdminUser(4L, "admin", "平台管理员"));
        mockMvc.perform(get("/admin/signon"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/console"));
    }

    @Test
    void successRedirectsToValidatedReturnPath() throws Exception {
        when(adminAuthService.login(eq("admin"), any(), any()))
                .thenReturn(new AdminUser(4L, "admin", "平台管理员"));
        when(adminAuthService.safeReturnPath(null)).thenReturn("/admin/console");
        when(adminAuthService.safeReturnPath("/admin/products")).thenReturn("/admin/products");
        when(adminAuthService.safeReturnPath("//evil.example")).thenReturn("/admin/console");

        mockMvc.perform(post("/admin/signon").param("username", "admin").param("password", "pw"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/admin/console"));
        mockMvc.perform(post("/admin/signon").param("username", "admin").param("password", "pw")
                        .param("returnTo", "/admin/products"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/admin/products"));
        mockMvc.perform(post("/admin/signon").param("username", "admin").param("password", "pw")
                        .param("returnTo", "//evil.example"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/admin/console"));
    }

    @Test
    void failureShowsUnifiedMessageWithAccountButNoPassword() throws Exception {
        when(adminAuthService.login(eq("alice"), any(), any()))
                .thenThrow(new BusinessException(AdminAuthService.UNIFIED_FAILURE));
        when(adminAuthService.safeReturnPath(null)).thenReturn("/admin/console");

        mockMvc.perform(post("/admin/signon").param("username", "alice").param("password", "secret-pw"))
                .andExpect(status().isOk())
                .andExpect(view().name("signon"))
                .andExpect(content().string(containsString("账号或口令不正确，或无后台权限")))
                .andExpect(content().string(containsString("value=\"alice\"")))
                .andExpect(content().string(not(containsString("secret-pw"))));
    }

    @Test
    void signoutWithoutSessionIsFirstStoppedByAccessCheck() throws Exception {
        // 退出是 /admin/** 下的写入口：未登录时先被访问检查拦回登录页，不进入控制器
        mockMvc.perform(post("/admin/signout"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/signon?returnTo=%2Fadmin%2Fsignout"));
    }

    @Test
    void signoutInvalidatesSessionForLoggedInAdmin() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(AdminSessionService.KEY_ADMIN_ID, 4L);
        session.setAttribute(AdminSessionService.KEY_ADMIN_USERNAME, "admin");
        session.setAttribute(AdminSessionService.KEY_ADMIN_DISPLAY_NAME, "平台管理员");
        when(adminSessionService.from(any())).thenReturn(new AdminUser(4L, "admin", "平台管理员"));

        StoreUser row = new StoreUser();
        row.setId(4L);
        row.setUsername("admin");
        row.setRole("ADMIN");
        row.setStatus("ENABLED");
        when(storeUserMapper.selectById(4L)).thenReturn(row);

        mockMvc.perform(post("/admin/signout").session(session))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/signon"));
        org.junit.jupiter.api.Assertions.assertTrue(session.isInvalid(), "退出必须使后台会话失效");
    }
}
