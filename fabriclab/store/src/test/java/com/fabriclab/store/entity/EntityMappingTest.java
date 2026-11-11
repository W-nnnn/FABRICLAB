package com.fabriclab.store.entity;

import com.baomidou.mybatisplus.core.metadata.TableInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 校验实体与课程 DDL 的表名、主键、关键字段映射，不依赖数据库连接。
 */
class EntityMappingTest {

    private static final List<Class<?>> ENTITIES = List.of(
            StoreUser.class, Category.class, Product.class, ProductImage.class,
            CartItem.class, SalesOrder.class, SalesOrderItem.class);

    @BeforeAll
    static void initTableInfo() {
        Configuration configuration = new Configuration();
        for (Class<?> entity : ENTITIES) {
            TableInfoHelper.initTableInfo(new org.apache.ibatis.builder.MapperBuilderAssistant(configuration, ""), entity);
        }
    }

    @Test
    void allSevenTablesAreMapped() {
        List<String> tables = ENTITIES.stream()
                .map(e -> TableInfoHelper.getTableInfo(e).getTableName())
                .sorted()
                .toList();
        assertEquals(List.of("cart_item", "category", "product", "product_image",
                "sales_order", "sales_order_item", "store_user"), tables);
    }

    @Test
    void primaryKeysAreAutoIncrementId() {
        for (Class<?> entity : ENTITIES) {
            TableInfo info = TableInfoHelper.getTableInfo(entity);
            assertNotNull(info.getKeyProperty(), entity.getSimpleName() + " 缺少主键");
            assertEquals("id", info.getKeyProperty(), entity.getSimpleName() + " 主键应为 id");
        }
    }

    @Test
    void categoryDoesNotWriteGeneratedColumn() {
        TableInfo info = TableInfoHelper.getTableInfo(Category.class);
        assertTrue(info.getFieldList().stream().noneMatch(f -> "parent_key".equals(f.getColumn())),
                "parent_key 是数据库生成列，实体不得写入");
    }

    @Test
    void productKeepsSingleSkuFields() {
        TableInfo info = TableInfoHelper.getTableInfo(Product.class);
        List<String> columns = info.getFieldList().stream().map(f -> f.getColumn()).toList();
        assertTrue(columns.contains("sku"), "商品必须保留款号 sku");
        assertTrue(columns.stream().noneMatch(c -> c.contains("size") || c.contains("color")),
                "单 SKU 口径：不得出现尺码、配色规格列");
    }
}
