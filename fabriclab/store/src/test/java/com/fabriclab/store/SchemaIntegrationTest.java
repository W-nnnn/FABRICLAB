package com.fabriclab.store;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 真实数据库连通性与七张表存在性检查。
 * 仅在提供 MYSQL_PASSWORD 环境变量时执行：无口令时跳过，不伪造通过。
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "MYSQL_PASSWORD", matches = ".+")
class SchemaIntegrationTest {

    private static final List<String> TABLES = List.of(
            "store_user", "category", "product", "product_image",
            "cart_item", "sales_order", "sales_order_item");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void databaseIsReachableAndAllTablesExist() {
        for (String table : TABLES) {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = ?",
                    Integer.class, table);
            assertEquals(1, count, "缺少表：" + table);
        }
    }

    @Test
    void databaseUsesUtf8mb4() {
        String charset = jdbcTemplate.queryForObject(
                "SELECT default_character_set_name FROM information_schema.schemata WHERE schema_name = DATABASE()",
                String.class);
        assertEquals("utf8mb4", charset);
    }
}
