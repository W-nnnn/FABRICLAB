package com.fabriclab.store;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.fabriclab.store.service.CheckoutFaultPoint;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * 真实库（含种子数据）下的结算、下单、订单查询与取消验收（AC-12~17、AC-19、AC-30）：
 * 收货三项校验与金额由后端计算；两明细下单生成一主两明细、金额正确、库存分别扣减、车清理；
 * 改价、另一会话改车、库存不足、下架均拒绝且无残留；扣库存后注入异常时主表、明细、库存、
 * 版本与车共同回滚；下单后改名改价下架不改写快照；五状态筛选、完整订单号精确匹配、
 * 跨账号隔离与分页参数处理；取消按明细恢复库存且重复取消不再恢复。
 * 仅在提供 MYSQL_PASSWORD 时执行；无口令时跳过，不伪造通过。
 */
@SpringBootTest
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "MYSQL_PASSWORD", matches = ".+")
class OrderIntegrationTest {

    private static final String SKU_A = "FL-TEST-ORDER-01";
    private static final String SKU_B = "FL-TEST-ORDER-02";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private CheckoutFaultPoint faultPoint;

    private Long productA;
    private Long productB;
    private Long aliceId;
    private Long bobId;

    @BeforeEach
    void prepareFixture() {
        cleanUp();
        Long categoryId = jdbcTemplate.queryForObject(
                "SELECT id FROM category WHERE parent_id IS NOT NULL ORDER BY id LIMIT 1", Long.class);
        jdbcTemplate.update("""
                INSERT INTO product (sku, category_id, name, cover_image, price, stock, status, sort_order, version)
                VALUES (?, ?, '测试亚麻衬衫', 'images/demo.jpg', 199.00, 10, 'ON_SALE', 999, 1)
                """, SKU_A, categoryId);
        jdbcTemplate.update("""
                INSERT INTO product (sku, category_id, name, cover_image, price, stock, status, sort_order, version)
                VALUES (?, ?, '测试羊毛围巾', 'images/demo.jpg', 58.00, 5, 'ON_SALE', 999, 1)
                """, SKU_B, categoryId);
        productA = productIdOf(SKU_A);
        productB = productIdOf(SKU_B);
        aliceId = userIdOf("alice");
        bobId = userIdOf("bob");
        assertNotNull(productA);
        assertNotNull(aliceId);
        assertNotNull(bobId);
    }

    @AfterEach
    void cleanFixture() {
        cleanUp();
    }

    private void cleanUp() {
        jdbcTemplate.update("""
                DELETE oi FROM sales_order_item oi
                JOIN sales_order o ON oi.order_id = o.id
                JOIN store_user u ON o.user_id = u.id
                WHERE u.username IN ('alice', 'bob')
                """);
        jdbcTemplate.update("""
                DELETE o FROM sales_order o JOIN store_user u ON o.user_id = u.id
                WHERE u.username IN ('alice', 'bob')
                """);
        jdbcTemplate.update("""
                DELETE ci FROM cart_item ci JOIN store_user u ON ci.user_id = u.id
                WHERE u.username IN ('alice', 'bob')
                """);
        jdbcTemplate.update("DELETE FROM product WHERE sku IN (?, ?)", SKU_A, SKU_B);
        jdbcTemplate.update("UPDATE store_user SET status = 'ENABLED' WHERE username IN ('alice', 'bob')");
    }

    // ---------- 助手 ----------

    private record Page(String csrf, MockHttpSession session) {
    }

    private record SignIn(String csrf, MockHttpSession session) {
    }

    private Long productIdOf(String sku) {
        return jdbcTemplate.queryForObject("SELECT id FROM product WHERE sku = ?", Long.class, sku);
    }

    private Long userIdOf(String username) {
        return jdbcTemplate.queryForObject("SELECT id FROM store_user WHERE username = ?", Long.class, username);
    }

    private Page signonPage() throws Exception {
        MvcResult mvc = mockMvc.perform(get("/signon")).andReturn();
        assertEquals(200, mvc.getResponse().getStatus());
        return new Page((String) mvc.getModelAndView().getModel().get("csrfToken"),
                (MockHttpSession) mvc.getRequest().getSession());
    }

    private SignIn signIn(String username) throws Exception {
        Page page = signonPage();
        MvcResult login = mockMvc.perform(post("/signon").session(page.session())
                        .param("username", username).param("password", "Passw0rd!2026")
                        .param("_csrf", page.csrf()))
                .andReturn();
        assertEquals(302, login.getResponse().getStatus(), username + " 应登录成功");
        return new SignIn(page.csrf(), (MockHttpSession) login.getRequest().getSession());
    }

    private void addToCart(MockHttpSession session, String csrf, Long productId, String quantity) throws Exception {
        MvcResult result = mockMvc.perform(post("/cart/add").session(session)
                        .param("productId", String.valueOf(productId))
                        .param("quantity", quantity).param("_csrf", csrf))
                .andReturn();
        assertEquals(302, result.getResponse().getStatus());
    }

    private MvcResult openCheckout(MockHttpSession session) throws Exception {
        return mockMvc.perform(get("/checkout").session(session)).andReturn();
    }

    private MvcResult submit(MockHttpSession session, String csrf, String name, String phone, String address)
            throws Exception {
        return mockMvc.perform(post("/checkout/submit").session(session)
                        .param("receiverName", name).param("receiverPhone", phone)
                        .param("receiverAddress", address).param("_csrf", csrf))
                .andReturn();
    }

    private String html(MvcResult result) {
        return new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
    }

    /** 重定向携带的会话级提示（失败原因）。 */
    private String flash(MvcResult result) {
        Object value = result.getFlashMap().get("cartError");
        return value == null ? "" : value.toString();
    }

    private int orderCount(long userId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sales_order WHERE user_id = ?", Integer.class, userId);
    }

    private int itemCount(long userId) {
        return jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM sales_order_item oi JOIN sales_order o ON oi.order_id = o.id
                WHERE o.user_id = ?
                """, Integer.class, userId);
    }

    private int stockOf(Long productId) {
        return jdbcTemplate.queryForObject("SELECT stock FROM product WHERE id = ?", Integer.class, productId);
    }

    private long versionOf(Long productId) {
        return jdbcTemplate.queryForObject("SELECT version FROM product WHERE id = ?", Long.class, productId);
    }

    private int cartRows(long userId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM cart_item WHERE user_id = ?", Integer.class, userId);
    }

    private Long latestOrderId(long userId) {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM sales_order WHERE user_id = ? ORDER BY id DESC LIMIT 1", Long.class, userId);
    }

    private Long insertOrder(long userId, String status, String orderNo, LocalDateTime createdAt) {
        boolean paid = !"PENDING_PAYMENT".equals(status) && !"CANCELLED".equals(status);
        boolean shipped = "SHIPPED".equals(status) || "COMPLETED".equals(status);
        Timestamp created = Timestamp.valueOf(createdAt);
        Timestamp paidAt = Timestamp.valueOf(createdAt.plusMinutes(1));
        jdbcTemplate.update("""
                INSERT INTO sales_order (order_no, user_id, status, total_amount, receiver_name, receiver_phone,
                    receiver_address, created_at, payment_method, payment_no, paid_at, cancelled_at, shipped_at,
                    completed_at)
                VALUES (?, ?, ?, 199.00, '林未', '13800001234', '上海市静安区某路 1 号', ?, ?, ?, ?, ?, ?, ?)
                """, orderNo, userId, status, created,
                paid ? "MOCK" : null,
                paid ? "MOCK-" + orderNo : null,
                paid ? paidAt : null,
                "CANCELLED".equals(status) ? paidAt : null,
                shipped ? paidAt : null,
                "COMPLETED".equals(status) ? paidAt : null);
        return jdbcTemplate.queryForObject(
                "SELECT id FROM sales_order WHERE order_no = ?", Long.class, orderNo);
    }

    // ---------- AC-12 收货校验与金额由后端计算 ----------

    @Test
    void ac12RejectsBlankAndOverlongReceiverWithoutOrderSideEffects() throws Exception {
        SignIn alice = signIn("alice");
        addToCart(alice.session(), alice.csrf(), productA, "2");
        assertEquals(200, openCheckout(alice.session()).getResponse().getStatus());

        MvcResult blankAddress = submit(alice.session(), alice.csrf(), "林未", "13800001234", "   ");
        assertEquals(200, blankAddress.getResponse().getStatus());
        assertTrue(html(blankAddress).contains("详细地址不能为空"));
        assertEquals(0, orderCount(aliceId), "地址为空不得产生订单");

        MvcResult tooLongAddress = submit(alice.session(), alice.csrf(), "林未", "13800001234", "址".repeat(256));
        assertTrue(html(tooLongAddress).contains("详细地址最长 255 个字符"));

        MvcResult blankName = submit(alice.session(), alice.csrf(), "  ", "13800001234", "上海市静安区某路 1 号");
        assertTrue(html(blankName).contains("收货人不能为空"));

        MvcResult tooLongPhone = submit(alice.session(), alice.csrf(), "林未", "1".repeat(31), "上海市静安区某路 1 号");
        assertTrue(html(tooLongPhone).contains("联系电话最长 30 个字符"));

        assertEquals(0, orderCount(aliceId), "校验失败不得产生订单");
        assertEquals(10, stockOf(productA), "校验失败不得扣库存");
        assertEquals(1, cartRows(aliceId), "校验失败保留购物车条目");
        assertEquals(1L, versionOf(productA), "校验失败不得改商品版本");
    }

    @Test
    void ac12IgnoresTamperedAmountFieldsAndComputesServerSide() throws Exception {
        SignIn alice = signIn("alice");
        addToCart(alice.session(), alice.csrf(), productA, "2");
        assertEquals(200, openCheckout(alice.session()).getResponse().getStatus());

        MvcResult submitted = mockMvc.perform(post("/checkout/submit").session(alice.session())
                        .param("receiverName", "林未").param("receiverPhone", "13800001234")
                        .param("receiverAddress", "上海市静安区某路 1 号")
                        .param("totalAmount", "0.01").param("unitPrice", "0.01")
                        .param("lineAmount", "0.01").param("price", "0.01")
                        .param("_csrf", alice.csrf()))
                .andReturn();
        assertEquals(302, submitted.getResponse().getStatus());
        assertTrue(submitted.getResponse().getRedirectedUrl().startsWith("/orders/"));

        BigDecimal total = jdbcTemplate.queryForObject(
                "SELECT total_amount FROM sales_order WHERE user_id = ?", BigDecimal.class, aliceId);
        assertEquals(0, total.compareTo(new BigDecimal("398.00")), "总额必须由后端按当前价计算");
        BigDecimal unitPrice = jdbcTemplate.queryForObject("""
                SELECT oi.unit_price FROM sales_order_item oi JOIN sales_order o ON oi.order_id = o.id
                WHERE o.user_id = ?
                """, BigDecimal.class, aliceId);
        assertEquals(0, unitPrice.compareTo(new BigDecimal("199.00")), "明细单价取数据库快照");
    }

    // ---------- AC-14 两明细正常下单 ----------

    @Test
    void ac14PlacesOrderWithTwoItemsDeductsStockAndClearsCart() throws Exception {
        SignIn alice = signIn("alice");
        addToCart(alice.session(), alice.csrf(), productA, "2");
        addToCart(alice.session(), alice.csrf(), productB, "1");

        MvcResult page = openCheckout(alice.session());
        assertEquals(200, page.getResponse().getStatus());
        String checkoutHtml = html(page);
        assertTrue(checkoutHtml.contains("¥ 456.00"), "确认页合计由后端计算");
        assertTrue(checkoutHtml.contains("测试亚麻衬衫"));
        assertTrue(checkoutHtml.contains("测试羊毛围巾"));

        MvcResult submitted = submit(alice.session(), alice.csrf(), "  林未  ", " 13800001234 ",
                " 上海市静安区某路 1 号 ");
        assertEquals(302, submitted.getResponse().getStatus());
        String location = submitted.getResponse().getRedirectedUrl();
        assertTrue(location.startsWith("/orders/"), location);
        long orderId = Long.parseLong(location.substring("/orders/".length()));

        assertEquals(1, orderCount(aliceId), "一次整车下单只产生一条主记录");
        String status = jdbcTemplate.queryForObject(
                "SELECT status FROM sales_order WHERE id = ?", String.class, orderId);
        assertEquals("PENDING_PAYMENT", status);
        BigDecimal total = jdbcTemplate.queryForObject(
                "SELECT total_amount FROM sales_order WHERE id = ?", BigDecimal.class, orderId);
        assertEquals(0, total.compareTo(new BigDecimal("456.00")), "总额 = 398 + 58");
        String orderNo = jdbcTemplate.queryForObject(
                "SELECT order_no FROM sales_order WHERE id = ?", String.class, orderId);
        assertTrue(orderNo.startsWith("FL") && orderNo.length() <= 40, orderNo);
        assertEquals("林未", jdbcTemplate.queryForObject(
                "SELECT receiver_name FROM sales_order WHERE id = ?", String.class, orderId), "收货人去首尾空白");
        assertEquals("上海市静安区某路 1 号", jdbcTemplate.queryForObject(
                "SELECT receiver_address FROM sales_order WHERE id = ?", String.class, orderId));

        assertEquals(2, itemCount(aliceId), "两条明细");
        assertEquals(2, jdbcTemplate.queryForObject("""
                SELECT quantity FROM sales_order_item WHERE order_id = ? AND product_id = ?
                """, Integer.class, orderId, productA));
        assertEquals(0, jdbcTemplate.queryForObject("""
                SELECT line_amount FROM sales_order_item WHERE order_id = ? AND product_id = ?
                """, BigDecimal.class, orderId, productA).compareTo(new BigDecimal("398.00")));
        assertEquals("测试亚麻衬衫", jdbcTemplate.queryForObject("""
                SELECT product_name_snapshot FROM sales_order_item WHERE order_id = ? AND product_id = ?
                """, String.class, orderId, productA));
        assertEquals(1, jdbcTemplate.queryForObject("""
                SELECT quantity FROM sales_order_item WHERE order_id = ? AND product_id = ?
                """, Integer.class, orderId, productB));

        assertEquals(8, stockOf(productA), "首项库存 10 - 2");
        assertEquals(4, stockOf(productB), "次项库存 5 - 1");
        assertEquals(2L, versionOf(productA), "下单维护商品版本");
        assertEquals(2L, versionOf(productB));
        assertEquals(0, cartRows(aliceId), "结算条目已清理");

        MvcResult detail = mockMvc.perform(get("/orders/" + orderId).session(alice.session())).andReturn();
        assertEquals(200, detail.getResponse().getStatus());
        assertTrue(html(detail).contains("应付"), "待支付展示应付");
        assertTrue(html(detail).contains(orderNo));
        assertFalse(html(detail).contains("已模拟支付时间"));
    }

    // ---------- AC-13 确认后车或价格变化 ----------

    @Test
    void ac13RejectsStaleConfirmationAfterPriceChange() throws Exception {
        SignIn alice = signIn("alice");
        addToCart(alice.session(), alice.csrf(), productA, "1");
        assertEquals(200, openCheckout(alice.session()).getResponse().getStatus());

        jdbcTemplate.update("UPDATE product SET price = 159.00 WHERE id = ?", productA);

        MvcResult submitted = submit(alice.session(), alice.csrf(), "林未", "13800001234", "上海市静安区某路 1 号");
        assertEquals(200, submitted.getResponse().getStatus(), "改价后旧确认不能成交");
        assertTrue(html(submitted).contains("价格已变化"));
        assertEquals(0, orderCount(aliceId), "不产生订单");
        assertEquals(10, stockOf(productA), "不扣库存");
        assertEquals(1, cartRows(aliceId), "车保留");

        jdbcTemplate.update("UPDATE product SET price = 199.00 WHERE id = ?", productA);
    }

    @Test
    void ac13RejectsStaleConfirmationWhenCartChangedByAnotherSession() throws Exception {
        SignIn first = signIn("alice");
        addToCart(first.session(), first.csrf(), productA, "1");
        assertEquals(200, openCheckout(first.session()).getResponse().getStatus());

        // 同账号另一个会话新增一款商品
        SignIn second = signIn("alice");
        addToCart(second.session(), second.csrf(), productB, "1");

        MvcResult submitted = submit(first.session(), first.csrf(), "林未", "13800001234", "上海市静安区某路 1 号");
        assertEquals(200, submitted.getResponse().getStatus());
        assertTrue(html(submitted).contains("购物车已变化"));
        assertEquals(0, orderCount(aliceId), "不产生订单");
        assertEquals(2, cartRows(aliceId), "不误删另一会话新增的商品");
        assertEquals(10, stockOf(productA));
        assertEquals(5, stockOf(productB));
    }

    // ---------- AC-15 库存不足与下架 ----------

    @Test
    void ac15RejectsWhenStockOrStatusChangedAfterConfirmation() throws Exception {
        SignIn alice = signIn("alice");
        addToCart(alice.session(), alice.csrf(), productA, "3");
        assertEquals(200, openCheckout(alice.session()).getResponse().getStatus());

        jdbcTemplate.update("UPDATE product SET stock = 1 WHERE id = ?", productA);
        MvcResult shortStock = submit(alice.session(), alice.csrf(), "林未", "13800001234", "上海市静安区某路 1 号");
        assertEquals(302, shortStock.getResponse().getStatus(), "库存不足回购物车提示");
        assertEquals("/cart", shortStock.getResponse().getRedirectedUrl());
        assertTrue(flash(shortStock).contains("库存不足"), flash(shortStock));
        assertEquals(0, orderCount(aliceId), "不产生订单");
        assertEquals(0, itemCount(aliceId), "无明细残留");
        assertEquals(1, stockOf(productA), "库存未被扣减");
        assertEquals(1, cartRows(aliceId), "车保留");

        jdbcTemplate.update("UPDATE product SET stock = 10, status = 'DRAFT' WHERE id = ?", productA);
        MvcResult offSale = submit(alice.session(), alice.csrf(), "林未", "13800001234", "上海市静安区某路 1 号");
        assertEquals(302, offSale.getResponse().getStatus(), "下架回购物车提示");
        assertTrue(flash(offSale).contains("已下架"), flash(offSale));
        assertEquals(0, orderCount(aliceId), "下架不产生订单");
        assertEquals(1, cartRows(aliceId), "车保留");

        jdbcTemplate.update("UPDATE product SET status = 'ON_SALE' WHERE id = ?", productA);
    }

    // ---------- AC-16 注入异常回滚 ----------

    @Test
    void ac16RollsBackOrderItemsStockVersionAndCartOnInjectedFailure() throws Exception {
        SignIn alice = signIn("alice");
        addToCart(alice.session(), alice.csrf(), productA, "2");
        assertEquals(200, openCheckout(alice.session()).getResponse().getStatus());

        // 在订单与明细写入、首项库存扣减之后注入失败：清理结算条目之前抛出技术异常
        doThrow(new IllegalStateException("injected checkout failure"))
                .when(faultPoint).afterStockDeducted(anyLong());

        MvcResult submitted = submit(alice.session(), alice.csrf(), "林未", "13800001234", "上海市静安区某路 1 号");
        assertEquals(500, submitted.getResponse().getStatus(), "注入异常按技术异常处理");
        assertEquals(0, orderCount(aliceId), "主表无残留");
        assertEquals(0, itemCount(aliceId), "明细无残留");
        assertEquals(10, stockOf(productA), "库存回到下单前");
        assertEquals(1L, versionOf(productA), "商品版本回到下单前");
        assertEquals(1, cartRows(aliceId), "结算条目保留");
    }

    // ---------- AC-17 同一车后到请求 ----------

    @Test
    void ac17SecondSubmissionOfSameCartIsRejected() throws Exception {
        SignIn first = signIn("alice");
        addToCart(first.session(), first.csrf(), productA, "1");
        assertEquals(200, openCheckout(first.session()).getResponse().getStatus());

        SignIn second = signIn("alice");
        assertEquals(200, openCheckout(second.session()).getResponse().getStatus());

        MvcResult success = submit(first.session(), first.csrf(), "林未", "13800001234", "上海市静安区某路 1 号");
        assertEquals(302, success.getResponse().getStatus());
        assertTrue(success.getResponse().getRedirectedUrl().startsWith("/orders/"));

        MvcResult late = submit(second.session(), second.csrf(), "林未", "13800001234", "上海市静安区某路 1 号");
        assertEquals(302, late.getResponse().getStatus(), "后到请求不能成交");
        assertEquals("/cart", late.getResponse().getRedirectedUrl(), "后到请求回购物车提示失效");

        assertEquals(1, orderCount(aliceId), "同一车只成交一次");
        assertEquals(9, stockOf(productA), "只扣一次库存");
    }

    // ---------- AC-19 快照不变 ----------

    @Test
    void ac19KeepsSnapshotAfterProductRenameRepricingAndOffSale() throws Exception {
        SignIn alice = signIn("alice");
        addToCart(alice.session(), alice.csrf(), productA, "2");
        assertEquals(200, openCheckout(alice.session()).getResponse().getStatus());
        MvcResult submitted = submit(alice.session(), alice.csrf(), "林未", "13800001234", "上海市静安区某路 1 号");
        long orderId = Long.parseLong(submitted.getResponse().getRedirectedUrl().substring("/orders/".length()));

        jdbcTemplate.update("""
                UPDATE product SET name = '改名后衬衫', price = 1.00,
                    cover_image = 'images/other.jpg', status = 'DRAFT' WHERE id = ?
                """, productA);

        MvcResult detail = mockMvc.perform(get("/orders/" + orderId).session(alice.session())).andReturn();
        assertEquals(200, detail.getResponse().getStatus());
        String detailHtml = html(detail);
        assertTrue(detailHtml.contains("测试亚麻衬衫"), "历史明细保留原名");
        assertTrue(detailHtml.contains("¥ 199.00"), "历史明细保留原单价");
        assertFalse(detailHtml.contains("改名后衬衫"), "不改写为当前名称");

        assertEquals("测试亚麻衬衫", jdbcTemplate.queryForObject("""
                SELECT product_name_snapshot FROM sales_order_item WHERE order_id = ?
                """, String.class, orderId));
        assertEquals("images/demo.jpg", jdbcTemplate.queryForObject("""
                SELECT cover_image_snapshot FROM sales_order_item WHERE order_id = ?
                """, String.class, orderId));
        assertEquals(0, jdbcTemplate.queryForObject("""
                SELECT unit_price FROM sales_order_item WHERE order_id = ?
                """, BigDecimal.class, orderId).compareTo(new BigDecimal("199.00")));
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT total_amount FROM sales_order WHERE id = ?", BigDecimal.class, orderId)
                .compareTo(new BigDecimal("398.00")));
    }

    // ---------- AC-30 五状态、完整订单号、跨账号与分页 ----------

    @Test
    void ac30FiltersFiveStatusesMatchesExactOrderNoAndKeepsOwnerScope() throws Exception {
        String pendingNo = "FLT-AC30-PENDING";
        String paidNo = "FLT-AC30-PAID";
        String shippedNo = "FLT-AC30-SHIPPED";
        String completedNo = "FLT-AC30-COMPLETED";
        String cancelledNo = "FLT-AC30-CANCELLED";
        LocalDateTime base = LocalDateTime.of(2026, 1, 1, 9, 0);
        insertOrder(aliceId, "PENDING_PAYMENT", pendingNo, base);
        insertOrder(aliceId, "PAID", paidNo, base.plusMinutes(5));
        insertOrder(aliceId, "SHIPPED", shippedNo, base.plusMinutes(10));
        insertOrder(aliceId, "COMPLETED", completedNo, base.plusMinutes(15));
        Long cancelledOrderId = insertOrder(aliceId, "CANCELLED", cancelledNo, base.plusMinutes(20));
        insertOrder(bobId, "PENDING_PAYMENT", "FLT-AC30-BOB", base);

        SignIn alice = signIn("alice");
        String all = html(mockMvc.perform(get("/orders").session(alice.session())).andReturn());
        assertTrue(all.contains(pendingNo) && all.contains(paidNo) && all.contains(shippedNo)
                && all.contains(completedNo) && all.contains(cancelledNo));
        assertFalse(all.contains("FLT-AC30-BOB"), "列表只显示本人订单");

        assertStatusFilter(alice.session(), "PENDING_PAYMENT", pendingNo, paidNo);
        assertStatusFilter(alice.session(), "PAID", paidNo, pendingNo);
        assertStatusFilter(alice.session(), "SHIPPED", shippedNo, paidNo);
        assertStatusFilter(alice.session(), "COMPLETED", completedNo, paidNo);
        assertStatusFilter(alice.session(), "CANCELLED", cancelledNo, paidNo);

        String exact = html(mockMvc.perform(get("/orders").session(alice.session())
                .param("orderNo", "  " + paidNo + "  ")).andReturn());
        assertTrue(exact.contains(paidNo));
        assertFalse(exact.contains(pendingNo), "订单号精确匹配");

        String prefix = html(mockMvc.perform(get("/orders").session(alice.session())
                .param("orderNo", "FLT-AC30")).andReturn());
        assertTrue(prefix.contains("没有匹配该订单号的订单"), "前缀不作部分匹配");

        String combined = html(mockMvc.perform(get("/orders").session(alice.session())
                .param("status", "PAID").param("orderNo", cancelledNo)).andReturn());
        assertTrue(combined.contains("没有匹配该订单号的订单"), "状态与订单号共同生效");

        // 分页：按 created_at、id 倒序，第二页取到较早的订单
        String page1 = html(mockMvc.perform(get("/orders").session(alice.session())
                .param("page", "1").param("size", "2")).andReturn());
        String page2 = html(mockMvc.perform(get("/orders").session(alice.session())
                .param("page", "2").param("size", "2")).andReturn());
        assertTrue(page1.contains(cancelledNo), "最新的取消订单在第一页");
        assertFalse(page1.contains(pendingNo));
        assertTrue(page2.contains(pendingNo) || page2.contains(paidNo), "第二页包含较早订单");

        String overflow = html(mockMvc.perform(get("/orders").session(alice.session())
                .param("page", "99").param("size", "2")).andReturn());
        assertTrue(overflow.contains("超出末页") || overflow.contains("没有匹配"), "超过末页显示空结果");

        MvcResult badStatus = mockMvc.perform(get("/orders").session(alice.session())
                .param("status", "DELIVERING")).andReturn();
        assertEquals(400, badStatus.getResponse().getStatus(), "非法状态返回 400");
        MvcResult badSize = mockMvc.perform(get("/orders").session(alice.session())
                .param("size", "100")).andReturn();
        assertEquals(400, badSize.getResponse().getStatus(), "越界页大小返回 400");

        // 跨账号：bob 看不到 alice 的订单，也不能打开其详情
        SignIn bob = signIn("bob");
        String bobList = html(mockMvc.perform(get("/orders").session(bob.session())).andReturn());
        assertFalse(bobList.contains(cancelledNo));
        assertTrue(bobList.contains("FLT-AC30-BOB"));
        MvcResult crossDetail = mockMvc.perform(get("/orders/" + cancelledOrderId).session(bob.session())).andReturn();
        assertEquals(404, crossDetail.getResponse().getStatus(), "越权访问详情 404");
    }

    private void assertStatusFilter(MockHttpSession session, String status, String expected, String unexpected)
            throws Exception {
        String page = html(mockMvc.perform(get("/orders").session(session).param("status", status)).andReturn());
        assertTrue(page.contains(expected), status + " 应显示 " + expected);
        assertFalse(page.contains(unexpected), status + " 不应显示 " + unexpected);
    }

    // ---------- 取消待支付（FR-O-04，AC-21 基础证据） ----------

    @Test
    void cancelRestoresStockOnceAndDoesNotRefillCart() throws Exception {
        SignIn alice = signIn("alice");
        addToCart(alice.session(), alice.csrf(), productA, "2");
        addToCart(alice.session(), alice.csrf(), productB, "1");
        assertEquals(200, openCheckout(alice.session()).getResponse().getStatus());
        MvcResult submitted = submit(alice.session(), alice.csrf(), "林未", "13800001234", "上海市静安区某路 1 号");
        long orderId = Long.parseLong(submitted.getResponse().getRedirectedUrl().substring("/orders/".length()));
        assertEquals(8, stockOf(productA));
        assertEquals(2L, versionOf(productA));

        MvcResult cancelled = mockMvc.perform(post("/orders/" + orderId + "/cancel")
                .session(alice.session()).param("_csrf", alice.csrf())).andReturn();
        assertEquals(302, cancelled.getResponse().getStatus());
        assertEquals("CANCELLED", jdbcTemplate.queryForObject(
                "SELECT status FROM sales_order WHERE id = ?", String.class, orderId));
        assertNotNull(jdbcTemplate.queryForObject(
                "SELECT cancelled_at FROM sales_order WHERE id = ?", Timestamp.class, orderId));
        assertEquals(10, stockOf(productA), "按明细恢复首项库存");
        assertEquals(5, stockOf(productB), "按明细恢复次项库存");
        assertEquals(3L, versionOf(productA), "取消维护商品版本");
        assertEquals(0, cartRows(aliceId), "取消不自动回填购物车");

        MvcResult detail = mockMvc.perform(get("/orders/" + orderId).session(alice.session())).andReturn();
        assertTrue(html(detail).contains("已取消"));
        assertTrue(html(detail).contains("取消时间"));

        MvcResult repeat = mockMvc.perform(post("/orders/" + orderId + "/cancel")
                .session(alice.session()).param("_csrf", alice.csrf())).andReturn();
        assertEquals(302, repeat.getResponse().getStatus());
        assertTrue(html(mockMvc.perform(get("/orders/" + orderId).session(alice.session())).andReturn())
                .contains("订单已取消"));
        assertEquals(10, stockOf(productA), "重复取消不再恢复库存");
        assertEquals(5, stockOf(productB));
        assertEquals(3L, versionOf(productA), "重复取消不再改版本");
    }

    @Test
    void cancelRejectsPaidOrderAndOtherUsersOrder() throws Exception {
        Long paidOrderId = insertOrder(aliceId, "PAID", "FLT-AC30-PAID-2", LocalDateTime.of(2026, 1, 1, 9, 0));
        SignIn alice = signIn("alice");
        MvcResult cancelled = mockMvc.perform(post("/orders/" + paidOrderId + "/cancel")
                .session(alice.session()).param("_csrf", alice.csrf())).andReturn();
        assertEquals(302, cancelled.getResponse().getStatus());
        assertTrue(html(mockMvc.perform(get("/orders/" + paidOrderId).session(alice.session())).andReturn())
                .contains("当前状态不能取消订单"));
        assertEquals("PAID", jdbcTemplate.queryForObject(
                "SELECT status FROM sales_order WHERE id = ?", String.class, paidOrderId));

        SignIn bob = signIn("bob");
        MvcResult cross = mockMvc.perform(post("/orders/" + paidOrderId + "/cancel")
                .session(bob.session()).param("_csrf", bob.csrf())).andReturn();
        assertEquals(404, cross.getResponse().getStatus(), "越权取消 404 且不改变订单");
        assertEquals("PAID", jdbcTemplate.queryForObject(
                "SELECT status FROM sales_order WHERE id = ?", String.class, paidOrderId));
    }
}
