package com.fabriclab.store.model;

import com.fabriclab.store.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** 类型三值由分类编码后缀映射，不新增数据库字段。 */
class CategoryTypeTest {

    @Test
    void garmentSuffixMapsToGarments() {
        assertEquals(CategoryType.GARMENTS, CategoryType.fromCode("COMMUTE_GAR"));
        assertEquals(CategoryType.GARMENTS, CategoryType.fromCode("outdoor_gar"));
    }

    @Test
    void accessorySuffixMapsToAccessories() {
        assertEquals(CategoryType.ACCESSORIES, CategoryType.fromCode("COMMUTE_ACC"));
    }

    @Test
    void anyOtherCodeMapsToAll() {
        assertEquals(CategoryType.ALL, CategoryType.fromCode("COMMUTE"));
        assertEquals(CategoryType.ALL, CategoryType.fromCode("OCCASION_FR"));
    }

    @Test
    void blankCodeRejected() {
        org.junit.jupiter.api.Assertions.assertThrows(BusinessException.class, () -> CategoryType.fromCode(null));
        org.junit.jupiter.api.Assertions.assertThrows(BusinessException.class, () -> CategoryType.fromCode("  "));
    }
}
