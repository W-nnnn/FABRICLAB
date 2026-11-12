package com.fabriclab.admin.controller;

import com.fabriclab.admin.entity.StoreUser;
import com.fabriclab.admin.mapper.StoreUserMapper;
import com.fabriclab.admin.model.AdminUser;
import com.fabriclab.admin.security.CsrfService;
import com.fabriclab.admin.service.AdminSessionService;
import com.fabriclab.admin.service.BrandService;
import com.fabriclab.admin.service.ConsoleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * 控制台：未登录不得进入、管理员可进入；页面只呈现身份与模块范围，
 * 不展示任何未经真实统计的经营指标（NFR-11）。
 */
@WebMvcTest(ConsoleController.class)
@Import(ConsoleService.class)
class ConsoleControllerTest {

    @Autowired
    private MockMvc mockMvc;

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
    }

    private MockHttpSession adminSession() {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(AdminSessionService.KEY_ADMIN_ID, 4L);
        session.setAttribute(AdminSessionService.KEY_ADMIN_USERNAME, "admin");
        session.setAttribute(AdminSessionService.KEY_ADMIN_DISPLAY_NAME, "平台管理员");
        session.setAttribute(AdminSessionService.KEY_SIGNIN_AT, "2026-11-12 22:00:00");
        return session;
    }

    private StoreUser adminRow() {
        StoreUser user = new StoreUser();
        user.setId(4L);
        user.setUsername("admin");
        user.setRole("ADMIN");
        user.setStatus("ENABLED");
        return user;
    }

    @Test
    void rootRedirectsToConsole() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/console"));
    }

    @Test
    void anonymousCannotReachConsole() throws Exception {
        when(adminSessionService.from(any())).thenReturn(null);
        mockMvc.perform(get("/admin/console"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/signon?returnTo=%2Fadmin%2Fconsole"));
    }

    @Test
    void consoleShowsIdentityAndModuleScopeWithoutFabricatedMetrics() throws Exception {
        when(adminSessionService.from(any())).thenReturn(new AdminUser(4L, "admin", "平台管理员"));
        when(adminSessionService.signinAt(any())).thenReturn("2026-11-12 22:00:00");
        when(storeUserMapper.selectById(4L)).thenReturn(adminRow());

        mockMvc.perform(get("/admin/console").session(adminSession()))
                .andExpect(status().isOk())
                .andExpect(view().name("console"))
                .andExpect(content().string(containsString("平台管理员")))
                .andExpect(content().string(containsString("分类与商品")))
                .andExpect(content().string(containsString("订单管理")))
                .andExpect(content().string(containsString("2026-11-12 22:00:00")))
                .andExpect(content().string(containsString("ADMINSESSION")))
                // 控制台不得出现伪造统计
                .andExpect(content().string(not(containsString("销售额"))))
                .andExpect(content().string(not(containsString("营业额"))))
                .andExpect(content().string(not(containsString("转化率"))))
                .andExpect(content().string(not(containsString("访客"))));
    }
}
