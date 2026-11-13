package com.fabriclab.store.config;

import org.springframework.context.i18n.LocaleContextHolder;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 数据库内容（分类名、商品名、简介、详情等种子数据中文名）的英文展示翻译。
 * 中文 locale 原样返回，英文 locale 查词典，未命中再走正则规则，最后回退原文。
 * 词典来自 classpath:content-en.properties（UTF-8 读取，键为原始中文串）。
 * 无状态、无依赖：由 CurrentUserAdvice 直接实例化并注入模板模型（tr），
 * 不注册为 Spring Bean，保证 @WebMvcTest 切片同样可用。
 */
public class ContentI18n {

    private final Map<String, String> dict = new HashMap<>();
    private final Map<Pattern, String> rules = new HashMap<>();

    public ContentI18n() {
        try (InputStream in = ContentI18n.class.getResourceAsStream("/content-en.properties")) {
            if (in != null) {
                java.util.Properties props = new java.util.Properties();
                props.load(new InputStreamReader(in, StandardCharsets.UTF_8));
                props.forEach((k, v) -> dict.put(String.valueOf(k), String.valueOf(v)));
            }
        } catch (Exception ignored) {
            // 词典缺失时全部回退原文
        }
        // 带可变数字的消息模板
        rules.put(Pattern.compile("^库存不足，仅剩 (\\d+) 件$"), "Only $1 left in stock");
        rules.put(Pattern.compile("^库存不足，当前可购 (\\d+).*$"), "Insufficient stock — up to $1 available");
        rules.put(Pattern.compile("^单款数量最多 (\\d+).*$"), "Maximum quantity per SKU is $1");
        rules.put(Pattern.compile("^数量必须在 (\\d+)[^\\d]*(\\d+).*$"), "Quantity must be between $1 and $2");
        rules.put(Pattern.compile("^用户名最长 (\\d+) 个字符$"), "Username must be at most $1 characters");
        rules.put(Pattern.compile("^密码长度必须为 (\\d+).*$"), "Password must be at least $1 characters");
        rules.put(Pattern.compile("^搜索关键字最长 (\\d+).*$"), "Search keyword must be at most $1 characters");
        rules.put(Pattern.compile("^商品名称超过 (\\d+).*$"), "Product name must be at most $1 characters");
        rules.put(Pattern.compile("^简介超过 (\\d+).*$"), "Summary must be at most $1 characters");
    }

    /** 模板入口：按当前 locale 翻译数据库中文名。 */
    public String t(String text) {
        if (text == null || text.isBlank()) {
            return text;
        }
        if (!LocaleContextHolder.getLocale().getLanguage().startsWith("en")) {
            return text;
        }
        String hit = dict.get(text);
        if (hit != null) {
            return hit;
        }
        for (Map.Entry<Pattern, String> rule : rules.entrySet()) {
            java.util.regex.Matcher m = rule.getKey().matcher(text);
            if (m.find()) {
                return m.replaceAll(rule.getValue());
            }
        }
        return text;
    }
}
