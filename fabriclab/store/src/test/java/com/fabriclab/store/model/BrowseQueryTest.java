package com.fabriclab.store.model;

import com.fabriclab.store.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 列表查询参数解析：页码归一、页大小白名单、关键字长度与裁剪、类型与排序字面值。 */
class BrowseQueryTest {

    @Test
    void blankParamsFallBackToDefaults() {
        BrowseQuery query = BrowseQuery.parse(null, null, null, null, null, null);
        assertNull(query.categoryId());
        assertEquals(CategoryType.ALL, query.productType());
        assertFalse(query.productTypeExplicit());
        assertEquals(SortOption.DEFAULT, query.sort());
        assertNull(query.keyword());
        assertEquals(1, query.page());
        assertEquals(6, query.size());
        assertNull(query.productTypeParam());
    }

    @Test
    void smallPageNumbersNormalizeToOne() {
        assertEquals(1, BrowseQuery.parse(null, null, null, null, "0", null).page());
        assertEquals(1, BrowseQuery.parse(null, null, null, null, "-3", null).page());
    }

    @Test
    void pageAndSizeRejectNonNumericAndOutOfRange() {
        assertThrows(BusinessException.class, () -> BrowseQuery.parse(null, null, null, null, "abc", null));
        assertThrows(BusinessException.class, () -> BrowseQuery.parse(null, null, null, null, "1.5", null));
        assertThrows(BusinessException.class, () -> BrowseQuery.parse(null, null, null, null, "9999999999", null));
        assertThrows(BusinessException.class, () -> BrowseQuery.parse(null, null, null, null, "99999999999999999999", null));
        assertThrows(BusinessException.class, () -> BrowseQuery.parse(null, null, null, null, null, "0"));
        assertThrows(BusinessException.class, () -> BrowseQuery.parse(null, null, null, null, null, "51"));
        assertThrows(BusinessException.class, () -> BrowseQuery.parse(null, null, null, null, null, "6.5"));
        assertEquals(50, BrowseQuery.parse(null, null, null, null, null, "50").size());
        assertEquals(1, BrowseQuery.parse(null, null, null, null, null, "1").size());
    }

    @Test
    void keywordIsTrimmedAndLengthCappedWithoutTruncation() {
        assertEquals("亚麻衬衫", BrowseQuery.parse(null, null, null, " 亚麻衬衫 ", null, null).keyword());
        assertNull(BrowseQuery.parse(null, null, null, "   ", null, null).keyword());
        assertEquals(120, BrowseQuery.parse(null, null, null, "衣".repeat(120), null, null).keyword().length());
        assertThrows(BusinessException.class,
                () -> BrowseQuery.parse(null, null, null, "衣".repeat(121), null, null));
    }

    @Test
    void categoryMustBeNumericId() {
        assertEquals(12L, BrowseQuery.parse("12", null, null, null, null, null).categoryId());
        assertNull(BrowseQuery.parse("", null, null, null, null, null).categoryId());
        assertThrows(BusinessException.class, () -> BrowseQuery.parse("1.5", null, null, null, null, null));
        assertThrows(BusinessException.class, () -> BrowseQuery.parse("abc", null, null, null, null, null));
    }

    @Test
    void productTypeAndSortAcceptWhitelistOnly() {
        assertEquals(CategoryType.GARMENTS,
                BrowseQuery.parse(null, "GARMENTS", null, null, null, null).productType());
        assertTrue(BrowseQuery.parse(null, "ALL", null, null, null, null).productTypeExplicit());
        assertEquals("ALL", BrowseQuery.parse(null, "ALL", null, null, null, null).productTypeParam());
        assertEquals(SortOption.PRICE_DESC,
                BrowseQuery.parse(null, null, "PRICE_DESC", null, null, null).sort());
        assertThrows(BusinessException.class, () -> BrowseQuery.parse(null, "PETS", null, null, null, null));
        assertThrows(BusinessException.class, () -> BrowseQuery.parse(null, "garments", null, null, null, null));
        assertThrows(BusinessException.class, () -> BrowseQuery.parse(null, null, "SALES", null, null, null));
        assertThrows(BusinessException.class, () -> BrowseQuery.parse(null, null, "price_asc", null, null, null));
    }
}
