package com.fabriclab.store.service;

import com.fabriclab.store.entity.Category;
import com.fabriclab.store.exception.BusinessException;
import com.fabriclab.store.mapper.CategoryMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 分类两级约束：一级 / 二级归属、编码唯一、同级名唯一、创建后不可变字段。 */
@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    @Mock
    private CategoryMapper categoryMapper;

    @InjectMocks
    private CategoryService service;

    @Test
    void createLevelOneCategory() {
        when(categoryMapper.selectCount(any())).thenReturn(0L, 0L);

        Category created = service.create("COMMUTE", null, "通勤系列", 10);

        assertEquals("COMMUTE", created.getCode());
        assertEquals("通勤系列", created.getName());
        assertEquals(10, created.getSortOrder());
        verify(categoryMapper).insert(created);
    }

    @Test
    void createLevelTwoUnderLevelOne() {
        Category parent = new Category();
        parent.setId(1L);
        parent.setCode("COMMUTE");
        parent.setParentId(null);
        when(categoryMapper.selectById(1L)).thenReturn(parent);
        when(categoryMapper.selectCount(any())).thenReturn(0L, 0L);

        Category created = service.create("COMMUTE_GAR", 1L, "成衣", 10);

        assertEquals(1L, created.getParentId());
        verify(categoryMapper).insert(created);
    }

    @Test
    void rejectThirdLevel() {
        Category parent = new Category();
        parent.setId(101L);
        parent.setParentId(1L);
        when(categoryMapper.selectById(101L)).thenReturn(parent);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.create("BAD_CODE", 101L, "三级", 10));

        assertEquals("分类固定两级，上级必须是一级分类", ex.getMessage());
        verify(categoryMapper, never()).insert(any(Category.class));
    }

    @Test
    void rejectDuplicateCode() {
        when(categoryMapper.selectCount(any())).thenReturn(1L);

        assertThrows(BusinessException.class, () -> service.create("COMMUTE", null, "通勤系列", 10));
    }

    @Test
    void rejectDuplicateSiblingName() {
        Category parent = new Category();
        parent.setId(1L);
        parent.setParentId(null);
        when(categoryMapper.selectById(1L)).thenReturn(parent);
        when(categoryMapper.selectCount(any())).thenReturn(0L, 1L);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.create("COMMUTE_GAR", 1L, "成衣", 10));

        assertEquals("同级分类名称已存在：成衣", ex.getMessage());
    }

    @Test
    void updateKeepsCodeAndParentImmutable() {
        Category existing = new Category();
        existing.setId(101L);
        existing.setCode("COMMUTE_GAR");
        existing.setParentId(1L);
        existing.setName("成衣");
        existing.setSortOrder(10);
        when(categoryMapper.selectById(101L)).thenReturn(existing);
        when(categoryMapper.selectCount(any())).thenReturn(0L);

        Category updated = service.update(101L, "成衣款", 20);

        assertEquals("COMMUTE_GAR", updated.getCode());
        assertEquals(1L, updated.getParentId());
        assertEquals("成衣款", updated.getName());
        assertEquals(20, updated.getSortOrder());
        verify(categoryMapper).updateById(existing);
    }

    @Test
    void updateRejectsSiblingNameConflict() {
        Category existing = new Category();
        existing.setId(101L);
        existing.setParentId(1L);
        existing.setName("成衣");
        when(categoryMapper.selectById(101L)).thenReturn(existing);
        when(categoryMapper.selectCount(any())).thenReturn(1L);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.update(101L, "配饰", 20));

        assertEquals("同级分类名称已存在：配饰", ex.getMessage());
    }
}
