package com.fabriclab.admin.t16;

import com.fabriclab.admin.exception.BusinessException;
import com.fabriclab.admin.model.OrderPage;
import com.fabriclab.admin.model.OrderQuery;
import com.fabriclab.admin.service.AdminOrderService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

/** UC-A8：订单号完整精确匹配、五状态筛选、缺省页大小 10 与倒序分页。 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "MYSQL_PASSWORD", matches = ".+")
class T16OrderListTest {

    @Autowired private AdminOrderService orders;
    @Autowired private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        LocaleContextHolder.setLocale(Locale.SIMPLIFIED_CHINESE);
        clean();
        jdbc.update("INSERT INTO store_user (username, password_hash, display_name, role, status) "
                + "VALUES ('t16list', ?, 'T16 下单', 'CUSTOMER', 'ENABLED')",
                "$2a$10$AZQW9H0zzpstWCih7ev.rubTNT2spCFm8iydr01pdtiTnxJpA1A76");
        Long userId = jdbc.queryForObject("SELECT id FROM store_user WHERE username = 't16list'", Long.class);
        pending("T16-L-P1", userId, "129.00", 3);
        pending("T16-L-P2", userId, "59.90", 2);
        paid("T16-L-PA1", userId, "268.00", 5);
        shipped("T16-L-SH1", userId, "480.00", 6);
        completed("T16-L-CO1", userId, "99.00", 8);
        cancelled("T16-L-CA1", userId, "75.50", 4);
    }

    @AfterEach
    void tearDown() {
        LocaleContextHolder.resetLocaleContext();
        clean();
    }

    private void clean() {
        jdbc.update("DELETE FROM sales_order_item WHERE order_id IN "
                + "(SELECT id FROM sales_order WHERE order_no LIKE 'T16-L-%')");
        jdbc.update("DELETE FROM sales_order WHERE order_no LIKE 'T16-L-%'");
        jdbc.update("DELETE FROM store_user WHERE username LIKE 't16list%'");
    }

    private void base(String orderNo, Long userId, String amount, int hoursAgo) {
        jdbc.update("INSERT INTO sales_order (order_no, user_id, status, total_amount, receiver_name, "
                        + "receiver_phone, receiver_address, created_at) VALUES (?, ?, 'PENDING_PAYMENT', ?, "
                        + "'T16 收货人', '13800000011', '上海市 T16 路 1 号', NOW() - INTERVAL ? HOUR)",
                orderNo, userId, amount, hoursAgo);
    }

    private void pending(String orderNo, Long userId, String amount, int hoursAgo) {
        base(orderNo, userId, amount, hoursAgo);
    }

    private void paid(String orderNo, Long userId, String amount, int hoursAgo) {
        base(orderNo, userId, amount, hoursAgo);
        jdbc.update("UPDATE sales_order SET status = 'PAID', payment_method = 'MOCK', payment_no = ?, "
                + "paid_at = created_at WHERE order_no = ?", "MOCK-" + orderNo, orderNo);
    }

    private void shipped(String orderNo, Long userId, String amount, int hoursAgo) {
        paid(orderNo, userId, amount, hoursAgo);
        jdbc.update("UPDATE sales_order SET status = 'SHIPPED', shipped_at = paid_at "
                + "WHERE order_no = ?", orderNo);
    }

    private void completed(String orderNo, Long userId, String amount, int hoursAgo) {
        shipped(orderNo, userId, amount, hoursAgo);
        jdbc.update("UPDATE sales_order SET status = 'COMPLETED', completed_at = shipped_at "
                + "WHERE order_no = ?", orderNo);
    }

    private void cancelled(String orderNo, Long userId, String amount, int hoursAgo) {
        base(orderNo, userId, amount, hoursAgo);
        jdbc.update("UPDATE sales_order SET status = 'CANCELLED', cancelled_at = created_at "
                + "WHERE order_no = ?", orderNo);
    }

    @Test
    void defaultPageSizeIsTen() {
        Long userId = jdbc.queryForObject("SELECT id FROM store_user WHERE username = 't16list'", Long.class);
        for (int i = 0; i < 12; i++) {
            pending("T16-L-BULK" + i, userId, "10.00", i);
        }
        OrderPage page = orders.page(OrderQuery.of(null, null, 1, null));
        assertEquals(10, page.size());
        assertEquals(10, page.rows().size());
        assertTrue(page.pages() >= 2);
        assertTrue(page.total() >= 12);
    }

    @Test
    void keywordMatchesFullOrderNoOnly() {
        OrderPage hit = orders.page(OrderQuery.of("T16-L-P1", null, 1, null));
        assertEquals(1, hit.total());
        assertEquals("T16-L-P1", hit.rows().get(0).orderNo());

        OrderPage partial = orders.page(OrderQuery.of("T16-L", null, 1, null));
        assertEquals(0, partial.total(), "订单号是完整精确匹配，不接受片段");
    }

    @Test
    void statusFilterNarrowsResults() {
        OrderPage paid = orders.page(OrderQuery.of("T16-L-PA1", "paid", 1, null));
        assertEquals(1, paid.total());
        assertEquals("T16-L-PA1", paid.rows().get(0).orderNo());

        OrderPage cancelled = orders.page(OrderQuery.of("T16-L-CA1", "cancelled", 1, null));
        assertEquals(1, cancelled.total());
        assertEquals("T16-L-CA1", cancelled.rows().get(0).orderNo());

        OrderPage mismatch = orders.page(OrderQuery.of("T16-L-PA1", "cancelled", 1, null));
        assertEquals(0, mismatch.total(), "状态与订单号不吻合时无结果");
    }

    @Test
    void rowsOrderedByCreatedDesc() {
        OrderPage page = orders.page(OrderQuery.of(null, null, 1, 50));
        List<String> nos = page.rows().stream().map(r -> r.orderNo()).toList();
        assertTrue(nos.indexOf("T16-L-P2") < nos.indexOf("T16-L-CO1"),
                "下单更晚的订单应排在前面");
    }

    @Test
    void keywordLongerThanFortyIsRejected() {
        assertThrows(BusinessException.class,
                () -> orders.page(OrderQuery.of("X".repeat(41), null, 1, null)));
    }

    @Test
    void unknownStatusFilterIsRejected() {
        assertThrows(BusinessException.class,
                () -> orders.page(OrderQuery.of(null, "flying", 1, null)));
    }

    @Test
    void statusCountsCoverFiveStates() {
        OrderPage page = orders.page(OrderQuery.of(null, null, 1, 50));
        assertTrue(page.pendingCount() >= 2);
        assertTrue(page.paidCount() >= 1);
        assertTrue(page.shippedCount() >= 1);
        assertTrue(page.completedCount() >= 1);
        assertTrue(page.cancelledCount() >= 1);
    }
}
