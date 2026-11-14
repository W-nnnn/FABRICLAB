package com.fabriclab.admin.t15;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** T15 页面链路：列表、详情、保存、重置、启停与管理员账号的 404。 */
@SpringBootTest
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "MYSQL_PASSWORD", matches = ".+")
class T15PageTest {

    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;

    private long id;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    @BeforeEach
    void setUp() {
        LocaleContextHolder.setLocale(Locale.SIMPLIFIED_CHINESE);
        clean();
        jdbc.update("INSERT INTO store_user (username, password_hash, display_name, role, status, email) "
                        + "VALUES ('t15web', ?, 'T15 网页', 'CUSTOMER', 'ENABLED', 'web@example.test')",
                encoder.encode("OldPass!2026"));
        id = jdbc.queryForObject("SELECT id FROM store_user WHERE username = 't15web'", Long.class);
        jdbc.update("INSERT INTO sales_order (order_no, user_id, status, total_amount, receiver_name, "
                        + "receiver_phone, receiver_address, payment_method, payment_no, paid_at) "
                        + "VALUES ('T15-WEB-1', ?, 'PAID', 168.00, '网页收货人', '13800000002', "
                        + "'上海市 T15 路 9 号', 'MOCK', 'MOCK-T15WEB', NOW())", id);
    }

    @AfterEach
    void tearDown() {
        LocaleContextHolder.resetLocaleContext();
        clean();
    }

    private void clean() {
        jdbc.update("DELETE FROM sales_order WHERE order_no LIKE 'T15-%'");
        jdbc.update("DELETE FROM store_user WHERE username LIKE 't15%'");
    }

    private record Session(MockHttpSession session, String csrf) {}

    private Session login() throws Exception {
        MvcResult page = mvc.perform(get("/admin/signon")).andReturn();
        String csrf = (String) page.getModelAndView().getModel().get("csrfToken");
        MockHttpSession s = (MockHttpSession) page.getRequest().getSession();
        MvcResult ok = mvc.perform(post("/admin/signon").session(s)
                .param("username", "admin").param("password", "Passw0rd!2026")
                .param("_csrf", csrf)).andReturn();
        assertEquals(302, ok.getResponse().getStatus());
        return new Session((MockHttpSession) ok.getRequest().getSession(), csrf);
    }

    private String html(String path, Session s) throws Exception {
        MvcResult r = mvc.perform(get(path).session(s.session())).andReturn();
        assertEquals(200, r.getResponse().getStatus());
        return r.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    @Test
    void listShowsCustomerRowsWithCountsAndEntry() throws Exception {
        Session s = login();
        String html = html("/admin/users?kw=t15web", s);
        assertTrue(html.contains("账号管理"));
        assertTrue(html.contains("t15web"));
        assertTrue(html.contains("T15 网页"));
        assertTrue(html.contains("/admin/users/" + id), "用户名应链接到账号详情");
        assertTrue(html.contains("已启用"));
        assertTrue(html.contains("重置口令"), "行菜单应提供重置入口");
    }

    @Test
    void listNeverShowsAdminAccount() throws Exception {
        Session s = login();
        long admin = jdbc.queryForObject("SELECT id FROM store_user WHERE role='ADMIN' LIMIT 1", Long.class);
        String html = html("/admin/users?size=50&kw=admin", s);
        assertEquals(0, countOf(html, "/admin/users/" + admin), "管理员账号不得出现在列表");
        assertTrue(html.contains("没有符合条件的账号"), "按管理员用户名搜索应无结果");
    }

    private int countOf(String haystack, String needle) {
        int n = 0;
        int i = haystack.indexOf(needle);
        while (i >= 0) {
            n++;
            i = haystack.indexOf(needle, i + needle.length());
        }
        return n;
    }

    @Test
    void detailShowsReadonlyIdentityAndOrderSnapshot() throws Exception {
        Session s = login();
        String html = html("/admin/users/" + id, s);
        assertTrue(html.contains("t15web"));
        assertTrue(html.contains("账号资料"));
        assertTrue(html.contains("用户名不可修改") || html.contains("创建后不可修改"));
        assertTrue(html.contains("readonly"), "用户名与角色应只读");
        assertTrue(html.contains("最近订单"));
        assertTrue(html.contains("T15-WEB-1"), "详情应列出最近订单，作为快照核对");
        assertTrue(html.contains("网页收货人") || html.contains("已支付"));
        assertTrue(html.contains("重置口令"));
        assertTrue(html.contains("停用账号"));
    }

    @Test
    void saveUpdatesProfileAndRedirectsBack() throws Exception {
        Session s = login();
        MvcResult r = mvc.perform(post("/admin/users/" + id).session(s.session())
                .param("_csrf", s.csrf())
                .param("username", "t15web").param("role", "CUSTOMER")
                .param("displayName", "T15 已改名")
                .param("email", "new@example.test").param("phone", "021-6688 0011")).andReturn();
        assertEquals(302, r.getResponse().getStatus());
        assertEquals("/admin/users/" + id, r.getResponse().getRedirectedUrl());
        assertEquals("T15 已改名", jdbc.queryForObject(
                "SELECT display_name FROM store_user WHERE id = ?", String.class, id));

        String after = html("/admin/users/" + id, s);
        assertTrue(after.contains("账号资料已保存"), "保存后应给一次性提示");
        assertTrue(after.contains("T15 已改名"));
    }

    @Test
    void invalidEmailRerendersWithErrorAndKeepsInput() throws Exception {
        Session s = login();
        MvcResult r = mvc.perform(post("/admin/users/" + id).session(s.session())
                .param("_csrf", s.csrf()).param("username", "t15web").param("role", "CUSTOMER")
                .param("displayName", "T15 输入有误").param("email", "not-an-email")).andReturn();
        assertEquals(200, r.getResponse().getStatus());
        String html = r.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertTrue(html.contains("邮箱格式不正确"));
        assertTrue(html.contains("T15 输入有误"), "错误回显保留已填内容");
        assertEquals("T15 网页", jdbc.queryForObject(
                "SELECT display_name FROM store_user WHERE id = ?", String.class, id));
    }

    @Test
    void resetPasswordThroughHttpReplacesHash() throws Exception {
        Session s = login();
        String before = jdbc.queryForObject(
                "SELECT password_hash FROM store_user WHERE id = ?", String.class, id);
        MvcResult r = mvc.perform(post("/admin/users/" + id + "/password")
                .session(s.session()).param("_csrf", s.csrf())).andReturn();
        assertEquals(302, r.getResponse().getStatus());
        String after = jdbc.queryForObject(
                "SELECT password_hash FROM store_user WHERE id = ?", String.class, id);
        assertNotEquals(before, after);
        assertTrue(encoder.matches("Passw0rd!2026", after));
        assertTrue(html("/admin/users/" + id, s).contains("口令已重置为统一初始口令"));
    }

    @Test
    void disableThenRepeatReportsCurrentState() throws Exception {
        Session s = login();
        mvc.perform(post("/admin/users/" + id + "/status").session(s.session())
                .param("_csrf", s.csrf()).param("target", "DISABLED")).andReturn();
        assertEquals("DISABLED", jdbc.queryForObject(
                "SELECT status FROM store_user WHERE id = ?", String.class, id));
        assertTrue(html("/admin/users/" + id, s).contains("账号已停用"), "首次停用应给提示");

        mvc.perform(post("/admin/users/" + id + "/status").session(s.session())
                .param("_csrf", s.csrf()).param("target", "DISABLED")).andReturn();
        String html = html("/admin/users/" + id, s);
        assertTrue(html.contains("该账号已是当前状态"), "重复停用应提示当前状态");
        assertTrue(html.contains("已停用"));
    }

    @Test
    void adminAccountPageIs404() throws Exception {
        Session s = login();
        long admin = jdbc.queryForObject("SELECT id FROM store_user WHERE role='ADMIN' LIMIT 1", Long.class);
        MvcResult r = mvc.perform(get("/admin/users/" + admin).session(s.session())).andReturn();
        assertEquals(404, r.getResponse().getStatus());
    }

    @Test
    void englishPageSwitchesCopy() throws Exception {
        Session s = login();
        String html = html("/admin/users?lang=en", s);
        assertTrue(html.contains("Accounts"));
        assertTrue(html.contains("Username"));
        assertTrue(html.contains("Enabled"));

        String detail = html("/admin/users/" + id + "?lang=en", s);
        assertTrue(detail.contains("Account"));
        assertTrue(detail.contains("Reset password"));
        assertTrue(detail.contains("Recent orders"));
    }

    @Test
    void sidebarShowsAccountsEntry() throws Exception {
        Session s = login();
        String html = html("/admin/users", s);
        assertTrue(html.contains("href=\"/admin/users\""), "侧栏应有账号管理入口");
        assertTrue(html.contains("顾客"));
    }
}
