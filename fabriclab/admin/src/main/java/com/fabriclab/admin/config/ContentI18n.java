package com.fabriclab.admin.config;

import org.springframework.context.i18n.LocaleContextHolder;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * 商品数据的英文展示词典（分类名、款名、面料、摘要）。
 * 中文 locale 原样返回；英文 locale 查 classpath:content-en.properties，
 * 未命中回退原文。与用户端口径一致；编辑表单不经过此翻译，始终显示可编辑原文。
 * 无状态、无 Spring 依赖，切片测试也可直接使用。
 */
public class ContentI18n {

    private final Map<String, String> dict = new HashMap<>();

    public ContentI18n() {
        try (InputStream in = ContentI18n.class.getResourceAsStream("/content-en.properties")) {
            if (in != null) {
                java.util.Properties props = new java.util.Properties();
                props.load(new InputStreamReader(in, StandardCharsets.UTF_8));
                props.forEach((k, v) -> dict.put(String.valueOf(k), String.valueOf(v)));
            }
        } catch (Exception ignored) {
            // 词典缺失时回退原文
        }
    }

    public String t(String text) {
        if (text == null || text.isBlank()
                || !LocaleContextHolder.getLocale().getLanguage().startsWith("en")) {
            return text;
        }
        String hit = dict.get(text);
        return hit == null ? text : hit;
    }
}
