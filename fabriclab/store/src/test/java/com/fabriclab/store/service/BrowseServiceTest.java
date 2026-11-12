package com.fabriclab.store.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fabriclab.store.entity.Category;
import com.fabriclab.store.entity.Product;
import com.fabriclab.store.exception.NotFoundException;
import com.fabriclab.store.mapper.CategoryMapper;
import com.fabriclab.store.mapper.ProductMapper;
import com.fabriclab.store.model.BrowseQuery;
import com.fabriclab.store.model.CategoryType;
import com.fabriclab.store.service.BrowseService.CategoryScope;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 浏览服务：分类作用域、二级入口类型切换升级、关键字通配符转义、首页两栏上限。 */
@ExtendWith(MockitoExtension.class)
class BrowseServiceTest {

    @Mock
    private ProductMapper productMapper;

    @Mock
    private CategoryMapper categoryMapper;

    @InjectMocks
    private BrowseService service;

    private Category line(long id, String code) {
        Category category = new Category();
        category.setId(id);
        category.setCode(code);
        category.setParentId(null);
        category.setName("系列线" + id);
        return category;
    }

    private Category child(long id, long parentId, String code) {
        Category category = new Category();
        category.setId(id);
        category.setCode(code);
        category.setParentId(parentId);
        category.setName("二级分类" + id);
        return category;
    }

    @Test
    void secondLevelEntryDefaultsToOwnScopeWithTypeSelected() {
        when(categoryMapper.selectById(101L)).thenReturn(child(101, 1, "COMMUTE_GAR"));
        when(categoryMapper.selectById(1L)).thenReturn(line(1, "COMMUTE"));

        CategoryScope scope = service.resolveScope(BrowseQuery.parse("101", null, null, null, null, null));

        assertTrue(scope.entryIsSecondLevel());
        assertEquals(CategoryType.GARMENTS, scope.selectedType());
        assertEquals(List.of(101L), scope.categoryIds());
        assertEquals(1L, scope.selectedLine().getId());
    }

    @Test
    void secondLevelEntryWithSameTypeStaysInsideSecondLevel() {
        when(categoryMapper.selectById(101L)).thenReturn(child(101, 1, "COMMUTE_GAR"));
        when(categoryMapper.selectById(1L)).thenReturn(line(1, "COMMUTE"));

        CategoryScope scope = service.resolveScope(BrowseQuery.parse("101", "GARMENTS", null, null, null, null));

        assertEquals(List.of(101L), scope.categoryIds());
    }

    @Test
    void secondLevelEntrySwitchingTypeUpgradesToLineScope() {
        when(categoryMapper.selectById(101L)).thenReturn(child(101, 1, "COMMUTE_GAR"));
        when(categoryMapper.selectById(1L)).thenReturn(line(1, "COMMUTE"));
        when(categoryMapper.selectList(any())).thenReturn(List.of(
                child(101, 1, "COMMUTE_GAR"), child(102, 1, "COMMUTE_ACC")));

        CategoryScope all = service.resolveScope(BrowseQuery.parse("101", "ALL", null, null, null, null));
        assertEquals(List.of(101L, 102L), all.categoryIds());
        assertEquals(CategoryType.ALL, all.selectedType());

        CategoryScope accessories = service.resolveScope(
                BrowseQuery.parse("101", "ACCESSORIES", null, null, null, null));
        assertEquals(List.of(102L), accessories.categoryIds());
        assertEquals(CategoryType.ACCESSORIES, accessories.selectedType());
    }

    @Test
    void firstLevelEntryFiltersChildrenByType() {
        when(categoryMapper.selectById(1L)).thenReturn(line(1, "COMMUTE"));
        when(categoryMapper.selectList(any())).thenReturn(List.of(
                child(101, 1, "COMMUTE_GAR"), child(102, 1, "COMMUTE_ACC")));

        CategoryScope all = service.resolveScope(BrowseQuery.parse("1", "ALL", null, null, null, null));
        assertEquals(List.of(101L, 102L), all.categoryIds());
        assertEquals(1L, all.selectedLine().getId());
        assertFalse(all.entryIsSecondLevel());

        CategoryScope garments = service.resolveScope(BrowseQuery.parse("1", "GARMENTS", null, null, null, null));
        assertEquals(List.of(101L), garments.categoryIds());
    }

    @Test
    void noCategoryWithTypeFiltersAcrossAllLines() {
        when(categoryMapper.selectList(any())).thenReturn(List.of(
                child(101, 1, "COMMUTE_GAR"), child(102, 1, "COMMUTE_ACC"),
                child(502, 5, "HOME_ACC")));

        CategoryScope scope = service.resolveScope(BrowseQuery.parse(null, "GARMENTS", null, null, null, null));

        assertEquals(List.of(101L), scope.categoryIds());
        assertNull(scope.selectedLine());
    }

    @Test
    void missingCategoryIsNotFound() {
        when(categoryMapper.selectById(9999L)).thenReturn(null);

        assertThrows(NotFoundException.class,
                () -> service.resolveScope(BrowseQuery.parse("9999", null, null, null, null, null)));
    }

    @Test
    void keywordWildcardsAreEscapedAsLiteralCharacters() {
        assertEquals("a\\%b\\_c\\\\d", BrowseService.escapeLike("a%b_c\\d"));
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void browseAppliesOnSaleFilterEscapedKeywordAndPagination() {
        when(categoryMapper.selectById(101L)).thenReturn(child(101, 1, "COMMUTE_GAR"));
        when(categoryMapper.selectById(1L)).thenReturn(line(1, "COMMUTE"));
        when(productMapper.selectPage(any(), any())).thenAnswer(inv -> new Page<Product>());

        BrowseService.BrowseResult result = service.browse(
                BrowseQuery.parse("101", null, null, "50%OFF_衫", "2", "10"));

        ArgumentCaptor<Page<Product>> pageCaptor = ArgumentCaptor.forClass(Page.class);
        ArgumentCaptor<LambdaQueryWrapper<Product>> wrapperCaptor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(productMapper).selectPage(pageCaptor.capture(), wrapperCaptor.capture());
        assertEquals(2, pageCaptor.getValue().getCurrent());
        assertEquals(10, pageCaptor.getValue().getSize());
        assertTrue(wrapperCaptor.getValue().getSqlSegment().contains("LIKE"));
        assertTrue(wrapperCaptor.getValue().getParamNameValuePairs().containsValue("50\\%OFF\\_衫"));
        assertEquals(List.of(101L), result.scope().categoryIds());
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void homeViewCapsNewArrivalsAtThreeAndFeaturedAtFour() {
        when(productMapper.selectPage(any(), any())).thenAnswer(inv -> new Page<Product>());
        when(categoryMapper.selectList(any())).thenReturn(List.of());

        BrowseService.HomeView home = service.homeView();

        ArgumentCaptor<Page<Product>> pages = ArgumentCaptor.forClass(Page.class);
        verify(productMapper, times(2)).selectPage(pages.capture(), any());
        assertEquals(3, pages.getAllValues().get(0).getSize());
        assertEquals(4, pages.getAllValues().get(1).getSize());
        assertTrue(home.newArrivals().isEmpty());
        assertTrue(home.featured().isEmpty());
        assertTrue(home.lines().isEmpty());
    }
}
