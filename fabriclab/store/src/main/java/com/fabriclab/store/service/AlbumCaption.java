package com.fabriclab.store.service;

/**
 * 后台相册把图类型与来源许可写进 {@code product_image.alt_text}（格式 {@code F|来源|替代文本}）。
 * 用户端只取替代文本，避免把编码写进 img 的 alt；不含前缀的历史数据原样返回。
 */
public final class AlbumCaption {

    private AlbumCaption() { }

    public static String alt(String raw) {
        if (raw == null) return "";
        if (raw.length() >= 2 && raw.charAt(1) == '|') {
            char kind = raw.charAt(0);
            if (kind == 'F' || kind == 'W' || kind == 'D') {
                int second = raw.indexOf('|', 2);
                return second < 0 ? "" : raw.substring(second + 1);
            }
        }
        return raw;
    }
}
