package com.fabriclab.admin.t15;

import com.fabriclab.admin.exception.BusinessException;
import com.fabriclab.admin.exception.NotFoundException;
import com.fabriclab.admin.model.UserForm;
import com.fabriclab.admin.service.AdminUserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

/** UC-A7：资料修改、口令重置、启停与越权拒绝；管理员账号一律不可达。 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "MYSQL_PASSWORD", matches = ".+")
class T15UserActionTest {

    @Autowired private AdminUserService users;
    @Autowired private JdbcTemplate jdbc;

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    private long id;

    @BeforeEach
    void setUp() {
        LocaleContextHolder.setLocale(Locale.SIMPLIFIED_CHINESE);
        clean();
        jdbc.update("INSERT INTO store_user (username, password_hash, display_name, role, status, email, phone) "
                        + "VALUES ('t15act', ?, 'T15 改名前', 'CUSTOMER', 'ENABLED', 'old@example.test', '13800000009')",
                encoder.encode("OldPass!2026"));
        id = jdbc.queryForObject("SELECT id FROM store_user WHERE username = 't15act'", Long.class);
        Long owner = jdbc.queryForObject("SELECT id FROM store_user WHERE username = 't15act'", Long.class);
        jdbc.update("INSERT INTO sales_order (order_no, user_id, status, total_amount, receiver_name, "
                        + "receiver_phone, receiver_address) VALUES ('T15-SNAP-1', ?, 'PENDING_PAYMENT', 259.00, "
                        + "'改名前收货人', '13800000001', '上海市 T15 路 8 号')", owner);
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

    private UserForm form(String displayName, String email, String phone) {
        return new UserForm(id, "t15act", "CUSTOMER", displayName, email, phone);
    }

    private long adminId() {
        return jdbc.queryForObject("SELECT id FROM store_user WHERE role = 'ADMIN' LIMIT 1", Long.class);
    }

    @Test
    void updatesDisplayNameEmailAndPhone() {
        users.update(id, form("  T15 改名后  ", " new@example.test ", "021-6688 0011 "));
        var row = jdbc.queryForMap("SELECT display_name, email, phone FROM store_user WHERE id = ?", id);
        assertEquals("T15 改名后", row.get("display_name"));
        assertEquals("new@example.test", row.get("email"));
        assertEquals("021-6688 0011", row.get("phone"));
    }

    @Test
    void editingProfileLeavesOrderSnapshotUntouched() {
        String before = jdbc.queryForObject(
                "SELECT receiver_name FROM sales_order WHERE order_no = 'T15-SNAP-1'", String.class);
        users.update(id, form("T15 改名后", "new@example.test", ""));
        String after = jdbc.queryForObject(
                "SELECT receiver_name FROM sales_order WHERE order_no = 'T15-SNAP-1'", String.class);
        assertEquals(before, after, "历史订单收件人是下单时的快照，改资料不得改写");
        assertEquals("改名前收货人", after);
    }

    @Test
    void blankOptionalFieldsBecomeNull() {
        users.update(id, form("T15 改名后", "  ", "  "));
        var row = jdbc.queryForMap("SELECT email, phone FROM store_user WHERE id = ?", id);
        assertNull(row.get("email"));
        assertNull(row.get("phone"));
    }

    @Test
    void usernameAndRoleAreImmutable() {
        assertThrows(BusinessException.class, () -> users.update(id,
                new UserForm(id, "t15other", "CUSTOMER", "T15 改名", "", "")));
        assertThrows(BusinessException.class, () -> users.update(id,
                new UserForm(id, "t15act", "ADMIN", "T15 改名", "", "")));
        assertEquals("T15 改名前", jdbc.queryForObject(
                "SELECT display_name FROM store_user WHERE id = ?", String.class, id), "被拒的提交不改任何字段");
        assertEquals("CUSTOMER", jdbc.queryForObject(
                "SELECT role FROM store_user WHERE id = ?", String.class, id));
    }

    @Test
    void invalidContactDetailsAreRejected() {
        assertThrows(BusinessException.class, () -> users.update(id, form("", "", "")));
        assertThrows(BusinessException.class, () -> users.update(id, form("T15", "not-an-email", "")));
        assertThrows(BusinessException.class, () -> users.update(id, form("T15", "", "abc电话")));
        assertThrows(BusinessException.class,
                () -> users.update(id, form("T".repeat(51), "", "")));
        assertThrows(BusinessException.class,
                () -> users.update(id, form("T15", "a".repeat(95) + "@example.test", "")));
    }

    @Test
    void resetPasswordSwitchesToSharedInitialPassword() {
        String before = jdbc.queryForObject(
                "SELECT password_hash FROM store_user WHERE id = ?", String.class, id);
        users.resetPassword(id);
        String after = jdbc.queryForObject(
                "SELECT password_hash FROM store_user WHERE id = ?", String.class, id);
        assertNotEquals(before, after, "重置必须换一条新散列");
        assertTrue(encoder.matches(AdminUserService.INITIAL_PASSWORD, after));
        assertFalse(encoder.matches("OldPass!2026", after), "旧口令立即失效");
    }

    @Test
    void resetPasswordIsRepeatableAndKeepsAccountUsable() {
        users.resetPassword(id);
        String first = jdbc.queryForObject(
                "SELECT password_hash FROM store_user WHERE id = ?", String.class, id);
        users.resetPassword(id);
        String second = jdbc.queryForObject(
                "SELECT password_hash FROM store_user WHERE id = ?", String.class, id);
        assertNotEquals(first, second, "每次重置都换新盐，指纹必然变化");
        assertTrue(encoder.matches(AdminUserService.INITIAL_PASSWORD, second));
        assertEquals("ENABLED", jdbc.queryForObject(
                "SELECT status FROM store_user WHERE id = ?", String.class, id));
    }

    @Test
    void disableAndEnableReportWhetherStateChanged() {
        assertTrue(users.setStatus(id, "DISABLED"));
        assertEquals("DISABLED", jdbc.queryForObject(
                "SELECT status FROM store_user WHERE id = ?", String.class, id));
        assertFalse(users.setStatus(id, "DISABLED"), "重复停用不写库");
        assertEquals("已停用", users.currentStatusLabel(id));

        assertTrue(users.setStatus(id, "ENABLED"));
        assertFalse(users.setStatus(id, "ENABLED"));
        assertEquals("已启用", users.currentStatusLabel(id));
    }

    @Test
    void unknownTargetFallsBackToEnabled() {
        users.setStatus(id, "DISABLED");
        assertTrue(users.setStatus(id, "WHATEVER"));
        assertEquals("ENABLED", jdbc.queryForObject(
                "SELECT status FROM store_user WHERE id = ?", String.class, id));
    }

    @Test
    void adminAccountIsOutOfReach() {
        long admin = adminId();
        assertThrows(NotFoundException.class, () -> users.view(admin), "管理员账号不在此入口维护");
        assertThrows(NotFoundException.class, () -> users.form(admin));
        assertThrows(NotFoundException.class, () -> users.update(admin,
                new UserForm(admin, "admin", "ADMIN", "改管理员", "", "")));
        assertThrows(NotFoundException.class, () -> users.resetPassword(admin));
        assertThrows(NotFoundException.class, () -> users.setStatus(admin, "DISABLED"));
        assertEquals("ENABLED", jdbc.queryForObject(
                "SELECT status FROM store_user WHERE id = ?", String.class, admin));
    }

    @Test
    void missingAccountIs404() {
        assertThrows(NotFoundException.class, () -> users.view(999_999_999L));
        assertThrows(NotFoundException.class, () -> users.resetPassword(999_999_999L));
    }

    @Test
    void viewCarriesRecentOrdersAndFacts() {
        var view = users.view(id);
        assertEquals("t15act", view.username());
        assertEquals("CUSTOMER", view.role());
        assertEquals(1, view.orderCount());
        assertEquals("¥259.00", view.ordersTotal());
        assertEquals(1, view.recentOrders().size());
        assertEquals("T15-SNAP-1", view.recentOrders().get(0).orderNo());
        assertEquals("待支付", view.recentOrders().get(0).statusLabel());
    }
}
