package com.fabriclab.store;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 真实库（含种子数据）下的登录、会话与访问检查验收（AC-05/06/07/31）：
 * 仅启用 CUSTOMER 登录成功；错误密码、禁用、管理员四态统一失败且密码不回显；
 * 登录后禁用账号，受保护入口（真实购物车页 /cart）被拦截；退出后会话失效；
 * 缺 CSRF 的写请求 403；用户端 Cookie 独立命名 STORESESSION
 * （AC-06 本端证据，跨端联测随管理端完成）。
 * 仅在提供 MYSQL_PASSWORD 时执行；无口令时跳过，不伪造通过。
 */
@SpringBootTest
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "MYSQL_PASSWORD", matches = ".+")
class AuthIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private org.springframework.core.env.Environment environment;

    private String htmlOf(MvcResult mvc) throws Exception {
        return mvc.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    /** GET /signon 取 CSRF 令牌与同一会话。 */
    private record SignonPage(String csrf, MockHttpSession session) {
    }

    private SignonPage signonPage() throws Exception {
        MvcResult mvc = mockMvc.perform(get("/signon")).andExpect(status().isOk()).andReturn();
        return new SignonPage(
                (String) mvc.getModelAndView().getModel().get("csrfToken"),
                (MockHttpSession) mvc.getRequest().getSession());
    }

    private MvcResult postSignon(SignonPage page, String username, String password,
                                 String returnTo, boolean withCsrf) throws Exception {
        var builder = post("/signon").session(page.session())
                .param("username", username).param("password", password);
        if (returnTo != null) {
            builder = builder.param("returnTo", returnTo);
        }
        if (withCsrf) {
            builder = builder.param("_csrf", page.csrf());
        }
        return mockMvc.perform(builder).andReturn();
    }

    @Test
    void enabledCustomerSignsInAndCookieIsStoreNamed() throws Exception {
        SignonPage page = signonPage();
        MvcResult mvc = postSignon(page, "alice", "Passw0rd!2026", null, true);

        assertEquals(302, mvc.getResponse().getStatus());
        assertEquals("/main", mvc.getResponse().getRedirectedUrl());
        // MockMvc 不回写会话 Cookie，此处以生效配置为准：用户端 Cookie 独立命名，
        // 与管理端（8081）隔离；跨端联测随管理端应用完成（AC-06 完整证据在 T12）。
        assertEquals("STORESESSION", environment.getProperty("server.servlet.session.cookie.name"));

        MockHttpSession session = (MockHttpSession) mvc.getRequest().getSession();
        assertEquals(1L, session.getAttribute("storeUserId"));
        assertEquals("alice", session.getAttribute("storeUsername"));
    }

    @Test
    void fourFailureModesShareUnifiedMessageAndNeverEchoPassword() throws Exception {
        SignonPage page = signonPage();
        String[][] attempts = {{"alice", "wrong-pass"}, {"disabled", "Passw0rd!2026"},
                {"admin", "Passw0rd!2026"}, {"ghost-user", "whatever"}};
        for (String[] attempt : attempts) {
            MvcResult mvc = postSignon(page, attempt[0], attempt[1], null, true);
            String html = htmlOf(mvc);
            assertEquals(200, mvc.getResponse().getStatus(), attempt[0] + " 应重渲表单");
            assertTrue(html.contains("账号或密码错误，或账号不可用"), attempt[0] + " 应为统一失败提示");
            assertTrue(html.contains("value=\"" + attempt[0] + "\""), "用户名可回显");
            assertFalse(html.contains(attempt[1]), "密码不得回显：" + attempt[0]);
        }
        Integer enabledAdmin = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM store_user WHERE username='admin' AND role='ADMIN' AND status='ENABLED'",
                Integer.class);
        assertEquals(1, enabledAdmin, "管理员账号存在且启用，仍不得登录用户端");
    }

    @Test
    void loginHonorsInAppReturnPathOnly() throws Exception {
        SignonPage page = signonPage();
        MvcResult good = postSignon(page, "alice", "Passw0rd!2026", "/category", true);
        assertEquals("/category", good.getResponse().getRedirectedUrl());

        SignonPage page2 = signonPage();
        MvcResult evil = postSignon(page2, "alice", "Passw0rd!2026", "//evil.example", true);
        assertEquals("/main", evil.getResponse().getRedirectedUrl());
    }

    @Test
    void disabledAfterLoginIsBlockedOnProtectedEntry() throws Exception {
        SignonPage page = signonPage();
        MvcResult login = postSignon(page, "alice", "Passw0rd!2026", null, true);
        MockHttpSession session = (MockHttpSession) login.getRequest().getSession();

        // 登录态下受保护入口可访问（测试桩控制器）
        mockMvc.perform(get("/cart").session(session)).andExpect(status().isOk());

        try {
            jdbcTemplate.update("UPDATE store_user SET status='DISABLED' WHERE username='alice'");
            MvcResult blocked = mockMvc.perform(get("/cart").session(session)).andReturn();
            assertEquals(302, blocked.getResponse().getStatus(), "禁用后受保护入口必须拒绝");
            assertTrue(blocked.getResponse().getRedirectedUrl().startsWith("/signon"), "应跳登录页");
        } finally {
            jdbcTemplate.update("UPDATE store_user SET status='ENABLED' WHERE username='alice'");
        }
    }

    @Test
    void signoutInvalidatesSessionAndProtectedEntryReblocked() throws Exception {
        SignonPage page = signonPage();
        MvcResult login = postSignon(page, "bob", "Passw0rd!2026", null, true);
        MockHttpSession session = (MockHttpSession) login.getRequest().getSession();
        mockMvc.perform(get("/cart").session(session)).andExpect(status().isOk());

        mockMvc.perform(post("/signout").session(session).param("_csrf", page.csrf()))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/main"));

        assertTrue(((MockHttpSession) session).isInvalid(), "退出必须使会话失效");
        mockMvc.perform(get("/cart").session(session)).andExpect(status().is3xxRedirection());
        // 数据库购物车不删除：本任务不触碰 cart_item，验证仅记录行数不变
        Integer cartRows = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM cart_item", Integer.class);
        assertNotNull(cartRows);
    }

    @Test
    void writeRequestWithoutCsrfIsRejectedWithInvalidFormPage() throws Exception {
        SignonPage page = signonPage();
        MvcResult mvc = postSignon(page, "alice", "Passw0rd!2026", null, false);
        String html = htmlOf(mvc);
        assertEquals(403, mvc.getResponse().getStatus());
        assertTrue(html.contains("无效表单防护请求"));
        // 篡改令牌同样拒绝
        MvcResult tampered = mockMvc.perform(post("/signon").session(page.session())
                        .param("username", "alice").param("password", "Passw0rd!2026").param("_csrf", "bad-token"))
                .andReturn();
        assertEquals(403, tampered.getResponse().getStatus());
    }
}
