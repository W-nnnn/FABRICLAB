package com.fabriclab.store;

import com.fabriclab.store.service.CancelFaultPoint;
import com.fabriclab.store.service.OrderService;
import com.fabriclab.store.service.PaymentService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * 真实库（含种子数据）下的教学模拟支付与取消竞争验收（AC-18~24）：
 * 先失败后成功再重复成功时订单状态、凭证、首次时间与库存均符合规则；伪造金额无效；
 * 下单后改价下架不改变有效待支付订单金额且不阻止付款；取消两明细只恢复一次库存且车不自动回填；
 * 下架订单取消后库存恢复、仍保持下架、已取消不能付款；取消恢复首项后注入异常整体回滚；
 * 支付与取消并发只有一种状态迁移成功；他人订单支付 404 且不改变任何数据。
 * 仅在提供 MYSQL_PASSWORD 时执行；无口令时跳过，不伪造通过。
 */
@SpringBootTest
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "MYSQL_PASSWORD", matches = ".+")
class PaymentIntegrationTest {

    private static final String SKU_A = "FL-TEST-PAY-01";
    private static final String SKU_B = "FL-TEST-PAY-02";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private OrderService orderService;

    @MockitoBean
    private CancelFaultPoint cancelFaultPoint;

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

    /** 下单并返回订单 ID（成功时跳转本人订单详情）。 */
    private long placeOrder(SignIn signed) throws Exception {
        assertEquals(200, mockMvc.perform(get("/checkout").session(signed.session()))
                .andReturn().getResponse().getStatus());
        MvcResult submitted = mockMvc.perform(post("/checkout/submit").session(signed.session())
                        .param("receiverName", "林未").param("receiverPhone", "13800001234")
                        .param("receiverAddress", "上海市静安区某路 1 号").param("_csrf", signed.csrf()))
                .andReturn();
        assertEquals(302, submitted.getResponse().getStatus(), "下单成功应重定向");
        String url = submitted.getResponse().getRedirectedUrl();
        assertNotNull(url);
        assertTrue(url.startsWith("/orders/"), url);
        return Long.parseLong(url.substring("/orders/".length()));
    }

    private MvcResult paymentPage(MockHttpSession session, long orderId) throws Exception {
        return mockMvc.perform(get("/orders/" + orderId + "/payment").session(session)).andReturn();
    }

    private MvcResult pay(MockHttpSession session, String csrf, long orderId, String... extraParams) throws Exception {
        var request = post("/orders/" + orderId + "/payment").session(session).param("_csrf", csrf);
        for (int i = 0; i + 1 < extraParams.length; i += 2) {
            request = request.param(extraParams[i], extraParams[i + 1]);
        }
        return mockMvc.perform(request).andReturn();
    }

    private MvcResult payFail(MockHttpSession session, String csrf, long orderId) throws Exception {
        return mockMvc.perform(post("/orders/" + orderId + "/payment/fail").session(session)
                .param("_csrf", csrf)).andReturn();
    }

    private MvcResult cancelOrder(MockHttpSession session, String csrf, long orderId) throws Exception {
        return mockMvc.perform(post("/orders/" + orderId + "/cancel").session(session)
                .param("_csrf", csrf)).andReturn();
    }

    private String html(MvcResult result) {
        return new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
    }

    private String flash(MvcResult result, String key) {
        Object value = result.getFlashMap().get(key);
        return value == null ? "" : value.toString();
    }

    private String statusOf(long orderId) {
        return jdbcTemplate.queryForObject("SELECT status FROM sales_order WHERE id = ?", String.class, orderId);
    }

    private String paymentNoOf(long orderId) {
        return jdbcTemplate.queryForObject("SELECT payment_no FROM sales_order WHERE id = ?", String.class, orderId);
    }

    private String paymentMethodOf(long orderId) {
        return jdbcTemplate.queryForObject("SELECT payment_method FROM sales_order WHERE id = ?", String.class, orderId);
    }

    private String paidAtOf(long orderId) {
        return jdbcTemplate.queryForObject(
                "SELECT DATE_FORMAT(paid_at, '%Y-%m-%d %H:%i:%s.%f') FROM sales_order WHERE id = ?",
                String.class, orderId);
    }

    private String cancelledAtOf(long orderId) {
        return jdbcTemplate.queryForObject(
                "SELECT DATE_FORMAT(cancelled_at, '%Y-%m-%d %H:%i:%s.%f') FROM sales_order WHERE id = ?",
                String.class, orderId);
    }

    private BigDecimal totalOf(long orderId) {
        return jdbcTemplate.queryForObject("SELECT total_amount FROM sales_order WHERE id = ?",
                BigDecimal.class, orderId);
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

    private int itemRows(long orderId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sales_order_item WHERE order_id = ?", Integer.class, orderId);
    }

    // ---------- AC-20 失败后成功、再重复成功 ----------

    @Test
    void ac20FailureThenSuccessThenRepeatKeepsFirstVoucherAndStock() throws Exception {
        SignIn alice = signIn("alice");
        addToCart(alice.session(), alice.csrf(), productA, "2");
        long orderId = placeOrder(alice);
        assertEquals(8, stockOf(productA), "下单时已扣库存");

        MvcResult page = paymentPage(alice.session(), orderId);
        assertEquals(200, page.getResponse().getStatus());
        assertTrue(html(page).contains("本站为品牌演示站点，支付不产生真实扣款"));
        assertTrue(html(page).contains("¥ 398.00"), "支付页显示固定订单金额");

        // 首次：模拟失败，仍待支付、无凭证、不动库存
        MvcResult failed = payFail(alice.session(), alice.csrf(), orderId);
        assertEquals(302, failed.getResponse().getStatus());
        assertEquals("/orders/" + orderId, failed.getResponse().getRedirectedUrl());
        assertTrue(flash(failed, "orderError").contains("订单仍为待支付"));
        assertEquals("PENDING_PAYMENT", statusOf(orderId));
        assertNull(paymentNoOf(orderId), "失败不生成凭证");
        assertNull(paidAtOf(orderId), "失败不记录成功时间");
        assertEquals(8, stockOf(productA), "失败不改变库存");

        // 再次：模拟成功
        MvcResult succeeded = pay(alice.session(), alice.csrf(), orderId);
        assertEquals(302, succeeded.getResponse().getStatus());
        assertTrue(flash(succeeded, "orderFlash").contains("支付成功，支付凭证"));
        assertEquals("PAID", statusOf(orderId));
        assertEquals("MOCK", paymentMethodOf(orderId));
        String firstNo = paymentNoOf(orderId);
        String firstPaidAt = paidAtOf(orderId);
        assertNotNull(firstNo);
        assertNotNull(firstPaidAt);
        assertEquals(8, stockOf(productA), "支付不再扣库存");

        // 重复成功：保留首次凭证与时间
        MvcResult repeated = pay(alice.session(), alice.csrf(), orderId);
        assertEquals(302, repeated.getResponse().getStatus());
        assertTrue(flash(repeated, "orderFlash").contains("保留首次支付凭证与时间"));
        assertEquals(firstNo, paymentNoOf(orderId), "重复成功不改写凭证");
        assertEquals(firstPaidAt, paidAtOf(orderId), "重复成功不改写首次成功时间");
        assertEquals("PAID", statusOf(orderId));
        assertEquals(8, stockOf(productA), "重复成功不再次扣减库存");
    }

    // ---------- AC-19 下单后改价、下架不影响有效待支付订单 ----------

    @Test
    void ac19PriceChangeAndOffSaleDoNotChangeAmountOrBlockPayment() throws Exception {
        SignIn alice = signIn("alice");
        addToCart(alice.session(), alice.csrf(), productA, "2");
        long orderId = placeOrder(alice);

        jdbcTemplate.update("UPDATE product SET price = 9999.00, name = '改名后的衬衫', status = 'DRAFT' WHERE id = ?",
                productA);

        MvcResult page = paymentPage(alice.session(), orderId);
        assertEquals(200, page.getResponse().getStatus(), "有效待支付订单仍可进入支付页");
        assertTrue(html(page).contains("¥ 398.00"));
        assertTrue(!html(page).contains("9999"), "金额只取订单总额");

        // 伪造金额参数不参与定价
        MvcResult paid = pay(alice.session(), alice.csrf(), orderId, "amount", "0.01", "totalAmount", "0.01");
        assertEquals(302, paid.getResponse().getStatus());
        assertEquals("PAID", statusOf(orderId));
        assertEquals(0, new BigDecimal("398.00").compareTo(totalOf(orderId)), "订单金额不变");
        assertEquals(0, new BigDecimal("199.00").compareTo(jdbcTemplate.queryForObject(
                "SELECT unit_price FROM sales_order_item WHERE order_id = ? LIMIT 1", BigDecimal.class, orderId)));
        assertEquals(8, stockOf(productA), "支付不重新扣库存");
    }

    // ---------- AC-21 取消两明细订单只恢复一次库存 ----------

    @Test
    void ac21CancelTwoItemsRestoresOnceAndDoesNotRefillCart() throws Exception {
        SignIn alice = signIn("alice");
        addToCart(alice.session(), alice.csrf(), productA, "2");
        addToCart(alice.session(), alice.csrf(), productB, "3");
        long orderId = placeOrder(alice);
        assertEquals(2, itemRows(orderId), "两条明细");
        assertEquals(8, stockOf(productA));
        assertEquals(2, stockOf(productB));
        assertEquals(0, cartRows(aliceId), "下单已清理结算条目");

        MvcResult cancelled = cancelOrder(alice.session(), alice.csrf(), orderId);
        assertEquals(302, cancelled.getResponse().getStatus());
        assertEquals("CANCELLED", statusOf(orderId));
        assertNotNull(cancelledAtOf(orderId));
        assertEquals(10, stockOf(productA), "首项恢复 2 件");
        assertEquals(5, stockOf(productB), "次项恢复 3 件");
        assertEquals(3L, versionOf(productA), "下单与取消各推一次版本");
        assertEquals(3L, versionOf(productB));
        assertEquals(0, cartRows(aliceId), "车不自动恢复");

        MvcResult again = cancelOrder(alice.session(), alice.csrf(), orderId);
        assertEquals(302, again.getResponse().getStatus());
        assertTrue(flash(again, "orderError").contains("已取消"));
        assertEquals(10, stockOf(productA), "重复取消不再恢复");
        assertEquals(5, stockOf(productB));
        assertEquals(3L, versionOf(productA), "重复取消不再推版本");
        assertEquals(3L, versionOf(productB));
        assertEquals(0, cartRows(aliceId));
    }

    // ---------- AC-22 下单后下架再取消，不能付款 ----------

    @Test
    void ac22OffSaleOrderCancelRestoresStockKeepsDraftAndBlocksPayment() throws Exception {
        SignIn alice = signIn("alice");
        addToCart(alice.session(), alice.csrf(), productA, "2");
        long orderId = placeOrder(alice);

        jdbcTemplate.update("UPDATE product SET status = 'DRAFT' WHERE id = ?", productA);

        MvcResult cancelled = cancelOrder(alice.session(), alice.csrf(), orderId);
        assertEquals(302, cancelled.getResponse().getStatus());
        assertEquals("CANCELLED", statusOf(orderId));
        assertEquals(10, stockOf(productA), "下架商品同样恢复数量");
        assertEquals("DRAFT", jdbcTemplate.queryForObject(
                "SELECT status FROM product WHERE id = ?", String.class, productA), "仍保持下架");

        MvcResult page = paymentPage(alice.session(), orderId);
        assertEquals(302, page.getResponse().getStatus(), "已取消订单不能进入支付页");
        assertTrue(flash(page, "orderError").contains("只有待支付订单"));

        MvcResult paid = pay(alice.session(), alice.csrf(), orderId);
        assertEquals(302, paid.getResponse().getStatus());
        assertTrue(flash(paid, "orderError").contains("不能发起支付"));
        assertEquals("CANCELLED", statusOf(orderId), "已取消不能支付");
        assertNull(paymentNoOf(orderId));
        assertEquals(10, stockOf(productA), "被拒绝的支付不改变库存");
    }

    // ---------- AC-23 取消恢复首项后注入异常整体回滚 ----------

    @Test
    void ac23CancelRollsBackOrderAndAllStocksWhenFaultInjected() throws Exception {
        SignIn alice = signIn("alice");
        addToCart(alice.session(), alice.csrf(), productA, "2");
        addToCart(alice.session(), alice.csrf(), productB, "1");
        long orderId = placeOrder(alice);
        assertEquals(8, stockOf(productA));
        assertEquals(4, stockOf(productB));

        doThrow(new IllegalStateException("injected cancel failure"))
                .when(cancelFaultPoint).afterStockRestored(anyLong());

        MvcResult cancelled = cancelOrder(alice.session(), alice.csrf(), orderId);
        assertEquals(500, cancelled.getResponse().getStatus(), "注入异常按技术异常处理");
        assertEquals("PENDING_PAYMENT", statusOf(orderId), "订单仍待支付");
        assertNull(cancelledAtOf(orderId));
        assertEquals(8, stockOf(productA), "首项库存回到取消前");
        assertEquals(4, stockOf(productB), "次项库存回到取消前");
        assertEquals(2L, versionOf(productA), "仅下单推过一次版本，取消已回滚");
        assertEquals(2L, versionOf(productB));
        assertEquals(0, cartRows(aliceId));
    }

    // ---------- AC-24 支付与取消并发只有一种迁移成功 ----------

    @Test
    void ac24ConcurrentPaymentAndCancelOnlyOneTransitionWins() throws Exception {
        SignIn alice = signIn("alice");
        addToCart(alice.session(), alice.csrf(), productA, "2");
        long orderId = placeOrder(alice);
        assertEquals(8, stockOf(productA));

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CyclicBarrier barrier = new CyclicBarrier(2);
        List<Callable<Boolean>> tasks = new ArrayList<>();
        tasks.add(() -> {
            barrier.await();
            paymentService.pay(aliceId, String.valueOf(orderId));
            return true;
        });
        tasks.add(() -> {
            barrier.await();
            orderService.cancel(aliceId, String.valueOf(orderId));
            return true;
        });
        int successes = 0;
        try {
            for (Future<Boolean> future : pool.invokeAll(tasks)) {
                try {
                    if (future.get()) {
                        successes++;
                    }
                } catch (java.util.concurrent.ExecutionException e) {
                    // 竞争失败方抛出业务异常，属预期
                }
            }
        } finally {
            pool.shutdownNow();
        }
        assertEquals(1, successes, "支付与取消只有一种迁移成功");

        String status = statusOf(orderId);
        if ("PAID".equals(status)) {
            assertNotNull(paymentNoOf(orderId));
            assertNotNull(paidAtOf(orderId));
            assertNull(cancelledAtOf(orderId));
            assertEquals(8, stockOf(productA), "成交订单不恢复库存");
        } else {
            assertEquals("CANCELLED", status);
            assertNull(paymentNoOf(orderId), "取消成功时无支付凭证");
            assertNull(paidAtOf(orderId));
            assertNotNull(cancelledAtOf(orderId));
            assertEquals(10, stockOf(productA), "取消成功恢复库存一次");
        }
    }

    // ---------- AC-18 他人订单不能发起支付 ----------

    @Test
    void ac18OtherUserCannotPayOrOpenPaymentPage() throws Exception {
        SignIn alice = signIn("alice");
        addToCart(alice.session(), alice.csrf(), productA, "2");
        long orderId = placeOrder(alice);

        SignIn bob = signIn("bob");
        MvcResult page = paymentPage(bob.session(), orderId);
        assertEquals(404, page.getResponse().getStatus(), "他人订单支付页 404");

        MvcResult paid = pay(bob.session(), bob.csrf(), orderId);
        assertEquals(404, paid.getResponse().getStatus(), "他人订单支付 404");

        MvcResult failed = payFail(bob.session(), bob.csrf(), orderId);
        assertEquals(404, failed.getResponse().getStatus(), "他人订单模拟失败 404");

        assertEquals("PENDING_PAYMENT", statusOf(orderId), "越权支付不改变订单");
        assertNull(paymentNoOf(orderId));
        assertEquals(8, stockOf(productA));
        assertEquals(bobId, userIdOf("bob"));
    }
}
