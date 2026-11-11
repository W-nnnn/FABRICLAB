package com.fabriclab.store.service;

import com.fabriclab.store.entity.Category;
import com.fabriclab.store.entity.Product;
import com.fabriclab.store.exception.BusinessException;
import com.fabriclab.store.mapper.CategoryMapper;
import com.fabriclab.store.mapper.ProductMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 商品模型校验：款号唯一、必须二级分类、价格库存口径、version 显式递增。 */
@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductMapper productMapper;

    @Mock
    private CategoryMapper categoryMapper;

    @InjectMocks
    private ProductService service;

    @Test
    void createAppliesDefaults() {
        when(categoryMapper.selectById(101L)).thenReturn(secondLevel());
        when(productMapper.selectCount(any())).thenReturn(0L);

        Product product = new Product();
        product.setSku("FL-CM-101");
        product.setCategoryId(101L);
        product.setName("亚麻通勤衬衫");
        product.setPrice(new BigDecimal("199.00"));
        product.setStock(10);

        Product created = service.create(product);

        assertEquals(ProductService.STATUS_DRAFT, created.getStatus());
        assertEquals(ProductService.DEFAULT_COVER, created.getCoverImage());
        assertEquals(0L, created.getVersion());
        assertEquals(0, created.getSortOrder());
    }

    @Test
    void rejectProductUnderFirstLevelCategory() {
        Category levelOne = new Category();
        levelOne.setId(1L);
        levelOne.setParentId(null);
        when(categoryMapper.selectById(1L)).thenReturn(levelOne);

        Product product = valid();
        product.setCategoryId(1L);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.create(product));
        assertEquals("商品必须归属二级分类", ex.getMessage());
        verify(productMapper, never()).insert(any(Product.class));
    }

    @Test
    void rejectDuplicateSku() {
        when(categoryMapper.selectById(101L)).thenReturn(secondLevel());
        when(productMapper.selectCount(any())).thenReturn(1L);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.create(valid()));
        assertEquals("款号已存在：FL-CM-01", ex.getMessage());
    }

    @Test
    void rejectNonPositivePrice() {
        Product product = valid();
        product.setPrice(BigDecimal.ZERO);
        assertThrows(BusinessException.class, () -> service.create(product));
    }

    @Test
    void rejectNegativeStock() {
        Product product = valid();
        product.setStock(-1);
        assertThrows(BusinessException.class, () -> service.create(product));
    }

    @Test
    void rejectUnknownStatus() {
        Product product = valid();
        product.setStatus("SOLD_OUT");
        assertThrows(BusinessException.class, () -> service.create(product));
    }

    @Test
    void updateBumpsVersion() {
        Product existing = valid();
        existing.setId(9L);
        existing.setStock(5);
        existing.setVersion(4L);
        when(productMapper.selectById(9L)).thenReturn(existing);

        Product patch = new Product();
        patch.setId(9L);
        patch.setStock(7);

        Product updated = service.update(patch);

        assertEquals(5L, updated.getVersion());
        assertEquals(7, updated.getStock());
        verify(productMapper).updateById(existing);
    }

    @Test
    void updateRejectsSkuChange() {
        Product existing = valid();
        existing.setId(9L);
        when(productMapper.selectById(9L)).thenReturn(existing);

        Product patch = new Product();
        patch.setId(9L);
        patch.setSku("FL-XX-99");

        BusinessException ex = assertThrows(BusinessException.class, () -> service.update(patch));
        assertEquals("款号创建后不可修改", ex.getMessage());
    }

    @Test
    void updateRejectsCategoryChange() {
        Product existing = valid();
        existing.setId(9L);
        existing.setCategoryId(101L);
        when(productMapper.selectById(9L)).thenReturn(existing);

        Product patch = new Product();
        patch.setId(9L);
        patch.setCategoryId(202L);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.update(patch));
        assertEquals("分类归属创建后不可修改", ex.getMessage());
    }

    private Product valid() {
        Product product = new Product();
        product.setSku("FL-CM-01");
        product.setCategoryId(101L);
        product.setName("亚麻通勤衬衫");
        product.setPrice(new BigDecimal("199.00"));
        product.setStock(10);
        return product;
    }

    private Category secondLevel() {
        Category category = new Category();
        category.setId(101L);
        category.setParentId(1L);
        return category;
    }
}
