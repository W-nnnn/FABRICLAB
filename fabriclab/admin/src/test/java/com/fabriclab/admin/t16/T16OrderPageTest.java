package com.fabriclab.admin.t16;

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

/** T16 页面链路：列表、详情、改收货、发货、删除、404 与英文渲染。 */
@SpringBootTest
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "MYSQL_PASSWORD", matches = ".+")
class T16OrderPageTest {

    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;

    private Long userId;
    private Long productId;

    @BeforeEach
    void setUp() {
        LocaleContextHolder.setLocale(Locale.SIMPLIFIED_CHINESE);
        clean();
        jdbc.update("INSERT INTO store_user (username, password_hash, display_name, role, status) "
                + "VALUES ('t16web', ?, 'T16 网页', 'CUSTOMER', 'ENABLED')",
                "$2a$10$AZQW9H0zzpstWCih7ev.rubTNT2spCFm8iydr01pdtiTnxJpA1A76");
        userId = jdbc.queryForObject("SELECT id FROM store_user WHERE username = 't16web'", Long.class);
        productId = jdbc.queryForObject("SELECT id FROM product ORDER BY id LIMIT 1", Long.class);
        pending("T16-WEB-P");
        paid("T16-WEB-1");
        cancelled("T16-WEB-C");
    }

    @AfterEach
    void tearDown() {
        LocaleContextHolder.resetLocaleContext();
        clean();
    }

    private void clean() {
        jdbc.update("DELETE FROM sales_order_item WHERE order_id IN "
                + "(SELECT id FROM sales_order WHERE order_no LIKE 'T16-WEB-%')");
        jdbc.update("DELETE FROM sales_order WHERE order_no LIKE 'T16-WEB-%'");
        jdbc.update("DELETE FROM store_user WHERE username LIKE 't16web%'");
    }

    private void pending(String orderNo) {
        jdbc.update("INSERT INTO sales_order (order_no, user_id, status, total_amount, receiver_name, "
                        + "receiver_phone, receiver_address) VALUES (?, ?, 'PENDING_PAYMENT', 168.00, "
                        + "'网页收货人', '13800000012', '上海市 T16 路 5 号')", orderNo, userId);
        item(orderNo);
    }

    private void paid(String orderNo) {
        pending(orderNo);
        jdbc.update("UPDATE sales_order SET status = 'PAID', payment_method = 'MOCK', payment_no = ?, "
                + "paid_at = NOW() WHERE order_no = ?", "MOCK-" + orderNo, orderNo);
    }

    private void cancelled(String orderNo) {
        pending(orderNo);
        jdbc.update("UPDATE sales_order SET status = 'CANCELLED', cancelled_at = NOW() "
                + "WHERE order_no = ?", orderNo);
    }

    private void item(String orderNo) {
        Long orderId = jdbc.queryForObject("SELECT id FROM sales_order WHERE order_no = ?", Long.class, orderNo);
        jdbc.update("INSERT INTO sales_order_item (order_id, product_id, sku_snapshot, "
                        + "product_name_snapshot, cover_image_snapshot, unit_price, quantity, line_amount) "
                        + "VALUES (?, ?, 'T16-SKU', 'T16 网页商品', 'img/t16.jpg', 168.00, 1, 168.00)",
                orderId, productId);
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
    void listRendersOrdersAndShipEntryOnlyForPaid() throws Exception {
        Session s = login();
        String html = html("/admin/orders", s);
        assertTrue(html.contains("订单管理"));
        assertTrue(html.contains("T16-WEB-1"));
        assertTrue(html.contains("/admin/orders/T16-WEB-1"), "订单号应链接到详情");
        assertTrue(html.contains("已支付"));
        assertTrue(html.contains("待支付"));
        assertEquals(1, count(html, "/admin/orders/T16-WEB-1/ship"), "待发货订单渲染发货入口");
        assertEquals(0, count(html, "/admin/orders/T16-WEB-P/ship"), "待支付订单不渲染发货入口");
    }

    private int count(String haystack, String needle) {
        int n = 0, i = haystack.indexOf(needle);
        while (i >= 0) { n++; i = haystack.indexOf(needle, i + needle.length()); }
        return n;
    }

    @Test
    void listSearchIsExactAndEmptyStateShows() throws Exception {
        Session s = login();
        String hit = html("/admin/orders?kw=T16-WEB-1", s);
        assertTrue(hit.contains("T16-WEB-1"));
        assertFalse(hit.contains("T16-WEB-P"));

        String none = html("/admin/orders?kw=NOPE", s);
        assertTrue(none.contains("没有符合条件的订单"));
    }

    @Test
    void detailRendersSnapshotTimelineAndActions() throws Exception {
        Session s = login();
        String paid = html("/admin/orders/T16-WEB-1", s);
        assertTrue(paid.contains("明细快照"));
        assertTrue(paid.contains("T16-SKU"));
        assertTrue(paid.contains("流转记录"));
        assertTrue(paid.contains("合计"));
        assertTrue(paid.contains("¥168.00"));
        assertTrue(paid.contains("MOCK-T16-WEB-1"), "支付凭证应原文呈现");
        assertTrue(paid.contains("确认发货"), "待发货订单应有发货按钮");
        assertTrue(paid.contains("修改收货信息"));
        assertFalse(paid.contains("删除订单"), "有效交易订单不出现删除入口");

        String pending = html("/admin/orders/T16-WEB-P", s);
        assertFalse(pending.contains("确认发货"), "待支付订单不渲染发货");
        assertTrue(pending.contains("修改收货信息"));

        String cancelled = html("/admin/orders/T16-WEB-C", s);
        assertTrue(cancelled.contains("删除订单"), "已取消且无凭证的订单才有删除入口");
        assertFalse(cancelled.contains("确认发货"));
    }

    @Test
    void updateReceiverPostsAndPersists() throws Exception {
        Session s = login();
        MvcResult r = mvc.perform(post("/admin/orders/T16-WEB-P/receiver").session(s.session())
                .param("_csrf", s.csrf())
                .param("receiverName", "新收货人")
                .param("receiverPhone", "021-6688 0011")
                .param("receiverAddress", "北京市 T16 路 6 号")).andReturn();
        assertEquals(302, r.getResponse().getStatus());
        assertEquals("/admin/orders/T16-WEB-P", r.getResponse().getRedirectedUrl());
        assertEquals("新收货人", jdbc.queryForObject(
                "SELECT receiver_name FROM sales_order WHERE order_no = 'T16-WEB-P'", String.class));
        assertTrue(html("/admin/orders/T16-WEB-P", s).contains("收货信息已更新"));
    }

    @Test
    void shipPendingIsRejectedWithNotice() throws Exception {
        Session s = login();
        MvcResult r = mvc.perform(post("/admin/orders/T16-WEB-P/ship").session(s.session())
                .param("_csrf", s.csrf())).andReturn();
        assertEquals(302, r.getResponse().getStatus());
        assertEquals("PENDING_PAYMENT", jdbc.queryForObject(
                "SELECT status FROM sales_order WHERE order_no = 'T16-WEB-P'", String.class),
                "AC-29：待支付发货被拒且状态不变");
        assertTrue(html("/admin/orders/T16-WEB-P", s).contains("只有待发货订单可以发货"));
    }

    @Test
    void shipPaidMovesToShippedOnce() throws Exception {
        Session s = login();
        MvcResult r = mvc.perform(post("/admin/orders/T16-WEB-1/ship").session(s.session())
                .param("_csrf", s.csrf())).andReturn();
        assertEquals(302, r.getResponse().getStatus());
        assertEquals("SHIPPED", jdbc.queryForObject(
                "SELECT status FROM sales_order WHERE order_no = 'T16-WEB-1'", String.class));
        assertNotNull(jdbc.queryForObject(
                "SELECT shipped_at FROM sales_order WHERE order_no = 'T16-WEB-1'", java.sql.Timestamp.class));
        assertTrue(html("/admin/orders/T16-WEB-1", s).contains("订单已发货"));
    }

    @Test
    void deleteValidTradeOrderIsRejectedButCancelledPasses() throws Exception {
        Session s = login();
        MvcResult keep = mvc.perform(post("/admin/orders/T16-WEB-1/delete").session(s.session())
                .param("_csrf", s.csrf())).andReturn();
        assertEquals(302, keep.getResponse().getStatus());
        assertEquals("/admin/orders/T16-WEB-1", keep.getResponse().getRedirectedUrl(),
                "删除被拒应留在详情页");
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM sales_order WHERE order_no = 'T16-WEB-1'", Integer.class),
                "AC-29：有效交易订单删除被拒");
        assertTrue(html("/admin/orders/T16-WEB-1", s).contains("仅已取消且无支付凭证的订单可以删除"));

        MvcResult gone = mvc.perform(post("/admin/orders/T16-WEB-C/delete").session(s.session())
                .param("_csrf", s.csrf())).andReturn();
        assertEquals(302, gone.getResponse().getStatus());
        assertEquals("/admin/orders", gone.getResponse().getRedirectedUrl());
        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM sales_order WHERE order_no = 'T16-WEB-C'", Integer.class));
        assertTrue(html("/admin/orders", s).contains("订单已删除"));
    }

    @Test
    void unknownOrderNoIs404() throws Exception {
        Session s = login();
        MvcResult r = mvc.perform(get("/admin/orders/T16-WEB-NOPE").session(s.session())).andReturn();
        assertEquals(404, r.getResponse().getStatus());
    }

    @Test
    void englishPagesSwitchCopy() throws Exception {
        Session s = login();
        String list = html("/admin/orders?lang=en", s);
        assertTrue(list.contains("Orders"));
        assertTrue(list.contains("Order No."));
        String detail = html("/admin/orders/T16-WEB-1?lang=en", s);
        assertTrue(detail.contains("Item Snapshot"));
        assertTrue(detail.contains("Timeline"));
        assertTrue(detail.contains("Mark as shipped"));
    }
}
