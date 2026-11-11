package com.fabriclab.store.model;

import com.fabriclab.store.exception.BusinessException;

import java.util.Locale;

/**
 * 分类类型三值：ALL / GARMENTS / ACCESSORIES。
 * 由分类编码后缀映射，不在数据库新增字段：*_GAR 为成衣，*_ACC 为配饰，其余为全部。
 */
public enum CategoryType {

    ALL, GARMENTS, ACCESSORIES;

    private static final String GARMENT_SUFFIX = "_GAR";
    private static final String ACCESSORY_SUFFIX = "_ACC";

    public static CategoryType fromCode(String categoryCode) {
        if (categoryCode == null || categoryCode.isBlank()) {
            throw new BusinessException("分类编码不能为空");
        }
        String code = categoryCode.trim().toUpperCase(Locale.ROOT);
        if (code.endsWith(GARMENT_SUFFIX)) {
            return GARMENTS;
        }
        if (code.endsWith(ACCESSORY_SUFFIX)) {
            return ACCESSORIES;
        }
        return ALL;
    }
}
