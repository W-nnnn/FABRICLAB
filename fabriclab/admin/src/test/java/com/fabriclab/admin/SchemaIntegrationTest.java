package com.fabriclab.admin;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 真实数据库连通性检查：后台只读写与用户端共享的同一份七张表，
 * 且存在一个启用的管理员账号用于登录验证。
 * 仅在提供 MYSQL_PASSWORD 环境变量时执行：无口令时跳过，不伪造通过。
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "MYSQL_PASSWORD", matches = ".+")
class SchemaIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void sharedBusinessTablesAreReachable() {
        for (String table : new String[]{"store_user", "category", "product",
                "product_image", "cart_item", "sales_order", "sales_order_item"}) {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = ?",
                    Integer.class, table);
            assertEquals(1, count, "缺少表：" + table);
        }
    }

    @Test
    void anEnabledAdminAccountExists() {
        Integer admins = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM store_user WHERE role='ADMIN' AND status='ENABLED'", Integer.class);
        org.junit.jupiter.api.Assertions.assertTrue(admins >= 1, "演示数据应含启用的管理员账号");
    }

    @Test
    void adminDoesNotOwnCustomerData() {
        Integer mixed = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sales_order o JOIN store_user u ON u.id = o.user_id "
                        + "WHERE u.role <> 'CUSTOMER'", Integer.class);
        assertEquals(0, mixed, "订单只属于顾客账号，后台不以管理员身份持有业务数据");
    }
}
