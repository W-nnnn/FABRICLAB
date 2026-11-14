package com.fabriclab.admin.t14;

import com.fabriclab.admin.entity.Category;
import com.fabriclab.admin.entity.Product;
import com.fabriclab.admin.entity.ProductImage;
import com.fabriclab.admin.exception.BusinessException;
import com.fabriclab.admin.exception.NotFoundException;
import com.fabriclab.admin.model.AlbumImage;
import com.fabriclab.admin.model.CategoryForm;
import com.fabriclab.admin.model.ProductForm;
import com.fabriclab.admin.service.AdminProductService;
import com.fabriclab.admin.service.AlbumText;
import com.fabriclab.admin.service.CatalogService;
import com.fabriclab.admin.service.ProductAlbumService;
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
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** UC-A6：相册三组图、来源许可编解码、设为封面与删除，回退封面由用户端承担。 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "MYSQL_PASSWORD", matches = ".+")
class T14AlbumTest {

    @Autowired private ProductAlbumService album;
    @Autowired private AdminProductService products;
    @Autowired private CatalogService catalog;
    @Autowired private JdbcTemplate jdbc;

    private static final String TOP = "T14ATOP";
    private static final String SUB = "T14ASUB";
    private static final String SKU = "FL-T14-ALBUM-01";

    @BeforeEach void zh() { LocaleContextHolder.setLocale(Locale.SIMPLIFIED_CHINESE); }
    @AfterEach void reset() { LocaleContextHolder.resetLocaleContext(); clean(); }

    private void clean() {
        jdbc.update("DELETE FROM product_image WHERE product_id IN (SELECT id FROM product WHERE sku LIKE 'FL-T14%')");
        jdbc.update("DELETE FROM product WHERE sku LIKE 'FL-T14%'");
        jdbc.update("DELETE FROM category WHERE code LIKE 'T14%'");
    }

    private long product(String cover) {
        Category top = catalog.create(new CategoryForm(null, TOP, null, "T14 相册一级", 95));
        Category sub = catalog.create(new CategoryForm(null, SUB, top.getId(), "T14 相册二级", 95));
        Product p = products.create(new ProductForm(null, SKU, sub.getId(), "T14 亚麻衬衫", "",
                "520.00", 9, AdminProductService.ON_SALE, cover, 1,
                "亚麻 100%", "", "直筒", "包边", "", 0L));
        return p.getId();
    }

    @Test
    void codecRoundTripsAndKeepsLegacyText() {
        String encoded = AlbumText.compose("W", "© FABRICLAB", "正面穿着，配白衬衫");
        AlbumText.Meta meta = AlbumText.parse(encoded);
        assertEquals("W", meta.kind());
        assertEquals("© FABRICLAB", meta.credit());
        assertEquals("正面穿着，配白衬衫", meta.alt());

        AlbumText.Meta legacy = AlbumText.parse("羊毛套头衫 · 深灰");
        assertEquals("F", legacy.kind(), "历史数据按平铺图处理");
        assertEquals("", legacy.credit());
        assertEquals("羊毛套头衫 · 深灰", legacy.alt());

        assertEquals("F", AlbumText.parse(AlbumText.compose(null, null, "只有替代文本")).kind());
        assertNull(AlbumText.kind("上身图"), "只接受 F / W / D 三种代码");
        assertTrue(encoded.length() <= 120, "编码后不得超过 alt_text 列宽");
    }

    @Test
    void threeGroupsKeepOrderAndMetadata() {
        long id = product("images/logo.png");
        album.add(id, "F", "images/products/FL-CM-01/1.jpg", "© FABRICLAB", "正面平铺", 2);
        album.add(id, "W", "images/products/FL-CM-01/2.jpg", "© FABRICLAB", "上身正面", 1);
        album.add(id, "D", "images/products/FL-CM-01/3.jpg", "", "", 1);

        Map<String, List<AlbumImage>> groups = album.grouped(id);
        assertEquals(3, groups.size());
        assertEquals(1, groups.get(AlbumText.FLAT).size());
        assertEquals(1, groups.get(AlbumText.WORN).size());
        assertEquals(1, groups.get(AlbumText.DETAIL).size());
        assertEquals("正面平铺", groups.get(AlbumText.FLAT).get(0).alt());
        assertEquals("© FABRICLAB", groups.get(AlbumText.WORN).get(0).credit());
        assertFalse(groups.get(AlbumText.FLAT).get(0).cover(), "未设为封面时不应标记");
    }

    @Test
    void addRejectsBadKindAndUnsafePath() {
        long id = product("images/logo.png");
        assertThrows(BusinessException.class,
                () -> album.add(id, "X", "images/logo.png", "", "", 0));
        assertThrows(BusinessException.class,
                () -> album.add(id, "F", "  ", "", "", 0));
        assertThrows(BusinessException.class,
                () -> album.add(id, "F", "/images/logo.png", "", "", 0));
        assertThrows(BusinessException.class,
                () -> album.add(id, "F", "../secret.jpg", "", "", 0));
        assertThrows(BusinessException.class,
                () -> album.add(id, "F", "images/logo.png", "", "", -1));
        assertEquals(0, album.count(id), "被拒的条目不得落库");
    }

    @Test
    void setCoverWritesPathAndVersionThenRefusesForeignImage() {
        long id = product("images/logo.png");
        ProductImage image = album.add(id, "F", "images/products/FL-CM-01/1.jpg", "", "正面", 0);
        album.setCover(id, image.getId());

        assertEquals("images/products/FL-CM-01/1.jpg",
                jdbc.queryForObject("SELECT cover_image FROM product WHERE id = ?", String.class, id));
        assertEquals(1L, jdbc.queryForObject("SELECT version FROM product WHERE id = ?", Long.class, id));
        assertTrue(album.images(id).get(0).cover(), "设为封面后该条目应带封面标记");

        album.setCover(id, image.getId());
        assertEquals(1L, jdbc.queryForObject("SELECT version FROM product WHERE id = ?", Long.class, id),
                "重复设为同一封面不应再推 version");

        long other = secondProduct();
        assertThrows(NotFoundException.class, () -> album.setCover(other, image.getId()),
                "不能把别的商品的图片设成本商品封面");
    }

    private long secondProduct() {
        jdbc.update("INSERT INTO product (sku, category_id, name, price, stock, status, cover_image, sort_order) "
                + "SELECT ?, id, 'T14 另一件', 300.00, 1, 'ON_SALE', 'images/logo.png', 2 FROM category WHERE code = ?",
                "FL-T14-ALBUM-02", SUB);
        return jdbc.queryForObject("SELECT id FROM product WHERE sku = ?", Long.class, "FL-T14-ALBUM-02");
    }

    @Test
    void deleteRemovesOnlyRequestedImage() {
        long id = product("images/logo.png");
        ProductImage a = album.add(id, "F", "images/products/FL-CM-01/1.jpg", "", "", 0);
        album.add(id, "D", "images/products/FL-CM-01/2.jpg", "", "", 0);
        album.delete(id, a.getId());
        assertEquals(1, album.count(id));
        assertEquals(AlbumText.DETAIL, album.images(id).get(0).kind());
    }
}
