package com.fabriclab.admin.t14;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * 服装相册归属：列表与详情都在 /admin/albums 下，侧栏高亮「服装相册」，
 * 返回与表单提交不切回款号维护；款号维护的相册跳转页保持原有归属。
 */
@SpringBootTest
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "MYSQL_PASSWORD", matches = ".+")
class T14AlbumScopeTest {

    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;

    private static final String SKU = "FL-ALB-SCOPE-01";

    @BeforeEach void zh() { LocaleContextHolder.setLocale(Locale.SIMPLIFIED_CHINESE); }
    @AfterEach void reset() {
        LocaleContextHolder.resetLocaleContext();
        jdbc.update("DELETE FROM product_image WHERE product_id IN (SELECT id FROM product WHERE sku LIKE 'FL-ALB-SCOPE%')");
        jdbc.update("DELETE FROM product WHERE sku LIKE 'FL-ALB-SCOPE%'");
        jdbc.update("DELETE FROM category WHERE code LIKE 'ALBSCOPE%'");
    }

    private record Session(MockHttpSession session, String csrf) {}

    private Session login() throws Exception {
        MvcResult page = mvc.perform(get("/admin/signon")).andReturn();
        String csrf = (String) page.getModelAndView().getModel().get("csrfToken");
        MockHttpSession s = (MockHttpSession) page.getRequest().getSession();
        MvcResult ok = mvc.perform(post("/admin/signon").session(s)
                .param("username", "admin").param("password", "Passw0rd!2026")
                .param("_csrf", csrf)).andReturn();
        assertEquals(302, ok.getResponse().getStatus());
        return new Session((MockHttpSession) ok.getRequest().getSession(), csrf);
    }

    private long product() {
        jdbc.update("INSERT INTO category (code, name, parent_id, sort_order) VALUES (?, ?, NULL, 93)",
                "ALBSCOPE-TOP", "相册归属一级");
        Long top = jdbc.queryForObject("SELECT id FROM category WHERE code = ?", Long.class, "ALBSCOPE-TOP");
        jdbc.update("INSERT INTO category (code, name, parent_id, sort_order) VALUES (?, ?, ?, 93)",
                "ALBSCOPE-SUB", "相册归属二级", top);
        Long sub = jdbc.queryForObject("SELECT id FROM category WHERE code = ?", Long.class, "ALBSCOPE-SUB");
        jdbc.update("INSERT INTO product (sku, category_id, name, price, stock, status, cover_image, sort_order) "
                + "VALUES (?, ?, '相册归属衬衫', 480.00, 8, 'ON_SALE', 'images/logo.png', 4)", SKU, sub);
        return jdbc.queryForObject("SELECT id FROM product WHERE sku = ?", Long.class, SKU);
    }

    private String html(String url, Session s) throws Exception {
        MvcResult r = mvc.perform(get(url).session(s.session())).andReturn();
        assertEquals(200, r.getResponse().getStatus(), url + " 应可访问");
        return r.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    @Test
    void albumIndexOpensAlbumModuleDetail() throws Exception {
        long id = product();
        Session s = login();
        String html = html("/admin/albums", s);
        assertTrue(html.contains("/admin/albums/" + id), "列表的「查看相册」必须指向相册模块");
        assertFalse(html.contains("/admin/products/" + id + "/album"), "列表不得把详情挂回款号维护");
    }

    @Test
    void albumDetailBelongsToAlbumModule() throws Exception {
        long id = product();
        Session s = login();
        String html = html("/admin/albums/" + id, s);
        assertTrue(html.matches("(?s).*<a[^>]*href=\"/admin/albums\"[^>]*aria-current=\"page\"[^>]*>.*"),
                "侧栏应高亮服装相册");
        assertTrue(html.contains("返回相册列表"));
        assertTrue(html.contains("href=\"/admin/albums\""), "返回按钮应回相册列表");
        assertTrue(html.contains("/admin/albums/" + id + "/upload"), "上传表单应留在相册模块");
        assertFalse(html.contains("/admin/products/" + id + "/album"), "详情页不得出现款号维护路径");
        assertTrue(html.contains("平铺图") && html.contains("上身图") && html.contains("细节图"));
    }

    @Test
    void albumPostsStayInAlbumModule() throws Exception {
        long id = product();
        Session s = login();
        MvcResult add = mvc.perform(post("/admin/albums/" + id).session(s.session())
                .param("_csrf", s.csrf()).param("kind", "F")
                .param("path", "images/products/FL-CM-01/1.jpg")
                .param("credit", "© FABRICLAB").param("alt", "正面平铺").param("sortOrder", "0")).andReturn();
        assertEquals(302, add.getResponse().getStatus());
        assertEquals("/admin/albums/" + id, add.getResponse().getRedirectedUrl());

        Long imageId = jdbc.queryForObject("SELECT id FROM product_image WHERE product_id = ?", Long.class, id);
        MvcResult cover = mvc.perform(post("/admin/albums/" + id + "/" + imageId + "/cover")
                .session(s.session()).param("_csrf", s.csrf())).andReturn();
        assertEquals("/admin/albums/" + id, cover.getResponse().getRedirectedUrl());
        MvcResult del = mvc.perform(post("/admin/albums/" + id + "/" + imageId + "/delete")
                .session(s.session()).param("_csrf", s.csrf())).andReturn();
        assertEquals("/admin/albums/" + id, del.getResponse().getRedirectedUrl());
    }

    @Test
    void styleJumpPageKeepsItsOwnScope() throws Exception {
        long id = product();
        Session s = login();
        String html = html("/admin/products/" + id + "/album", s);
        assertTrue(html.contains("返回款号"));
        assertTrue(html.contains("/admin/products/" + id + "/album/upload"));
        assertFalse(html.contains("返回相册列表"));

        MvcResult add = mvc.perform(post("/admin/products/" + id + "/album").session(s.session())
                .param("_csrf", s.csrf()).param("kind", "F")
                .param("path", "images/products/FL-CM-01/1.jpg").param("sortOrder", "0")).andReturn();
        assertEquals("/admin/products/" + id + "/album", add.getResponse().getRedirectedUrl());
    }
}
