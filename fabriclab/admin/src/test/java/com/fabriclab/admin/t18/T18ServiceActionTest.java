package com.fabriclab.admin.t18;

import com.fabriclab.admin.exception.BusinessException;
import com.fabriclab.admin.exception.NotFoundException;
import com.fabriclab.admin.model.AdminUser;
import com.fabriclab.admin.model.ServiceView;
import com.fabriclab.admin.service.AdminSupportService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.*;

/** 后台咨询动作：回复的状态迁移与首次回复时间、关闭 / 重开幂等、字段校验与对话顺序。 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "MYSQL_PASSWORD", matches = ".+")
class T18ServiceActionTest {

    @Autowired private AdminSupportService support;
    @Autowired private JdbcTemplate jdbc;

    private static final AdminUser ADMIN = new AdminUser(4L, "admin", "平台管理员");
    private Long alice;
    private long threadId;

    @BeforeEach
    void setUp() {
        clean();
        alice = jdbc.queryForObject("SELECT id FROM store_user WHERE username='alice'", Long.class);
        threadId = insertThread("T18-A-01", "OPEN");
        insertMessage(threadId, "CUSTOMER", "山羊绒高领衫选 M 还是 L？");
    }

    @AfterEach
    void tearDown() {
        clean();
    }

    private void clean() {
        jdbc.update("DELETE m FROM service_message m JOIN service_thread t ON m.thread_id=t.id "
                + "WHERE t.thread_no LIKE 'T18-A-%'");
        jdbc.update("DELETE FROM service_thread WHERE thread_no LIKE 'T18-A-%'");
    }

    private long insertThread(String no, String status) {
        jdbc.update("INSERT INTO service_thread (thread_no,user_id,category,subject,status,related_sku) "
                + "VALUES (?,?,'SIZING','T18 动作验收',?, 'FL-CM-01')", no, alice, status);
        return jdbc.queryForObject("SELECT id FROM service_thread WHERE thread_no=?", Long.class, no);
    }

    private void insertMessage(long id, String sender, String body) {
        jdbc.update("INSERT INTO service_message (thread_id,sender,author_id,author_name,body) "
                + "VALUES (?,?,?,?,?)", id, sender, sender.equals("ADMIN") ? ADMIN.id() : alice,
                sender.equals("ADMIN") ? ADMIN.displayName() : "林晓", body);
    }

    private String status(long id) {
        return jdbc.queryForObject("SELECT status FROM service_thread WHERE id=?", String.class, id);
    }

    @Test
    void replySetsRepliedAndKeepsFirstReplyTime() {
        support.reply(threadId, ADMIN, "  建议选 M，肩宽 44 合身。  ");
        assertEquals("REPLIED", status(threadId));
        String first = jdbc.queryForObject("SELECT CAST(first_replied_at AS CHAR) FROM service_thread WHERE id=?",
                String.class, threadId);
        assertNotNull(first);
        String body = jdbc.queryForObject(
                "SELECT body FROM service_message WHERE thread_id=? ORDER BY id DESC LIMIT 1",
                String.class, threadId);
        assertEquals("建议选 M，肩宽 44 合身。", body);
        assertEquals("ADMIN", jdbc.queryForObject(
                "SELECT sender FROM service_message WHERE thread_id=? ORDER BY id DESC LIMIT 1",
                String.class, threadId));

        support.reply(threadId, ADMIN, "补充：如喜欢宽松可选 L。");
        assertEquals("REPLIED", status(threadId));
        assertEquals(first, jdbc.queryForObject(
                "SELECT CAST(first_replied_at AS CHAR) FROM service_thread WHERE id=?", String.class, threadId));
        assertEquals(3, jdbc.queryForObject("SELECT COUNT(*) FROM service_message WHERE thread_id=?",
                Integer.class, threadId));
    }

    @Test
    void emptyTooLongAndClosedRepliesAreRejected() {
        assertEquals("body", assertThrows(BusinessException.class,
                () -> support.reply(threadId, ADMIN, "   ")).field());
        assertEquals("body", assertThrows(BusinessException.class,
                () -> support.reply(threadId, ADMIN, "字".repeat(1001))).field());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM service_message WHERE thread_id=?",
                Integer.class, threadId));

        support.close(threadId);
        assertThrows(BusinessException.class, () -> support.reply(threadId, ADMIN, "还能回复吗"));
        assertThrows(NotFoundException.class, () -> support.reply(99999999L, ADMIN, "不存在的咨询"));
    }

    @Test
    void closeIsIdempotentAndReopenClearsClosedAt() {
        assertTrue(support.close(threadId));
        assertEquals("CLOSED", status(threadId));
        String closedAt = jdbc.queryForObject("SELECT CAST(closed_at AS CHAR) FROM service_thread WHERE id=?",
                String.class, threadId);
        assertNotNull(closedAt);
        assertFalse(support.close(threadId), "重复关闭不应再次写库");
        assertEquals(closedAt, jdbc.queryForObject(
                "SELECT CAST(closed_at AS CHAR) FROM service_thread WHERE id=?", String.class, threadId));

        assertTrue(support.reopen(threadId));
        assertEquals("OPEN", status(threadId));
        assertNull(jdbc.queryForObject("SELECT closed_at FROM service_thread WHERE id=?", String.class, threadId));
        assertFalse(support.reopen(threadId), "只有已关闭的咨询可以重开");
        assertEquals("OPEN", status(threadId));
    }

    @Test
    void viewReturnsCustomerFactsAndOrderedConversation() {
        insertMessage(threadId, "ADMIN", "已核对版型");
        insertMessage(threadId, "CUSTOMER", "谢谢");
        ServiceView view = support.view(threadId);
        assertEquals("alice", view.username());
        assertEquals("林晓", view.displayName());
        assertEquals("T18 动作验收", view.thread().getSubject());
        assertEquals(3, view.messages().size());
        assertEquals("山羊绒高领衫选 M 还是 L？", view.messages().get(0).body());
        assertTrue(view.messages().get(0).fromCustomer());
        assertFalse(view.messages().get(1).fromCustomer());
        assertEquals("谢谢", view.messages().get(2).body());
        assertEquals("admin.svc.status.open", view.statusKey());
        assertThrows(NotFoundException.class, () -> support.view(99999999L));
    }
}
