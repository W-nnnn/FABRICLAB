package com.fabriclab.admin;

import jakarta.servlet.http.Cookie;
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
 * 真实库（含种子数据）下的后台登录与访问检查验收（FR-A-01、AC-06）：
 * 仅启用的 ADMIN 登录成功；顾客账号、错误口令、停用账号、不存在账号四类共用统一提示且口令不回显；
 * 登录后停用则后台入口被拦截；退出后会话失效；缺 CSRF 的写请求 403；
 * 后台 Cookie 独立命名 ADMINSESSION 且端口 8081，携带用户端 STORESESSION 不产生任何权限（AC-06 对侧证据：
 * 管理员登录用户端被拒由用户端 AuthIntegrationTest 覆盖）。
 * 仅在提供 MYSQL_PASSWORD 时执行；无口令时跳过，不伪造通过。
 */
@SpringBootTest
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "MYSQL_PASSWORD", matches = ".+")
class AdminAccessIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String htmlOf(MvcResult mvc) throws Exception {
        return mvc.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    /** GET /admin/signon 取 CSRF 令牌与同一会话。 */
    private record SignonPage(String csrf, MockHttpSession session) {
    }

    private SignonPage signonPage() throws Exception {
        MvcResult mvc = mockMvc.perform(get("/admin/signon")).andExpect(status().isOk()).andReturn();
        return new SignonPage(
                (String) mvc.getModelAndView().getModel().get("csrfToken"),
                (MockHttpSession) mvc.getRequest().getSession());
    }

    private MvcResult postSignon(SignonPage page, String username, String password, boolean withCsrf)
            throws Exception {
        var builder = post("/admin/signon").session(page.session())
                .param("username", username).param("password", password);
        if (withCsrf) {
            builder = builder.param("_csrf", page.csrf());
        }
        return mockMvc.perform(builder).andReturn();
    }

    private MockHttpSession loginAsAdmin() throws Exception {
        SignonPage page = signonPage();
        MvcResult mvc = postSignon(page, "admin", "Passw0rd!2026", true);
        assertEquals(302, mvc.getResponse().getStatus());
        assertEquals("/admin/console", mvc.getResponse().getRedirectedUrl());
        return (MockHttpSession) mvc.getRequest().getSession();
    }

    @Test
    void adminSessionCookieAndPortAreDeclaredIndependentFromStore() throws Exception {
        // 直接读工程自带配置：宿主环境注入的 SERVER__PORT 会覆盖运行时端口，不能作为配置文件证据
        var yml = new org.springframework.boot.env.YamlPropertySourceLoader()
                .load("application", new org.springframework.core.io.ClassPathResource("application.yml"))
                .get(0);
        assertEquals("ADMINSESSION", yml.getProperty("server.servlet.session.cookie.name"));
        assertEquals(8081, yml.getProperty("server.port"));
        assertFalse("STORESESSION".equals(yml.getProperty("server.servlet.session.cookie.name")),
                "后台与用户端 Cookie 名必须不同（AC-06）");
    }

    @Test
    void enabledAdminSignsInWithMinimalIdentity() throws Exception {
        SignonPage page = signonPage();
        MvcResult mvc = postSignon(page, "admin", "Passw0rd!2026", true);

        assertEquals(302, mvc.getResponse().getStatus());
        assertEquals("/admin/console", mvc.getResponse().getRedirectedUrl());
        MockHttpSession session = (MockHttpSession) mvc.getRequest().getSession();
        assertEquals(4L, session.getAttribute("adminUserId"));
        assertEquals("admin", session.getAttribute("adminUsername"));
        assertNotNull(session.getAttribute("adminSigninAt"));
        // 会话不保存口令散列
        assertEquals(null, session.getAttribute("passwordHash"), "会话不得保存口令散列");
    }

    @Test
    void consoleIsReachableAfterSignInAndShowsNoFabricatedMetrics() throws Exception {
        MockHttpSession session = loginAsAdmin();
        MvcResult mvc = mockMvc.perform(get("/admin/console").session(session))
                .andExpect(status().isOk()).andReturn();
        String html = htmlOf(mvc);
        assertTrue(html.contains("ADMINSESSION") && html.contains("STORESESSION"), "应呈现会话隔离说明");
        assertTrue(html.contains("分类与商品"), "应呈现后台模块范围");
        assertFalse(html.contains("销售额"), "控制台不得显示伪造统计");
        assertFalse(html.contains("转化率"), "控制台不得显示伪造统计");
    }

    @Test
    void customerAndWrongCredentialsShareUnifiedMessage() throws Exception {
        SignonPage page = signonPage();
        String[][] attempts = {{"alice", "Passw0rd!2026"}, {"admin", "wrong-pass"}, {"ghost-admin", "whatever"}};
        for (String[] attempt : attempts) {
            MvcResult mvc = postSignon(page, attempt[0], attempt[1], true);
            String html = htmlOf(mvc);
            assertEquals(200, mvc.getResponse().getStatus(), attempt[0] + " 应重渲登录页");
            assertTrue(html.contains("账号或口令不正确，或无后台权限"), attempt[0] + " 应为统一失败提示");
            assertTrue(html.contains("value=\"" + attempt[0] + "\""), "账号可回显");
            assertFalse(html.contains(attempt[1]), "口令不得回显：" + attempt[0]);
        }
        Integer customers = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM store_user WHERE username='alice' AND role='CUSTOMER' AND status='ENABLED'",
                Integer.class);
        assertEquals(1, customers, "顾客账号存在且启用，仍不得登录后台");
    }

    @Test
    void disabledAdminIsRefusedAtSignon() throws Exception {
        try {
            jdbcTemplate.update("UPDATE store_user SET status='DISABLED' WHERE username='admin'");
            SignonPage page = signonPage();
            MvcResult mvc = postSignon(page, "admin", "Passw0rd!2026", true);
            assertEquals(200, mvc.getResponse().getStatus());
            assertTrue(htmlOf(mvc).contains("账号或口令不正确，或无后台权限"));
        } finally {
            jdbcTemplate.update("UPDATE store_user SET status='ENABLED' WHERE username='admin'");
        }
    }

    @Test
    void disabledAfterLoginIsBlockedOnConsole() throws Exception {
        MockHttpSession session = loginAsAdmin();
        mockMvc.perform(get("/admin/console").session(session)).andExpect(status().isOk());
        try {
            jdbcTemplate.update("UPDATE store_user SET status='DISABLED' WHERE username='admin'");
            MvcResult blocked = mockMvc.perform(get("/admin/console").session(session)).andReturn();
            assertEquals(302, blocked.getResponse().getStatus(), "停用后后台入口必须拒绝");
            assertTrue(blocked.getResponse().getRedirectedUrl().startsWith("/admin/signon"));
            assertTrue(session.isInvalid(), "被拒绝时后台会话必须失效");
        } finally {
            jdbcTemplate.update("UPDATE store_user SET status='ENABLED' WHERE username='admin'");
        }
    }

    @Test
    void roleChangedAfterLoginIsBlockedOnConsole() throws Exception {
        MockHttpSession session = loginAsAdmin();
        try {
            jdbcTemplate.update("UPDATE store_user SET role='CUSTOMER' WHERE username='admin'");
            MvcResult blocked = mockMvc.perform(get("/admin/console").session(session)).andReturn();
            assertEquals(302, blocked.getResponse().getStatus(), "角色变化后必须拒绝");
        } finally {
            jdbcTemplate.update("UPDATE store_user SET role='ADMIN' WHERE username='admin'");
        }
    }

    @Test
    void signoutInvalidatesAdminSessionOnly() throws Exception {
        SignonPage page = signonPage();
        MvcResult login = postSignon(page, "admin", "Passw0rd!2026", true);
        MockHttpSession session = (MockHttpSession) login.getRequest().getSession();
        mockMvc.perform(get("/admin/console").session(session)).andExpect(status().isOk());

        mockMvc.perform(post("/admin/signout").session(session).param("_csrf", page.csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/signon"));

        assertTrue(session.isInvalid(), "退出必须使后台会话失效");
        mockMvc.perform(get("/admin/console").session(session)).andExpect(status().is3xxRedirection());
    }

    @Test
    void writeRequestsWithoutCsrfAreRejected() throws Exception {
        SignonPage page = signonPage();
        MvcResult mvc = postSignon(page, "admin", "Passw0rd!2026", false);
        assertEquals(403, mvc.getResponse().getStatus());
        assertTrue(htmlOf(mvc).contains("无效表单防护请求"));

        MvcResult tampered = mockMvc.perform(post("/admin/signon").session(page.session())
                        .param("username", "admin").param("password", "Passw0rd!2026")
                        .param("_csrf", "bad-token"))
                .andReturn();
        assertEquals(403, tampered.getResponse().getStatus());
    }

    @Test
    void foreignStoreSessionCookieGrantsNoAdminAccess() throws Exception {
        // AC-06：携带用户端 Cookie 名访问后台，不产生任何身份
        mockMvc.perform(get("/admin/console").cookie(new Cookie("STORESESSION", "any-store-session-id")))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/signon?returnTo=%2Fadmin%2Fconsole"));
    }
}
