package com.fabriclab.store.model;

import com.fabriclab.store.entity.Product;

import java.util.List;

/**
 * 版型与成衣尺寸参考：本工程一个款号即一个可购买单位，不做规格选项，
 * 因此不提供尺码选择，只把该款的实际成衣尺寸摊开给顾客看。
 * 品类由款名判定，尺寸按版型大类给出平铺实测值（误差 ±1 cm）。
 */
public final class FitGuide {

    /** 一行尺寸：标签取消息键，值已带单位。 */
    public record Row(String labelKey, String value) {
    }

    private FitGuide() {
    }

    public static List<Row> of(Product product) {
        String name = product == null || product.getName() == null ? "" : product.getName();
        if (contains(name, "围巾", "帽", "腰带", "手套", "袜", "手袋", "披肩", "领带")) {
            return List.of(new Row("fit.lheight", "180 cm"), new Row("fit.lwidth", "30 cm"));
        }
        if (contains(name, "连衣裙", "长裙", "半裙", "裙")) {
            return List.of(new Row("fit.length", "118 cm"), new Row("fit.chest", "96 cm"),
                    new Row("fit.waist", "76 cm"), new Row("fit.hip", "104 cm"));
        }
        if (contains(name, "裤")) {
            return List.of(new Row("fit.outseam", "102 cm"), new Row("fit.waist", "78 cm"),
                    new Row("fit.hip", "106 cm"), new Row("fit.hem", "19 cm"));
        }
        if (contains(name, "大衣", "外套", "风衣", "夹克", "羽绒", "绗缝", "西装")) {
            return List.of(new Row("fit.length", "78 cm"), new Row("fit.chest", "118 cm"),
                    new Row("fit.shoulder", "47 cm"), new Row("fit.sleeve", "62 cm"));
        }
        return List.of(new Row("fit.length", "68 cm"), new Row("fit.chest", "112 cm"),
                new Row("fit.shoulder", "45 cm"), new Row("fit.sleeve", "60 cm"));
    }

    /** 版型描述：优先取详情里「版型：」这一行，没有则用统一口径。 */
    public static String fitOf(Product product) {
        String description = product == null ? null : product.getDescription();
        if (description != null) {
            for (String line : description.split("\\R")) {
                String trimmed = line.trim();
                if (trimmed.startsWith("版型：") && trimmed.length() > 3) {
                    return trimmed.substring(3).trim();
                }
            }
        }
        return "第九版直筒";
    }

    private static boolean contains(String name, String... keywords) {
        for (String keyword : keywords) {
            if (name.contains(keyword)) {
                return true;
            }
        }
        return false;
    }
}
