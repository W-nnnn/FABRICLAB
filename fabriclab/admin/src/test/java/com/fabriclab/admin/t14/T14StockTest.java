package com.fabriclab.admin.t14;

import com.fabriclab.admin.entity.Category;
import com.fabriclab.admin.entity.Product;
import com.fabriclab.admin.exception.BusinessException;
import com.fabriclab.admin.exception.NotFoundException;
import com.fabriclab.admin.model.CategoryForm;
import com.fabriclab.admin.model.ProductForm;
import com.fabriclab.admin.service.AdminProductService;
import com.fabriclab.admin.service.CatalogService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

/** UC-A5 / AC-28：增量补货只接受正整数，基于最新库存加算并维护 version。 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "MYSQL_PASSWORD", matches = ".+")
class T14StockTest {

    @Autowired private AdminProductService products;
    @Autowired private CatalogService catalog;
    @Autowired private JdbcTemplate jdbc;

    private static final String TOP = "T14STOP";
    private static final String SUB = "T14SSUB";
    private static final String SKU = "FL-T14-STOCK-01";

    @BeforeEach void zh() { LocaleContextHolder.setLocale(Locale.SIMPLIFIED_CHINESE); }
    @AfterEach void reset() { LocaleContextHolder.resetLocaleContext(); clean(); }

    private void clean() {
        jdbc.update("DELETE FROM product WHERE sku LIKE 'FL-T14%'");
        jdbc.update("DELETE FROM category WHERE code LIKE 'T14%'");
    }

    private long product(int stock) {
        Category top = catalog.create(new CategoryForm(null, TOP, null, "T14 一级", 96));
        Category sub = catalog.create(new CategoryForm(null, SUB, top.getId(), "T14 二级", 96));
        Product p = products.create(new ProductForm(null, SKU, sub.getId(), "T14 羊毛围巾", "",
                "260.00", stock, AdminProductService.ON_SALE, "images/logo.png", 1,
                "羊毛 100%", "180g", "直筒", "锁边", "", 0L));
        return p.getId();
    }

    @Test
    void restockAddsToLatestStockAndBumpsVersion() {
        long id = product(10);
        int after = products.restock(id, 3);
        assertEquals(13, after);
        assertEquals(13, jdbc.queryForObject("SELECT stock FROM product WHERE id = ?", Integer.class, id));
        assertEquals(1L, jdbc.queryForObject("SELECT version FROM product WHERE id = ?", Long.class, id));
    }

    /** AC-28 的算式：初始 10，用户端下单 2 后剩 8，后台补货 3 → 11。 */
    @Test
    void restockReadsLatestStockAfterUserOrder() {
        long id = product(10);
        jdbc.update("UPDATE product SET stock = stock - 2, version = version + 1 WHERE id = ?", id);
        assertEquals(11, products.restock(id, 3));
        assertEquals(11, jdbc.queryForObject("SELECT stock FROM product WHERE id = ?", Integer.class, id));
        assertEquals(2L, jdbc.queryForObject("SELECT version FROM product WHERE id = ?", Long.class, id),
                "下单与补货各推一次 version");
    }

    @Test
    void restockRejectsZeroNegativeAndOversizedDelta() {
        long id = product(7);
        assertThrows(BusinessException.class, () -> products.restock(id, 0));
        assertThrows(BusinessException.class, () -> products.restock(id, -3));
        assertThrows(BusinessException.class, () -> products.restock(id, null));
        assertThrows(BusinessException.class, () -> products.restock(id, 10000));
        assertEquals(7, jdbc.queryForObject("SELECT stock FROM product WHERE id = ?", Integer.class, id),
                "被拒的补货不得改动库存");
        assertEquals(0L, jdbc.queryForObject("SELECT version FROM product WHERE id = ?", Long.class, id));
    }

    @Test
    void restockOnMissingProductIs404() {
        assertThrows(NotFoundException.class, () -> products.restock(999_999_999L, 1));
    }

    @Test
    void restockDoesNotTouchPriceStatusOrAlbum() {
        long id = product(4);
        products.restock(id, 6);
        assertEquals("260.00", jdbc.queryForObject("SELECT price FROM product WHERE id = ?",
                java.math.BigDecimal.class, id).toPlainString());
        assertEquals("ON_SALE", jdbc.queryForObject("SELECT status FROM product WHERE id = ?",
                String.class, id));
        assertEquals(10, jdbc.queryForObject("SELECT stock FROM product WHERE id = ?", Integer.class, id));
    }
}
