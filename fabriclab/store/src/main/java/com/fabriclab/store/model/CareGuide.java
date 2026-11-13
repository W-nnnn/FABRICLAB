package com.fabriclab.store.model;

import com.fabriclab.store.entity.Product;

/**
 * 洗护建议：按详情里的面料描述归纳到四类纤维，返回对应文案键。
 * 只做品类级建议，不替代成衣洗标；顺序按纤维关键字先细后粗，避免「羊绒」被「棉」误判。
 */
public final class CareGuide {

    public static final String KEY_SILK = "care.silk";
    public static final String KEY_WOOL = "care.wool";
    public static final String KEY_COTTON = "care.cotton";
    public static final String KEY_OTHER = "care.other";

    private CareGuide() {
    }

    public static String keyOf(Product product) {
        String description = product == null || product.getDescription() == null ? "" : product.getDescription();
        if (contains(description, "丝", "缎", "绸")) {
            return KEY_SILK;
        }
        if (contains(description, "羊绒", "羊毛", "驼绒", "羊驼", "毛呢", "呢")) {
            return KEY_WOOL;
        }
        if (contains(description, "棉", "麻")) {
            return KEY_COTTON;
        }
        return KEY_OTHER;
    }

    private static boolean contains(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }
}
