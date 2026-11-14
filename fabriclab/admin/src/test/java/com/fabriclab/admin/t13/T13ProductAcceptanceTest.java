package com.fabriclab.admin.t13;

import com.fabriclab.admin.entity.Category;
import com.fabriclab.admin.entity.Product;
import com.fabriclab.admin.exception.BusinessException;
import com.fabriclab.admin.model.CategoryForm;
import com.fabriclab.admin.model.ProductForm;
import com.fabriclab.admin.service.AdminProductService;
import com.fabriclab.admin.service.CatalogService;
import com.fabriclab.admin.service.DescriptionText;
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

/** AC-27 与商品写入口径：旧表单不覆盖新库存、上下架、受限删除、描述无损编解码。 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "MYSQL_PASSWORD", matches = ".+")
class T13ProductAcceptanceTest {

    @Autowired private CatalogService catalog;
    @Autowired private AdminProductService products;
    @Autowired private JdbcTemplate jdbc;

    private static final String TOP = "T13PTOP";
    private static final String SUB = "T13PSUB";
    private static final String SKU = "FL-T13-PROD-01";

    @BeforeEach void zh() { LocaleContextHolder.setLocale(Locale.SIMPLIFIED_CHINESE); }
    @AfterEach void reset() { LocaleContextHolder.resetLocaleContext(); clean(); }

    private void clean() {
        jdbc.update("DELETE FROM product WHERE sku LIKE 'FL-T13%'");
        jdbc.update("DELETE FROM category WHERE code LIKE 'T13P%'");
    }

    private long newProduct(String status, int stock) {
        Category top = catalog.create(new CategoryForm(null, TOP, null, "T13 商品一级", 98));
        Category sub = catalog.create(new CategoryForm(null, SUB, top.getId(), "T13 商品二级", 98));
        Product p = products.create(new ProductForm(null, SKU, sub.getId(), "T13 羊绒开衫", "摘要",
                "990.00", stock, status, "images/logo.png", 1,
                "山羊绒 100%", "12 针", "直筒", "包边缝合", "补充描述", 0L));
        return p.getId();
    }

    @Test
    void ac27_staleFormIsRejectedWithoutOverwritingNewStock() {
        long id = newProduct(AdminProductService.ON_SALE, 10);
        ProductForm stale = products.form(id);           // 读取时 version = 0
        assertEquals(0L, stale.version());

        // 模拟用户端下单 / 取消造成的并发写：version 递增、库存变化
        jdbc.update("UPDATE product SET version = version + 1, stock = stock - 2 WHERE id = ?", id);

        BusinessException e = assertThrows(BusinessException.class,
                () -> products.update(id, stale));
        assertTrue(e.getMessage().contains("已被其他操作更新"), "AC-27 应提示版本冲突");
        assertEquals(8, jdbc.queryForObject("SELECT stock FROM product WHERE id = ?",
                Integer.class, id), "旧表单不得覆盖新库存");
        assertEquals(1L, jdbc.queryForObject("SELECT version FROM product WHERE id = ?",
                Long.class, id));
    }

    @Test
    void validSaveIncrementsVersionAndKeepsStockReadOnly() {
        long id = newProduct(AdminProductService.DRAFT, 7);
        ProductForm f = products.form(id);
        products.update(id, new ProductForm(f.id(), f.sku(), f.categoryId(), "改后的名称",
                f.summary(), "1080.00", 999, AdminProductService.ON_SALE, f.coverImage(),
                f.sortOrder(), f.fabric(), f.weightNote(), f.fitNote(), f.craftNote(),
                f.descriptionExtra(), f.version()));

        assertEquals(1L, jdbc.queryForObject("SELECT version FROM product WHERE id = ?",
                Long.class, id), "保存应显式递增 version");
        assertEquals(7, jdbc.queryForObject("SELECT stock FROM product WHERE id = ?",
                Integer.class, id), "编辑页不接受库存绝对赋值");
        assertEquals("改后的名称", jdbc.queryForObject("SELECT name FROM product WHERE id = ?",
                String.class, id));
        assertEquals("ON_SALE", jdbc.queryForObject("SELECT status FROM product WHERE id = ?",
                String.class, id));
    }

    @Test
    void immutableSkuAndCategoryAreEnforced() {
        long id = newProduct(AdminProductService.DRAFT, 5);
        ProductForm f = products.form(id);
        assertThrows(BusinessException.class, () -> products.update(id,
                new ProductForm(f.id(), "CHANGED", f.categoryId(), f.name(), f.summary(), f.price(),
                        f.stock(), f.status(), f.coverImage(), f.sortOrder(), f.fabric(),
                        f.weightNote(), f.fitNote(), f.craftNote(), f.descriptionExtra(), f.version())));
        assertThrows(BusinessException.class, () -> products.update(id,
                new ProductForm(f.id(), f.sku(), 1L, f.name(), f.summary(), f.price(),
                        f.stock(), f.status(), f.coverImage(), f.sortOrder(), f.fabric(),
                        f.weightNote(), f.fitNote(), f.craftNote(), f.descriptionExtra(), f.version())));
    }

    @Test
    void publishUnpublishIsIdempotentAndDeleteRespectsReferences() {
        long id = newProduct(AdminProductService.DRAFT, 4);
        products.setStatus(id, AdminProductService.ON_SALE);
        long v1 = jdbc.queryForObject("SELECT version FROM product WHERE id = ?", Long.class, id);
        products.setStatus(id, AdminProductService.ON_SALE);
        long v2 = jdbc.queryForObject("SELECT version FROM product WHERE id = ?", Long.class, id);
        assertEquals(v1, v2, "重复上架不应再推 version");

        products.delete(id);
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM product WHERE id = ?",
                Integer.class, id), "无引用的商品可删除");
    }

    @Test
    void deleteIsRefusedWhenOrderLinesOrGalleryImagesExist() {
        long id = newProduct(AdminProductService.ON_SALE, 3);
        jdbc.update("INSERT INTO product_image (product_id, image_path, alt_text, sort_order) VALUES (?, ?, ?, ?)",
                id, "images/logo.png", "test", 0);
        try {
            BusinessException e = assertThrows(BusinessException.class, () -> products.delete(id));
            assertTrue(e.getMessage().contains("相册"));
        } finally {
            jdbc.update("DELETE FROM product_image WHERE product_id = ?", id);
        }
    }

    @Test
    void descriptionCodecIsLosslessForLegacyText() {
        String legacy = "细吊带背心 · 墨黑。面料：精纺羊毛混纺\n版型：直筒\n工艺：包边缝合";
        DescriptionText.Fields f = DescriptionText.parse(legacy);
        String recomposed = DescriptionText.compose(f);
        assertTrue(recomposed.contains("精纺羊毛混纺"), "原有面料信息不得丢失");
        assertTrue(recomposed.contains("包边缝合"), "原有工艺信息不得丢失");
        assertTrue(recomposed.contains("细吊带背心"), "未识别的原文必须完整保留");
    }
}
