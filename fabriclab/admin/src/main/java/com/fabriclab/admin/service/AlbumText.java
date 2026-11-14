package com.fabriclab.admin.service;

/**
 * 相册条目的图类型与来源许可编解码。
 * DDL 的 product_image 只有 alt_text 一列可用，因此把它写成 {@code F|来源|替代文本}：
 * 首段是图类型（F 平铺 / W 上身 / D 细节），第二段是来源许可，其余是替代文本。
 * 不带前缀的历史数据按「平铺图、无来源、原文即替代文本」解析，保证用户端旧数据仍可读。
 */
public final class AlbumText {

    public static final String FLAT = "F";
    public static final String WORN = "W";
    public static final String DETAIL = "D";

    /** alt_text 列宽 120：1 类型 + 1 分隔 + 48 来源 + 1 分隔 + 64 原文。 */
    public static final int MAX_CREDIT = 48;
    public static final int MAX_ALT = 64;
    private static final int MAX_COLUMN = 120;

    private AlbumText() { }

    public record Meta(String kind, String credit, String alt) { }

    public static Meta parse(String raw) {
        String value = raw == null ? "" : raw;
        if (value.length() >= 2 && value.charAt(1) == '|') {
            String kind = kind(value.substring(0, 1));
            if (kind != null) {
                int second = value.indexOf('|', 2);
                String credit = second < 0 ? value.substring(2) : value.substring(2, second);
                String alt = second < 0 ? "" : value.substring(second + 1);
                return new Meta(kind, credit, alt);
            }
        }
        return new Meta(FLAT, "", value);
    }

    public static String compose(String kind, String credit, String alt) {
        String k = kind(kind);
        if (k == null) k = FLAT;
        String c = cut(clean(credit), MAX_CREDIT);
        String a = cut(clean(alt), MAX_ALT);
        String value = k + "|" + c + "|" + a;
        return value.length() > MAX_COLUMN ? value.substring(0, MAX_COLUMN) : value;
    }

    /** 类型入参：接受 F/W/D，也接受界面上的完整单词。前后端同名走一套。 */
    public static String kind(String raw) {
        if (raw == null) return null;
        return switch (raw.trim().toUpperCase()) {
            case FLAT, "FLAT" -> FLAT;
            case WORN, "WORN" -> WORN;
            case DETAIL, "DETAIL" -> DETAIL;
            default -> null;
        };
    }

    public static boolean validKind(String raw) {
        return kind(raw) != null;
    }

    private static String clean(String raw) {
        return raw == null ? "" : raw.replace("|", " ").replace('\n', ' ').trim();
    }

    private static String cut(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }
}
