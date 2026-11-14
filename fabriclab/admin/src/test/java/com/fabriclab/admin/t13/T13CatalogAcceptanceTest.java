package com.fabriclab.admin.t13;

import com.fabriclab.admin.entity.Category;
import com.fabriclab.admin.entity.Product;
import com.fabriclab.admin.exception.BusinessException;
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

/**
 * AC-25 / AC-26 真库验收：后台新增分类与商品并上架后，用户端同库查询即可见；
 * 有子分类或有商品引用的分类删除被拒。分类层级、编码唯一与同级同名规则一并覆盖。
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "MYSQL_PASSWORD", matches = ".+")
class T13CatalogAcceptanceTest {

    @Autowired private CatalogService catalog;
    @Autowired private AdminProductService products;
    @Autowired private JdbcTemplate jdbc;

    private static final String TOP = "T13TOP";
    private static final String SUB = "T13SUB";
    private static final String SKU = "FL-T13-CAT-01";

    @BeforeEach void zh() { LocaleContextHolder.setLocale(Locale.SIMPLIFIED_CHINESE); }
    @AfterEach void reset() { LocaleContextHolder.resetLocaleContext(); clean(); }

    private void clean() {
        jdbc.update("DELETE FROM product WHERE sku = ?", SKU);
        jdbc.update("DELETE FROM category WHERE code IN (?, ?)", SUB, TOP);
        jdbc.update("DELETE FROM category WHERE code LIKE 'T13%'");
        jdbc.update("DELETE FROM product WHERE sku LIKE 'FL-T13%'");
    }

    private ProductForm form(String sku, Long categoryId, String status) {
        return new ProductForm(null, sku, categoryId, "T13 验收款", "T13 摘要",
                "1280.00", 10, status, "images/logo.png", 1,
                "山羊绒 100%", "12 针", "直筒", "包边缝合", "补充说明", 0L);
    }

    @Test
    void ac25_newCategoryAndPublishedProductAreVisibleThroughStorefrontQuery() {
        Category top = catalog.create(new CategoryForm(null, TOP, null, "T13 一级", 99));
        Category sub = catalog.create(new CategoryForm(null, SUB, top.getId(), "T13 二级", 99));
        Product p = products.create(form(SKU, sub.getId(), AdminProductService.ON_SALE));

        assertNotNull(p.getId());
        // 与用户端 ProductService 相同的可见口径：ON_SALE 且归属二级分类
        Integer visible = jdbc.queryForObject(
                "SELECT COUNT(*) FROM product p JOIN category c ON c.id = p.category_id " +
                        "WHERE p.sku = ? AND p.status = 'ON_SALE' AND c.parent_id IS NOT NULL",
                Integer.class, SKU);
        assertEquals(1, visible, "AC-25：上架商品必须在用户端查询口径下可见");
        Integer price = jdbc.queryForObject("SELECT price FROM product WHERE sku = ?", Integer.class, SKU);
        assertEquals(1280, price);
    }

    @Test
    void ac26_categoryWithChildrenOrProductReferencesCannotBeDeleted() {
        Category top = catalog.create(new CategoryForm(null, TOP, null, "T13 一级", 99));
        Category sub = catalog.create(new CategoryForm(null, SUB, top.getId(), "T13 二级", 99));

        BusinessException withChildren = assertThrows(BusinessException.class,
                () -> catalog.delete(top.getId()));
        assertTrue(withChildren.getMessage().contains("子分类"));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM category WHERE code = ?",
                Integer.class, TOP));

        products.create(form(SKU, sub.getId(), AdminProductService.DRAFT));
        BusinessException withProducts = assertThrows(BusinessException.class,
                () -> catalog.delete(sub.getId()));
        assertTrue(withProducts.getMessage().contains("商品引用"));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM category WHERE code = ?",
                Integer.class, SUB));
    }

    @Test
    void categoryRulesRejectDuplicatesThreeLevelsAndMutation() {
        Category top = catalog.create(new CategoryForm(null, TOP, null, "T13 一级", 99));
        Category sub = catalog.create(new CategoryForm(null, SUB, top.getId(), "T13 二级", 99));

        assertThrows(BusinessException.class,
                () -> catalog.create(new CategoryForm(null, TOP, null, "另一个一级", 1)),
                "编码必须全局唯一");
        assertThrows(BusinessException.class,
                () -> catalog.create(new CategoryForm(null, "T13SUB2", null, "T13 一级", 1)),
                "同级同名被拒");
        assertThrows(BusinessException.class,
                () -> catalog.create(new CategoryForm(null, "T13LV3", sub.getId(), "三级分类", 1)),
                "上级必须是第一级，禁止三级分类");
        assertThrows(BusinessException.class,
                () -> catalog.update(top.getId(),
                        new CategoryForm(top.getId(), "CHANGED", null, "T13 一级", 99)),
                "编码创建后不可修改");
        assertThrows(BusinessException.class,
                () -> catalog.update(sub.getId(),
                        new CategoryForm(sub.getId(), SUB, null, "T13 二级", 99)),
                "父级创建后不可修改");
        assertThrows(BusinessException.class,
                () -> catalog.create(new CategoryForm(null, "T13NEG", null, "负数排序", -1)));
    }

    @Test
    void productRulesRejectDuplicateSkuIllegalCategoryPriceAndStatus() {
        Category top = catalog.create(new CategoryForm(null, TOP, null, "T13 一级", 99));
        Category sub = catalog.create(new CategoryForm(null, SUB, top.getId(), "T13 二级", 99));
        products.create(form(SKU, sub.getId(), AdminProductService.DRAFT));

        assertThrows(BusinessException.class,
                () -> products.create(form(SKU, sub.getId(), AdminProductService.DRAFT)),
                "款号重复被拒");
        assertThrows(BusinessException.class,
                () -> products.create(form("FL-T13-LEVEL", top.getId(), AdminProductService.DRAFT)),
                "商品不能归属一级分类");
        assertThrows(BusinessException.class,
                () -> products.create(new ProductForm(null, "FL-T13-P0", sub.getId(), "零价", "",
                        "0", 1, AdminProductService.DRAFT, "images/logo.png", 1,
                        "", "", "", "", "", 0L)), "价格必须大于 0");
        assertThrows(BusinessException.class,
                () -> products.create(new ProductForm(null, "FL-T13-P3", sub.getId(), "三位小数", "",
                        "12.345", 1, AdminProductService.DRAFT, "images/logo.png", 1,
                        "", "", "", "", "", 0L)), "价格最多两位小数");
        assertThrows(BusinessException.class,
                () -> products.create(new ProductForm(null, "FL-T13-ST", sub.getId(), "非法状态", "",
                        "12.00", 1, "ARCHIVED", "images/logo.png", 1,
                        "", "", "", "", "", 0L)), "状态只接受 DRAFT / ON_SALE");
    }

    @Test
    void englishMessagesFollowLocale() {
        LocaleContextHolder.setLocale(Locale.ENGLISH);
        BusinessException e = assertThrows(BusinessException.class,
                () -> catalog.create(new CategoryForm(null, "", null, "", 0)));
        assertTrue(e.getMessage().contains("required"), "英文界面下的提示应为英文：" + e.getMessage());
    }
}
