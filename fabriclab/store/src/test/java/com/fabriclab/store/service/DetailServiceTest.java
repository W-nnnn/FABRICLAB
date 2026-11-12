package com.fabriclab.store.service;

import com.fabriclab.store.entity.Category;
import com.fabriclab.store.entity.Product;
import com.fabriclab.store.entity.ProductImage;
import com.fabriclab.store.exception.BusinessException;
import com.fabriclab.store.exception.NotFoundException;
import com.fabriclab.store.mapper.CategoryMapper;
import com.fabriclab.store.mapper.ProductImageMapper;
import com.fabriclab.store.mapper.ProductMapper;
import com.fabriclab.store.service.DetailService.GalleryImage;
import com.fabriclab.store.service.DetailService.ProductDetailView;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/** 详情服务：仅上架可看、相册升序与封面回退、ID 解析、分类路径。 */
@ExtendWith(MockitoExtension.class)
class DetailServiceTest {

    @Mock
    private ProductMapper productMapper;

    @Mock
    private CategoryMapper categoryMapper;

    @Mock
    private ProductImageMapper productImageMapper;

    @InjectMocks
    private DetailService service;

    private Product product(long id, String status, Integer stock, String cover) {
        Product product = new Product();
        product.setId(id);
        product.setSku("FL-CM-99");
        product.setCategoryId(101L);
        product.setName("测试外套");
        product.setSummary("弹力面料。");
        product.setDescription("第一段\n\n第二段 ");
        product.setCoverImage(cover);
        product.setPrice(new BigDecimal("429.00"));
        product.setStock(stock);
        product.setStatus(status);
        return product;
    }

    private Category second() {
        Category category = new Category();
        category.setId(101L);
        category.setParentId(1L);
        category.setName("通勤成衣");
        return category;
    }

    private Category line() {
        Category category = new Category();
        category.setId(1L);
        category.setParentId(null);
        category.setName("通勤出行");
        return category;
    }

    private ProductImage albumRow(long id, String path, int sortOrder) {
        ProductImage image = new ProductImage();
        image.setId(id);
        image.setProductId(5L);
        image.setImagePath(path);
        image.setAltText("alt-" + id);
        image.setSortOrder(sortOrder);
        return image;
    }

    @Test
    void onSaleProductWithPathAndOrderedAlbum() {
        when(productMapper.selectById(5L)).thenReturn(product(5, "ON_SALE", 25, "images/products/FL-CM-99/1.jpg"));
        when(categoryMapper.selectById(101L)).thenReturn(second());
        when(categoryMapper.selectById(1L)).thenReturn(line());
        // 查询包装器带 sort_order、id 升序，这里模拟数据库返回的已排序结果
        when(productImageMapper.selectList(any())).thenReturn(List.of(
                albumRow(1, "images/products/FL-CM-99/1.jpg", 10),
                albumRow(2, "images/products/FL-CM-99/2.jpg", 20)));

        ProductDetailView view = service.detail("5");

        assertEquals("测试外套", view.product().getName());
        assertEquals("通勤出行", view.line().getName());
        assertEquals("通勤成衣", view.second().getName());
        assertEquals(2, view.gallery().size());
        assertEquals("images/products/FL-CM-99/1.jpg", view.gallery().get(0).path());
        assertEquals("images/products/FL-CM-99/2.jpg", view.gallery().get(1).path());
        assertEquals(List.of("第一段", "第二段"), view.descriptionLines());
        assertTrue(!view.soldOut());
    }

    @Test
    void noAlbumFallsBackToCoverThenDefaultCover() {
        when(productMapper.selectById(6L)).thenReturn(product(6, "ON_SALE", 3, "images/products/FL-XX-00/0.jpg"));
        when(categoryMapper.selectById(101L)).thenReturn(second());
        when(categoryMapper.selectById(1L)).thenReturn(line());
        when(productImageMapper.selectList(any())).thenReturn(List.of());

        ProductDetailView view = service.detail("6");

        assertEquals(1, view.gallery().size());
        assertEquals("images/products/FL-XX-00/0.jpg", view.gallery().get(0).path());

        when(productMapper.selectById(7L)).thenReturn(product(7, "ON_SALE", 0, " "));
        ProductDetailView blankCover = service.detail("7");
        assertEquals(ProductService.DEFAULT_COVER, blankCover.gallery().get(0).path());
        assertTrue(blankCover.soldOut());
    }

    @Test
    void missingOrDraftProductThrowsNotFoundWithUnavailableTitle() {
        when(productMapper.selectById(8L)).thenReturn(null);
        NotFoundException missing = assertThrows(NotFoundException.class, () -> service.detail("8"));
        assertEquals("商品不存在或已下架", missing.getTitle());

        when(productMapper.selectById(9L)).thenReturn(product(9, "DRAFT", 10, null));
        NotFoundException draft = assertThrows(NotFoundException.class, () -> service.detail("9"));
        assertEquals("商品不存在或已下架", draft.getTitle());
    }

    @Test
    void brokenCategoryReferenceThrowsNotFound() {
        when(productMapper.selectById(10L)).thenReturn(product(10, "ON_SALE", 5, null));
        when(categoryMapper.selectById(101L)).thenReturn(null);

        NotFoundException broken = assertThrows(NotFoundException.class, () -> service.detail("10"));
        assertEquals("商品分类不可用", broken.getTitle());
    }

    @Test
    void parseIdRejectsMissingNonNumericAndOverflow() {
        assertThrows(BusinessException.class, () -> service.detail(null));
        assertThrows(BusinessException.class, () -> service.detail("  "));
        assertThrows(BusinessException.class, () -> service.detail("abc"));
        assertThrows(BusinessException.class, () -> service.detail("1.5"));
        assertThrows(BusinessException.class, () -> service.detail("99999999999999999999"));
        assertEquals(7L, DetailService.parseId(" 7 "));
    }
}
