package com.fabriclab.store;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * 客服中心（用户端）真实库验收：常见问题匿名可读、我的咨询需登录；
 * 新建咨询的字段校验与关联订单本人归属；列表筛选与跨账号隔离；
 * 追问 / 关闭 / 重开的状态迁移与幂等；CSRF 与越权 404。
 * 仅在提供 MYSQL_PASSWORD 时执行；无口令时跳过，不伪造通过。
 */
@SpringBootTest
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "MYSQL_PASSWORD", matches = ".+")
class ServiceIntegrationTest {

    private static final String PREFIX = "T18-";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JdbcTemplate jdbc;

    private record SignIn(String csrf, MockHttpSession session) {}

    @BeforeEach
    void prepare() {
        clean();
        jdbc.update("UPDATE store_user SET status='ENABLED' WHERE username IN ('alice','bob')");
    }

    @AfterEach
    void tearDown() {
        clean();
    }

    private void clean() {
        jdbc.update("DELETE FROM sales_order_item WHERE order_id IN "
                + "(SELECT id FROM sales_order WHERE order_no LIKE 'T18-ORDER-%')");
        jdbc.update("DELETE FROM sales_order WHERE order_no LIKE 'T18-ORDER-%'");
        jdbc.update("DELETE m FROM service_message m JOIN service_thread t ON m.thread_id=t.id "
                + "WHERE t.subject LIKE 'T18-%'");
        jdbc.update("DELETE FROM service_thread WHERE subject LIKE 'T18-%'");
    }

    /** 关联订单自建 fixture，不依赖演示订单是否被其他测试清理。 */
    private void insertOrder(String orderNo, Long userId) {
        jdbc.update("INSERT INTO sales_order "
                        + "(order_no,user_id,status,total_amount,receiver_name,receiver_phone,receiver_address) "
                        + "VALUES (?,?,'PENDING_PAYMENT',199.00,'咨询验收收货人','13800000000','上海市静安区测试路 1 号')",
                orderNo, userId);
    }

    private SignIn signIn(String username) throws Exception {
        MvcResult page = mockMvc.perform(get("/signon")).andReturn();
        String csrf = (String) page.getModelAndView().getModel().get("csrfToken");
        MockHttpSession session = (MockHttpSession) page.getRequest().getSession();
        MvcResult login = mockMvc.perform(post("/signon").session(session)
                .param("username", username).param("password", "Passw0rd!2026")
                .param("_csrf", csrf)).andReturn();
        assertEquals(302, login.getResponse().getStatus(), username + " 应登录成功");
        return new SignIn(csrf, (MockHttpSession) login.getRequest().getSession());
    }

    private MvcResult create(SignIn who, String subject, String body, String orderNo, String sku) throws Exception {
        var builder = post("/service/threads").session(who.session())
                .param("_csrf", who.csrf())
                .param("category", "SIZING")
                .param("subject", subject)
                .param("body", body);
        if (orderNo != null) builder = builder.param("orderNo", orderNo);
        if (sku != null) builder = builder.param("sku", sku);
        return mockMvc.perform(builder).andReturn();
    }

    private long threadIdOf(String subject) {
        return jdbc.queryForObject("SELECT id FROM service_thread WHERE subject=?", Long.class, subject);
    }

    private String html(MvcResult result) throws Exception {
        return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    @Test
    void faqIsPublicButThreadsRequireSignon() throws Exception {
        assertEquals(200, mockMvc.perform(get("/service")).andReturn().getResponse().getStatus());
        MvcResult blocked = mockMvc.perform(get("/service/threads")).andReturn();
        assertEquals(302, blocked.getResponse().getStatus());
        assertTrue(blocked.getResponse().getRedirectedUrl().startsWith("/signon?returnTo="));
        MvcResult newForm = mockMvc.perform(get("/service/threads/new")).andReturn();
        assertEquals(302, newForm.getResponse().getStatus());
    }

    @Test
    void createWritesThreadAndFirstMessage() throws Exception {
        SignIn alice = signIn("alice");
        MvcResult created = create(alice, PREFIX + "尺码咨询", "肩宽 44，选 M 还是 L？", null, "FL-CM-01");
        assertEquals(302, created.getResponse().getStatus());
        long id = threadIdOf(PREFIX + "尺码咨询");
        assertTrue(created.getResponse().getRedirectedUrl().endsWith("/service/threads/" + id));

        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM service_thread WHERE id=? AND status='OPEN' AND related_sku='FL-CM-01'",
                Integer.class, id));
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM service_message WHERE thread_id=? AND sender='CUSTOMER'",
                Integer.class, id));

        assertEquals("咨询已提交，客服回复后会显示在对话里", created.getFlashMap().get("notice"));

        MvcResult detail = mockMvc.perform(get("/service/threads/" + id).session(alice.session())).andReturn();
        assertEquals(200, detail.getResponse().getStatus());
        String page = html(detail);
        assertTrue(page.contains("肩宽 44，选 M 还是 L？"));
        assertTrue(page.contains("FL-CM-01"));
    }

    @Test
    void invalidFormKeepsInputAndWritesNothing() throws Exception {
        SignIn alice = signIn("alice");
        MvcResult bad = create(alice, "   ", "正文内容", null, null);
        assertEquals(200, bad.getResponse().getStatus());
        assertEquals("service-form", bad.getModelAndView().getViewName());
        assertEquals("subject", bad.getModelAndView().getModel().get("errorField"));
        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM service_thread WHERE subject LIKE 'T18-%'", Integer.class));
    }

    @Test
    void relatedOrderMustBelongToCurrentUser() throws Exception {
        Long aliceId = jdbc.queryForObject("SELECT id FROM store_user WHERE username='alice'", Long.class);
        Long bobId = jdbc.queryForObject("SELECT id FROM store_user WHERE username='bob'", Long.class);
        String aliceOrder = "T18-ORDER-ALICE";
        String bobOrder = "T18-ORDER-BOB";
        insertOrder(aliceOrder, aliceId);
        insertOrder(bobOrder, bobId);

        SignIn bob = signIn("bob");
        MvcResult denied = create(bob, PREFIX + "越权关联", "正文", aliceOrder, null);
        assertEquals(200, denied.getResponse().getStatus());
        assertEquals("orderNo", denied.getModelAndView().getModel().get("errorField"));

        MvcResult ok = create(bob, PREFIX + "本人关联", "正文", bobOrder, null);
        assertEquals(302, ok.getResponse().getStatus());
    }

    @Test
    void listFiltersAndOtherCustomersCannotRead() throws Exception {
        SignIn alice = signIn("alice");
        SignIn bob = signIn("bob");
        create(alice, PREFIX + "待回复咨询", "第一条", null, null);
        long second = threadIdOf(PREFIX + "待回复咨询");
        create(alice, PREFIX + "准备关闭", "第二条", null, null);
        long closing = threadIdOf(PREFIX + "准备关闭");
        mockMvc.perform(post("/service/threads/" + closing + "/close").session(alice.session())
                .param("_csrf", alice.csrf())).andReturn();

        MvcResult all = mockMvc.perform(get("/service/threads").session(alice.session())).andReturn();
        assertTrue(html(all).contains(PREFIX + "待回复咨询"));
        assertTrue(html(all).contains(PREFIX + "准备关闭"));
        MvcResult open = mockMvc.perform(get("/service/threads?status=OPEN").session(alice.session())).andReturn();
        assertTrue(html(open).contains(PREFIX + "待回复咨询"));
        assertFalse(html(open).contains(PREFIX + "准备关闭"));

        MvcResult bobsList = mockMvc.perform(get("/service/threads").session(bob.session())).andReturn();
        assertFalse(html(bobsList).contains(PREFIX + "待回复咨询"));
        MvcResult stolen = mockMvc.perform(get("/service/threads/" + second).session(bob.session())).andReturn();
        assertEquals(404, stolen.getResponse().getStatus());
        MvcResult illegalStatus = mockMvc.perform(get("/service/threads?status=REFUND")
                .session(alice.session())).andReturn();
        assertEquals(400, illegalStatus.getResponse().getStatus());
    }

    @Test
    void appendCloseAndReopenFollowStateMachine() throws Exception {
        SignIn alice = signIn("alice");
        create(alice, PREFIX + "状态迁移", "第一条", null, null);
        long id = threadIdOf(PREFIX + "状态迁移");

        // 客服回复后会员追问：状态回到待回复，消息按序追加
        jdbc.update("INSERT INTO service_message (thread_id, sender, author_id, author_name, body) "
                + "VALUES (?, 'ADMIN', 4, '平台管理员', '已核对版型')", id);
        jdbc.update("UPDATE service_thread SET status='REPLIED', first_replied_at=NOW() WHERE id=?", id);
        MvcResult followUp = mockMvc.perform(post("/service/threads/" + id + "/reply").session(alice.session())
                .param("_csrf", alice.csrf()).param("body", "还有一点想确认")).andReturn();
        assertEquals(302, followUp.getResponse().getStatus());
        assertEquals("OPEN", jdbc.queryForObject("SELECT status FROM service_thread WHERE id=?", String.class, id));
        assertEquals(3, jdbc.queryForObject("SELECT COUNT(*) FROM service_message WHERE thread_id=?",
                Integer.class, id));
        assertEquals("还有一点想确认", jdbc.queryForObject(
                "SELECT body FROM service_message WHERE thread_id=? ORDER BY id DESC LIMIT 1", String.class, id));

        // 关闭重复提交不重复写关闭时间；关闭后追问被拒
        mockMvc.perform(post("/service/threads/" + id + "/close").session(alice.session())
                .param("_csrf", alice.csrf())).andReturn();
        String closedAt = jdbc.queryForObject("SELECT CAST(closed_at AS CHAR) FROM service_thread WHERE id=?",
                String.class, id);
        mockMvc.perform(post("/service/threads/" + id + "/close").session(alice.session())
                .param("_csrf", alice.csrf())).andReturn();
        assertEquals(closedAt, jdbc.queryForObject("SELECT CAST(closed_at AS CHAR) FROM service_thread WHERE id=?",
                String.class, id));
        MvcResult blockedReply = mockMvc.perform(post("/service/threads/" + id + "/reply").session(alice.session())
                .param("_csrf", alice.csrf()).param("body", "还能问吗")).andReturn();
        assertEquals(302, blockedReply.getResponse().getStatus());
        assertEquals(3, jdbc.queryForObject("SELECT COUNT(*) FROM service_message WHERE thread_id=?",
                Integer.class, id));

        // 重开后状态回到待回复，关闭时间清空
        mockMvc.perform(post("/service/threads/" + id + "/reopen").session(alice.session())
                .param("_csrf", alice.csrf())).andReturn();
        assertEquals("OPEN", jdbc.queryForObject("SELECT status FROM service_thread WHERE id=?", String.class, id));
        assertNull(jdbc.queryForObject("SELECT closed_at FROM service_thread WHERE id=?", String.class, id));
    }

    @Test
    void postWithoutCsrfIsForbidden() throws Exception {
        SignIn alice = signIn("alice");
        MvcResult result = mockMvc.perform(post("/service/threads").session(alice.session())
                .param("category", "SIZING").param("subject", PREFIX + "无令牌").param("body", "正文"))
                .andReturn();
        assertEquals(403, result.getResponse().getStatus());
        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM service_thread WHERE subject LIKE 'T18-%'", Integer.class));
    }
}
