package com.fabriclab.admin.t16;

import com.fabriclab.admin.exception.BusinessException;
import com.fabriclab.admin.exception.NotFoundException;
import com.fabriclab.admin.model.ReceiverForm;
import com.fabriclab.admin.service.AdminOrderService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * UC-A9、UC-A10 与 AC-29：改收货限待支付 / 待发货，发货仅 PAID → SHIPPED 一次并写
 * 首次时间，删除仅已取消且无凭证；有效交易订单删除被拒。
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "MYSQL_PASSWORD", matches = ".+")
class T16OrderActionTest {

    @Autowired private AdminOrderService orders;
    @Autowired private JdbcTemplate jdbc;

    private Long userId;
    private Long productId;

    @BeforeEach
    void setUp() {
        LocaleContextHolder.setLocale(Locale.SIMPLIFIED_CHINESE);
        clean();
        jdbc.update("INSERT INTO store_user (username, password_hash, display_name, role, status) "
                + "VALUES ('t16act', ?, 'T16 动作', 'CUSTOMER', 'ENABLED')",
                "$2a$10$AZQW9H0zzpstWCih7ev.rubTNT2spCFm8iydr01pdtiTnxJpA1A76");
        userId = jdbc.queryForObject("SELECT id FROM store_user WHERE username = 't16act'", Long.class);
        productId = jdbc.queryForObject("SELECT id FROM product ORDER BY id LIMIT 1", Long.class);
    }

    @AfterEach
    void tearDown() {
        LocaleContextHolder.resetLocaleContext();
        clean();
    }

    private void clean() {
        jdbc.update("DELETE FROM sales_order_item WHERE order_id IN "
                + "(SELECT id FROM sales_order WHERE order_no LIKE 'T16-A-%')");
        jdbc.update("DELETE FROM sales_order WHERE order_no LIKE 'T16-A-%'");
        jdbc.update("DELETE FROM store_user WHERE username LIKE 't16act%'");
    }

    private void pending(String orderNo) {
        jdbc.update("INSERT INTO sales_order (order_no, user_id, status, total_amount, receiver_name, "
                        + "receiver_phone, receiver_address) VALUES (?, ?, 'PENDING_PAYMENT', 100.00, "
                        + "'原收货人', '13800000001', '上海市 T16 路 2 号')", orderNo, userId);
        item(orderNo);
    }

    private void toPaid(String orderNo) {
        jdbc.update("UPDATE sales_order SET status = 'PAID', payment_method = 'MOCK', payment_no = ?, "
                + "paid_at = NOW() WHERE order_no = ?", "MOCK-" + orderNo, orderNo);
    }

    private void paid(String orderNo) {
        pending(orderNo);
        toPaid(orderNo);
    }

    private void toShipped(String orderNo) {
        toPaid(orderNo);
        jdbc.update("UPDATE sales_order SET status = 'SHIPPED', shipped_at = NOW() WHERE order_no = ?",
                orderNo);
    }

    private void shipped(String orderNo) {
        pending(orderNo);
        toShipped(orderNo);
    }

    private void completed(String orderNo) {
        shipped(orderNo);
        jdbc.update("UPDATE sales_order SET status = 'COMPLETED', completed_at = NOW() "
                + "WHERE order_no = ?", orderNo);
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
                        + "VALUES (?, ?, 'T16-SKU', 'T16 快照商品', 'img/t16.jpg', 100.00, 1, 100.00)",
                orderId, productId);
    }

    private ReceiverForm form(String orderNo, String name, String phone, String address) {
        return new ReceiverForm(orderNo, name, phone, address);
    }

    @Test
    void updatesReceiverOnPendingAndPaidOrders() {
        pending("T16-A-RCV1");
        orders.updateReceiver(form("T16-A-RCV1", " 新收货人 ", "021-6688 0011", "北京市 T16 路 3 号 "));
        Map<String, Object> row = jdbc.queryForMap(
                "SELECT receiver_name, receiver_phone, receiver_address FROM sales_order WHERE order_no = ?",
                "T16-A-RCV1");
        assertEquals("新收货人", row.get("receiver_name"));
        assertEquals("021-6688 0011", row.get("receiver_phone"));
        assertEquals("北京市 T16 路 3 号", row.get("receiver_address"));

        paid("T16-A-RCV2");
        orders.updateReceiver(form("T16-A-RCV2", "待发货收货人", "13800000002", "广州市 T16 路 4 号"));
        assertEquals("待发货收货人", jdbc.queryForObject(
                "SELECT receiver_name FROM sales_order WHERE order_no = 'T16-A-RCV2'", String.class));
    }

    @Test
    void rejectsReceiverUpdateBeyondPendingAndPaid() {
        shipped("T16-A-LCK1");
        assertThrows(BusinessException.class,
                () -> orders.updateReceiver(form("T16-A-LCK1", "改名", "13800000003", "地址")));
        completed("T16-A-LCK2");
        assertThrows(BusinessException.class,
                () -> orders.updateReceiver(form("T16-A-LCK2", "改名", "13800000003", "地址")));
        cancelled("T16-A-LCK3");
        assertThrows(BusinessException.class,
                () -> orders.updateReceiver(form("T16-A-LCK3", "改名", "13800000003", "地址")));
    }

    @Test
    void validatesReceiverFields() {
        pending("T16-A-VLD");
        assertThrows(BusinessException.class,
                () -> orders.updateReceiver(form("T16-A-VLD", "", "13800000001", "地址")));
        assertThrows(BusinessException.class,
                () -> orders.updateReceiver(form("T16-A-VLD", "x".repeat(51), "13800000001", "地址")));
        assertThrows(BusinessException.class,
                () -> orders.updateReceiver(form("T16-A-VLD", "收货人", "", "地址")));
        assertThrows(BusinessException.class,
                () -> orders.updateReceiver(form("T16-A-VLD", "收货人", "138#0000", "地址")));
        assertThrows(BusinessException.class,
                () -> orders.updateReceiver(form("T16-A-VLD", "收货人", "13800000001", "")));
        assertThrows(BusinessException.class,
                () -> orders.updateReceiver(form("T16-A-VLD", "收货人", "13800000001", "长".repeat(256))));
        assertEquals("原收货人", jdbc.queryForObject(
                "SELECT receiver_name FROM sales_order WHERE order_no = 'T16-A-VLD'", String.class),
                "校验失败不得写库");
    }

    @Test
    void shipsPaidOrderExactlyOnce() {
        paid("T16-A-SHIP");
        orders.ship("T16-A-SHIP");
        Map<String, Object> row = jdbc.queryForMap(
                "SELECT status, shipped_at FROM sales_order WHERE order_no = 'T16-A-SHIP'");
        assertEquals("SHIPPED", row.get("status"));
        assertNotNull(row.get("shipped_at"));

        Object firstShippedAt = row.get("shipped_at");
        assertThrows(BusinessException.class, () -> orders.ship("T16-A-SHIP"),
                "重复发货应被拒绝");
        assertEquals(firstShippedAt, jdbc.queryForObject(
                "SELECT shipped_at FROM sales_order WHERE order_no = 'T16-A-SHIP'", Object.class),
                "重复发货不得改写首次发货时间");
    }

    @Test
    void rejectsShippingUnpaidAndShippedOrders() {
        pending("T16-A-NOSH");
        assertThrows(BusinessException.class, () -> orders.ship("T16-A-NOSH"));
        assertEquals("PENDING_PAYMENT", jdbc.queryForObject(
                "SELECT status FROM sales_order WHERE order_no = 'T16-A-NOSH'", String.class),
                "待支付订单发货被拒后状态不变");
        shipped("T16-A-ALSH");
        assertThrows(BusinessException.class, () -> orders.ship("T16-A-ALSH"));
    }

    @Test
    void deletesOnlyCancelledOrdersWithoutVoucher() {
        cancelled("T16-A-DEL");
        Long orderId = jdbc.queryForObject(
                "SELECT id FROM sales_order WHERE order_no = 'T16-A-DEL'", Long.class);
        orders.delete("T16-A-DEL");
        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM sales_order WHERE order_no = 'T16-A-DEL'", Integer.class),
                "订单主表应删除");
        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM sales_order_item WHERE order_id = ?", Integer.class, orderId),
                "明细应随单删除");
    }

    @Test
    void rejectsDeletingValidTradeOrders() {
        pending("T16-A-KEEP1");
        paid("T16-A-KEEP2");
        shipped("T16-A-KEEP3");
        completed("T16-A-KEEP4");
        assertThrows(BusinessException.class, () -> orders.delete("T16-A-KEEP1"));
        assertThrows(BusinessException.class, () -> orders.delete("T16-A-KEEP2"));
        assertThrows(BusinessException.class, () -> orders.delete("T16-A-KEEP3"));
        assertThrows(BusinessException.class, () -> orders.delete("T16-A-KEEP4"));
        assertEquals(4, jdbc.queryForObject(
                "SELECT COUNT(*) FROM sales_order WHERE order_no LIKE 'T16-A-KEEP%'", Integer.class),
                "有效交易订单一个都不能删");
    }

    @Test
    void viewRejectsUnknownOrderNo() {
        assertThrows(NotFoundException.class, () -> orders.view("T16-A-NOPE"));
    }
}
