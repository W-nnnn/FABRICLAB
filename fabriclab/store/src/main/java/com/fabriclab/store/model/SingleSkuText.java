package com.fabriclab.store.model;

import com.fabriclab.store.exception.BusinessException;

/**
 * 单 SKU 口径的文本落点工具：尺码、配色并入商品名称；
 * 面料、克重、版型、工艺以纯文本行写入简介与详情，展示时由模板统一转义。
 */
public final class SingleSkuText {

    public static final int NAME_MAX = 120;
    public static final int SUMMARY_MAX = 500;

    private SingleSkuText() {
    }

    /** 把尺码、配色并入基础名称，形成单 SKU 展示名，如「亚麻通勤衬衫（M / 雾蓝）」。 */
    public static String variantName(String baseName, String size, String color) {
        String base = requireText(baseName, "商品名称不能为空");
        StringBuilder variant = new StringBuilder();
        if (size != null && !size.isBlank()) {
            variant.append(size.trim());
        }
        if (color != null && !color.isBlank()) {
            if (variant.length() > 0) {
                variant.append(" / ");
            }
            variant.append(color.trim());
        }
        String name = variant.length() == 0 ? base : base + "（" + variant + "）";
        if (name.length() > NAME_MAX) {
            throw new BusinessException("商品名称超过 " + NAME_MAX + " 字符");
        }
        return name;
    }

    /** 面料档案：多行纯文本，供简介与详情承载，空项跳过。 */
    public static String fabricProfile(String fabric, String weight, String fit, String craft) {
        StringBuilder sb = new StringBuilder();
        append(sb, "面料", fabric);
        append(sb, "克重", weight);
        append(sb, "版型", fit);
        append(sb, "工艺", craft);
        int len = sb.length();
        return len == 0 ? "" : sb.substring(0, len - 1);
    }

    /** 裁剪纯文本简介并校验长度，null 视为无简介。 */
    public static String summary(String summary) {
        if (summary == null || summary.isBlank()) {
            return null;
        }
        String trimmed = summary.trim();
        if (trimmed.length() > SUMMARY_MAX) {
            throw new BusinessException("简介超过 " + SUMMARY_MAX + " 字符");
        }
        return trimmed;
    }

    private static void append(StringBuilder sb, String label, String value) {
        if (value != null && !value.isBlank()) {
            sb.append(label).append("：").append(value.trim()).append('\n');
        }
    }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(message);
        }
        return value.trim();
    }
}
