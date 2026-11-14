package com.fabriclab.admin.t15;

import com.fabriclab.admin.model.UserPage;
import com.fabriclab.admin.model.UserQuery;
import com.fabriclab.admin.model.UserRow;
import com.fabriclab.admin.service.AdminUserService;
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

/** UC-A7：列表只返回 CUSTOMER，用户名包含匹配、状态筛选、ID 升序分页与真实订单聚合。 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "MYSQL_PASSWORD", matches = ".+")
class T15UserListTest {

    @Autowired private AdminUserService users;
    @Autowired private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        LocaleContextHolder.setLocale(Locale.SIMPLIFIED_CHINESE);
        clean();
        user("t15a", "T15 甲", "ENABLED");
        user("t15b", "T15 乙", "DISABLED");
        user("t15c", "T15 丙", "ENABLED");
        order("t15a", "T15-ORD-1", "199.00");
        order("t15a", "T15-ORD-2", "301.50");
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

    private void user(String username, String displayName, String status) {
        jdbc.update("INSERT INTO store_user (username, password_hash, display_name, role, status, email) "
                        + "VALUES (?, ?, ?, 'CUSTOMER', ?, ?)",
                username, "$2a$10$AZQW9H0zzpstWCih7ev.rubTNT2spCFm8iydr01pdtiTnxJpA1A76",
                displayName, status, username + "@example.test");
    }

    private void order(String username, String orderNo, String amount) {
        Long userId = jdbc.queryForObject("SELECT id FROM store_user WHERE username = ?", Long.class, username);
        jdbc.update("INSERT INTO sales_order (order_no, user_id, status, total_amount, "
                        + "receiver_name, receiver_phone, receiver_address) "
                        + "VALUES (?, ?, 'PENDING_PAYMENT', ?, 'T15 收货人', '13800000000', '上海市 T15 路 1 号')",
                orderNo, userId, new java.math.BigDecimal(amount));
    }

    @Test
    void listOnlyReturnsCustomersSoAdminAccountIsNeverManaged() {
        UserPage page = users.page(UserQuery.of(null, null, 1, 50));
        assertTrue(page.rows().stream().noneMatch(r -> r.username().equals("admin")),
                "管理员账号不得出现在顾客列表");
        long expected = jdbc.queryForObject(
                "SELECT COUNT(*) FROM store_user WHERE role = 'CUSTOMER'", Long.class);
        assertEquals(expected, page.total());
    }

    @Test
    void usernameContainsMatchIgnoresCaseAndKeepsIdOrder() {
        UserPage page = users.page(UserQuery.of("T15", null, 1, 20));
        assertEquals(3, page.total());
        List<Long> ids = page.rows().stream().map(UserRow::id).toList();
        assertEquals(ids.stream().sorted().toList(), ids, "列表按 ID 升序");
        assertEquals(List.of("t15a", "t15b", "t15c"),
                page.rows().stream().map(UserRow::username).toList());

        assertEquals(1, users.page(UserQuery.of("t15b", null, 1, 20)).total(), "包含匹配而非前缀匹配");
        assertEquals(0, users.page(UserQuery.of("nobody-here", null, 1, 20)).total());
    }

    @Test
    void statusFilterAndSegmentCountsComeFromSameQuery() {
        UserPage enabled = users.page(UserQuery.of("t15", "enabled", 1, 20));
        assertEquals(2, enabled.total());
        assertTrue(enabled.rows().stream().allMatch(r -> "ENABLED".equals(r.status())));

        UserPage disabled = users.page(UserQuery.of("t15", "disabled", 1, 20));
        assertEquals(1, disabled.total());
        assertEquals("已停用", disabled.rows().get(0).statusLabel());

        long all = jdbc.queryForObject("SELECT COUNT(*) FROM store_user WHERE role='CUSTOMER'", Long.class);
        long on = jdbc.queryForObject("SELECT COUNT(*) FROM store_user WHERE role='CUSTOMER' AND status='ENABLED'", Long.class);
        long off = jdbc.queryForObject("SELECT COUNT(*) FROM store_user WHERE role='CUSTOMER' AND status='DISABLED'", Long.class);
        assertEquals(all, enabled.allCount());
        assertEquals(on, enabled.enabledCount());
        assertEquals(off, enabled.disabledCount());
        assertEquals(all, on + off);
    }

    @Test
    void unknownFilterFallsBackToAllAndPageSizeIsWhitelisted() {
        UserPage page = users.page(UserQuery.of("t15", "whatever", 0, 7));
        assertEquals(3, page.total());
        assertEquals(20, page.size(), "页大小只接受 10 / 20 / 50");
        assertEquals(1, page.page(), "页码小于 1 时回落第一页");
    }

    @Test
    void pagingSplitsRowsWithoutLosingAny() {
        for (int i = 0; i < 12; i++) {
            user("t15p" + i, "T15 分页 " + i, "ENABLED");
        }
        UserPage first = users.page(UserQuery.of("t15p", null, 1, 10));
        assertEquals(12, first.total());
        assertEquals(2, first.pages());
        assertEquals(10, first.rows().size());

        UserPage second = users.page(UserQuery.of("t15p", null, 2, 10));
        assertEquals(2, second.rows().size());
        assertTrue(first.rows().get(9).id() < second.rows().get(0).id(), "两页之间仍按 ID 升序");
        assertTrue(first.rows().stream().noneMatch(a -> second.rows().stream()
                .anyMatch(b -> a.id() == b.id())), "两页不重复");
    }

    @Test
    void orderCountAndAmountAggregatePerAccount() {
        UserRow row = users.page(UserQuery.of("t15a", null, 1, 20)).rows().get(0);
        assertEquals(2, row.orderCount());
        assertEquals("¥500.50", row.ordersTotal());

        UserRow empty = users.page(UserQuery.of("t15c", null, 1, 20)).rows().get(0);
        assertEquals(0, empty.orderCount());
        assertEquals("¥0.00", empty.ordersTotal());
    }

    @Test
    void englishLabelsUseLocalizedStatus() {
        LocaleContextHolder.setLocale(Locale.ENGLISH);
        UserPage page = users.page(UserQuery.of("t15b", null, 1, 20));
        assertEquals("Disabled", page.rows().get(0).statusLabel());
        assertEquals(3, users.page(UserQuery.of("T15", null, 1, 20)).total(), "英文界面同样按用户名匹配");
    }
}
