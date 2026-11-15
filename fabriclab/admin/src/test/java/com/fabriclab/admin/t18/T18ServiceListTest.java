package com.fabriclab.admin.t18;

import com.fabriclab.admin.model.ServicePage;
import com.fabriclab.admin.model.ServiceQuery;
import com.fabriclab.admin.model.ServiceRow;
import com.fabriclab.admin.service.AdminSupportService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** 后台咨询列表：真实状态计数、筛选、关键字与分页，数字来自当次查询而非静态值。 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "MYSQL_PASSWORD", matches = ".+")
class T18ServiceListTest {

    @Autowired private AdminSupportService support;
    @Autowired private JdbcTemplate jdbc;

    private Long alice;
    private Long bob;

    @BeforeEach
    void setUp() {
        clean();
        alice = jdbc.queryForObject("SELECT id FROM store_user WHERE username='alice'", Long.class);
        bob = jdbc.queryForObject("SELECT id FROM store_user WHERE username='bob'", Long.class);
        insert("T18-B-01", alice, "OPEN", "T18 尺码询问", "FL-CM-01", null, 1);
        insert("T18-B-02", alice, "OPEN", "T18 洗护询问", null, null, 1);
        insert("T18-B-03", bob, "REPLIED", "T18 支付询问", null, "DEMO-202611-BO-02", 2);
        insert("T18-B-04", bob, "CLOSED", "T18 试穿询问", null, null, 2);
    }

    @AfterEach
    void tearDown() {
        clean();
    }

    private void clean() {
        jdbc.update("DELETE m FROM service_message m JOIN service_thread t ON m.thread_id=t.id "
                + "WHERE t.thread_no LIKE 'T18-B-%'");
        jdbc.update("DELETE FROM service_thread WHERE thread_no LIKE 'T18-B-%'");
    }

    private void insert(String no, Long userId, String status, String subject,
                        String sku, String orderNo, int messages) {
        jdbc.update("INSERT INTO service_thread "
                        + "(thread_no,user_id,category,subject,status,related_sku,related_order_no,closed_at) "
                        + "VALUES (?,?,'FABRIC',?,?,?,?,CASE WHEN ?='CLOSED' THEN NOW() ELSE NULL END)",
                no, userId, subject, status, sku, orderNo, status);
        long id = jdbc.queryForObject("SELECT id FROM service_thread WHERE thread_no=?", Long.class, no);
        jdbc.update("INSERT INTO service_message (thread_id,sender,author_id,author_name,body) "
                + "VALUES (?,'CUSTOMER',?,'会员','第一条消息')", id, userId);
        if (messages > 1) {
            jdbc.update("INSERT INTO service_message (thread_id,sender,author_id,author_name,body) "
                    + "VALUES (?,'ADMIN',4,'平台管理员','客服已回复')", id);
        }
    }

    private List<String> numbers(ServicePage page) {
        return page.rows().stream().map(ServiceRow::threadNo).toList();
    }

    @Test
    void countsAreRealAndAddUp() {
        ServicePage page = support.page(ServiceQuery.of("T18-B", "all", 1, 10));
        assertEquals(4, page.total());
        assertEquals(4, page.rows().size());
        assertEquals(page.allCount(), page.openCount() + page.repliedCount() + page.closedCount());
        assertTrue(page.openCount() >= 2);
        assertTrue(page.repliedCount() >= 1);
        assertTrue(page.closedCount() >= 1);

        ServicePage open = support.page(ServiceQuery.of("T18-B", "open", 1, 10));
        assertEquals(2, open.total());
        assertTrue(numbers(open).containsAll(List.of("T18-B-01", "T18-B-02")));
        assertEquals(1, support.page(ServiceQuery.of("T18-B", "replied", 1, 10)).total());
        assertEquals(1, support.page(ServiceQuery.of("T18-B", "closed", 1, 10)).total());
        assertEquals(4, support.page(ServiceQuery.of("T18-B", "REFUND", 1, 10)).total(),
                "非法状态归一为全部，不静默漏数据");
    }

    @Test
    void keywordMatchesNumberSubjectCustomerAndStyle() {
        assertEquals(1, support.page(ServiceQuery.of("T18-B-03", "all", 1, 10)).total());
        assertEquals(1, support.page(ServiceQuery.of("洗护询问", "all", 1, 10)).total());
        assertEquals(2, support.page(ServiceQuery.of("bob", "all", 1, 10)).total());
        assertEquals(1, support.page(ServiceQuery.of("FL-CM-01", "all", 1, 10)).total());
        assertEquals(1, support.page(ServiceQuery.of("DEMO-202611-BO-02", "all", 1, 10)).total());
        assertEquals(0, support.page(ServiceQuery.of("不存在的关键词", "all", 1, 10)).total());
    }

    @Test
    void rowsCarryRealFactsAndPaginationWorks() {
        ServicePage first = support.page(ServiceQuery.of("T18-B", "all", 1, 10));
        ServiceRow row = first.rows().stream().filter(r -> r.threadNo().equals("T18-B-03")).findFirst().orElseThrow();
        assertEquals("bob", row.username());
        assertEquals(2, row.messageCount());
        assertEquals("REPLIED", row.status());
        assertEquals("客服已回复", row.lastSnippet());
        assertEquals("DEMO-202611-BO-02", row.relatedOrderNo());
        assertEquals("admin.svc.cat.fabric", row.categoryKey());

        ServicePage two = support.page(ServiceQuery.of("T18-B", "all", 1, 2));
        assertEquals(2, two.rows().size());
        assertEquals(2, two.pages());
        assertEquals(2, two.size());
        ServicePage last = support.page(ServiceQuery.of("T18-B", "all", 2, 2));
        assertEquals(2, last.rows().size());
        assertTrue(numbers(two).stream().noneMatch(numbers(last)::contains));
        assertEquals(0, support.page(ServiceQuery.of("T18-B", "all", 9, 2)).rows().size());
    }
}
