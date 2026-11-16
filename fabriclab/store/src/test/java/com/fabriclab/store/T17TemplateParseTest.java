package com.fabriclab.store;

import com.fabriclab.store.service.SeriesService;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 模板级校验：面料档案与工艺说明在四章齐全时能渲染出材质实拍，
 * 中英两版都不缺图、不缺文案键。不启动 Spring 上下文，也不连数据库。
 */
class T17TemplateParseTest {

    private SpringTemplateEngine engine() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setCharacterEncoding("UTF-8");
        resolver.setCacheable(false);

        ResourceBundleMessageSource messages = new ResourceBundleMessageSource();
        messages.setBasename("messages");
        messages.setDefaultEncoding("UTF-8");
        messages.setFallbackToSystemLocale(false);

        SpringTemplateEngine engine = new SpringTemplateEngine();
        engine.setTemplateResolver(resolver);
        engine.setTemplateEngineMessageSource(messages);
        /* 不启动 Web 容器：链接按根路径展开，与 8080 上的实际输出一致 */
        engine.setLinkBuilder(new org.thymeleaf.linkbuilder.ILinkBuilder() {
            @Override
            public String getName() {
                return "root-relative";
            }

            @Override
            public Integer getOrder() {
                return 0;
            }

            @Override
            public String buildLink(org.thymeleaf.context.IExpressionContext context, String base,
                                    java.util.Map<String, Object> parameters) {
                if (parameters == null || parameters.isEmpty()) {
                    return base;
                }
                StringBuilder sb = new StringBuilder(base).append('?');
                parameters.forEach((k, v) -> sb.append(k).append('=').append(v).append('&'));
                return sb.deleteCharAt(sb.length() - 1).toString();
            }
        });
        return engine;
    }

    private Context context(Locale locale, boolean withGroups) {
        Context ctx = new Context(locale);
        ctx.setVariable("lang", Locale.ENGLISH.equals(locale) ? "en" : "zh");
        ctx.setVariable("samples", List.of());
        ctx.setVariable("groups", withGroups ? List.of(
                new SeriesService.FabricGroup("fabrics.silk.t", "fabrics.silk.d", "care.silk", List.of()),
                new SeriesService.FabricGroup("fabrics.wool.t", "fabrics.wool.d", "care.wool", List.of()),
                new SeriesService.FabricGroup("fabrics.cotton.t", "fabrics.cotton.d", "care.cotton", List.of()),
                new SeriesService.FabricGroup("fabrics.other.t", "fabrics.other.d", "care.other", List.of())
        ) : List.of());
        return ctx;
    }

    private int count(String haystack, String needle) {
        int n = 0;
        int i = haystack.indexOf(needle);
        while (i >= 0) {
            n++;
            i = haystack.indexOf(needle, i + needle.length());
        }
        return n;
    }

    @Test
    void fabricsRenderTwoPhotosPerChapter() {
        for (Locale locale : List.of(Locale.SIMPLIFIED_CHINESE, Locale.ENGLISH)) {
            String html = engine().process("fabrics", context(locale, true));
            assertEquals(8, count(html, "/images/fibres/"), "每章两张，共八张：" + locale);
            assertTrue(html.contains("silk-01.jpg"), "丝：本色缎面");
            assertTrue(html.contains("wool-02.jpg"), "毛：粗纺羊毛");
            assertTrue(html.contains("cotton-01.jpg"), "棉：原棉");
            assertTrue(html.contains("linen-01.jpg"), "麻：亚麻平纹");
            assertTrue(html.contains("other-01.jpg"), "其他纤维：帆布");
            assertTrue(html.contains("fa-material__frame"), "图片走材质实拍容器");
            assertTrue(!html.contains("??"), "不得出现未解析的文案键：" + locale);
        }
    }

    @Test
    void craftsRenderFourStepsWithFigureAndCopy() {
        for (Locale locale : List.of(Locale.SIMPLIFIED_CHINESE, Locale.ENGLISH)) {
            String html = engine().process("crafts", context(locale, false));
            assertEquals(4, count(html, "fa-chapter__inner"), "四道工序：" + locale);
            assertEquals(4, count(html, "class=\"cr-figure fa-reveal\""), "每道一张实拍：" + locale);
            assertTrue(html.contains("/images/fibres/cotton-02.jpg"), "第一步：布面");
            assertTrue(html.contains("/images/fibres/linen-01.jpg"), "第二步：织纹");
            assertTrue(html.contains("/images/look/lb-07.jpg"), "第三步：版型");
            assertTrue(html.contains("/images/look/lb-03.jpg"), "第四步：缝合");
            assertTrue(html.contains("fa-facts"), "每道三道事实卡");
            assertTrue(html.contains("/category"), "页尾回选购页");
            assertTrue(!html.contains("??"), "不得出现未解析的文案键：" + locale);
        }
    }
}
