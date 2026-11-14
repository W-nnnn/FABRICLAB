package com.fabriclab.store.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** 后台相册把图类型与来源编在 alt_text 里，用户端只取替代文本。 */
class AlbumCaptionTest {

    @Test
    void stripsKindAndCreditPrefix() {
        assertEquals("正面穿着", AlbumCaption.alt("W|© FABRICLAB|正面穿着"));
        assertEquals("平铺背面", AlbumCaption.alt("F||平铺背面"));
        assertEquals("", AlbumCaption.alt("D||"));
    }

    @Test
    void keepsLegacyTextAndNulls() {
        assertEquals("羊毛套头衫 · 深灰", AlbumCaption.alt("羊毛套头衫 · 深灰"));
        assertEquals("", AlbumCaption.alt(null));
        assertEquals("X|A|B", AlbumCaption.alt("X|A|B"), "非法类型前缀按原文保留");
    }
}
