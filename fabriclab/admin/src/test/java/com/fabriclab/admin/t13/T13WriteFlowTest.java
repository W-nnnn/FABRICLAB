package com.fabriclab.admin.t13;

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

/** 写操作链路：CSRF、PRG、失败重渲保留输入、访问检查、上下架与删除反馈。 */
@SpringBootTest
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "MYSQL_PASSWORD", matches = ".+")
class T13WriteFlowTest {

    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;

    private static final String TOP = "T13WTOP";
    private static final String SUB = "T13WSUB";
    private static final String SKU = "FL-T13-WEB-01";

    @BeforeEach void zh() { LocaleContextHolder.setLocale(Locale.SIMPLIFIED_CHINESE); }
    @AfterEach void reset() {
        LocaleContextHolder.resetLocaleContext();
        jdbc.update("DELETE FROM product WHERE sku LIKE 'FL-T13%'");
        jdbc.update("DELETE FROM category WHERE code LIKE 'T13W%'");
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

    @Test
    void anonymousCannotReachCatalogPages() throws Exception {
        for (String path : new String[]{"/admin/categories", "/admin/categories/new",
                "/admin/products", "/admin/products/new", "/admin/products/1/edit"}) {
            MvcResult r = mvc.perform(get(path)).andReturn();
            assertEquals(302, r.getResponse().getStatus(), path);
            assertTrue(r.getResponse().getRedirectedUrl().startsWith("/admin/signon"));
        }
    }

    @Test
    void writesWithoutCsrfAreRejected() throws Exception {
        Session s = login();
        MvcResult r = mvc.perform(post("/admin/categories").session(s.session())
                .param("code", TOP).param("name", "缺令牌").param("sortOrder", "1")).andReturn();
        assertEquals(403, r.getResponse().getStatus());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM category WHERE code = ?",
                Integer.class, TOP));
    }

    @Test
    void createCategoryUsesPrgAndShowsFlashNotice() throws Exception {
        Session s = login();
        MvcResult created = mvc.perform(post("/admin/categories").session(s.session())
                .param("_csrf", s.csrf()).param("code", TOP).param("name", "T13 表单一级")
                .param("sortOrder", "97")).andReturn();
        assertEquals(302, created.getResponse().getStatus());
        assertEquals("/admin/categories", created.getResponse().getRedirectedUrl());
        MvcResult page = mvc.perform(get("/admin/categories").session(s.session())).andReturn();
        String html = page.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertTrue(html.contains("分类已创建"), "应呈现一次性成功提示");
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM category WHERE code = ?",
                Integer.class, TOP));
    }

    @Test
    void invalidPriceRerendersFormAndKeepsInput() throws Exception {
        Session s = login();
        mvc.perform(post("/admin/categories").session(s.session()).param("_csrf", s.csrf())
                .param("code", TOP).param("name", "T13 表单一级").param("sortOrder", "97"));
        Long subId = jdbc.queryForObject("SELECT id FROM category WHERE code = ?", Long.class, TOP);
        MvcResult r = mvc.perform(post("/admin/products").session(s.session())
                .param("_csrf", s.csrf()).param("sku", SKU)
                .param("categoryId", String.valueOf(subId))
                .param("name", "保留下来的名称").param("price", "0")
                .param("status", "DRAFT").param("coverImage", "images/logo.png")
                .param("sortOrder", "1").param("stock", "5")).andReturn();
        assertEquals(200, r.getResponse().getStatus(), "失败应重渲表单而非错误页");
        String html = r.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertTrue(html.contains("保留下来的名称"), "失败重渲必须保留输入");
        assertTrue(html.contains("价格"), "应呈现具体原因");
    }

    @Test
    void createPublishAndDeleteThroughHttp() throws Exception {
        Session s = login();
        mvc.perform(post("/admin/categories").session(s.session()).param("_csrf", s.csrf())
                .param("code", TOP).param("name", "T13 一级").param("sortOrder", "97"));
        Long topId = jdbc.queryForObject("SELECT id FROM category WHERE code = ?", Long.class, TOP);
        mvc.perform(post("/admin/categories").session(s.session()).param("_csrf", s.csrf())
                .param("code", SUB).param("parentId", String.valueOf(topId))
                .param("name", "T13 二级").param("sortOrder", "97"));
        Long subId = jdbc.queryForObject("SELECT id FROM category WHERE code = ?", Long.class, SUB);

        MvcResult created = mvc.perform(post("/admin/products").session(s.session())
                .param("_csrf", s.csrf()).param("sku", SKU)
                .param("categoryId", String.valueOf(subId)).param("name", "T13 网页款")
                .param("price", "680.00").param("status", "ON_SALE")
                .param("coverImage", "images/logo.png").param("sortOrder", "1")
                .param("stock", "6").param("fabric", "山羊绒 100%")
                .param("craftNote", "包边缝合").param("version", "0")).andReturn();
        assertEquals(302, created.getResponse().getStatus());
        Long id = jdbc.queryForObject("SELECT id FROM product WHERE sku = ?", Long.class, SKU);
        assertEquals("ON_SALE", jdbc.queryForObject("SELECT status FROM product WHERE id = ?",
                String.class, id));

        mvc.perform(post("/admin/products/" + id + "/status").session(s.session())
                .param("_csrf", s.csrf()).param("target", "DRAFT"));
        assertEquals("DRAFT", jdbc.queryForObject("SELECT status FROM product WHERE id = ?",
                String.class, id));

        mvc.perform(post("/admin/products/" + id + "/delete").session(s.session())
                .param("_csrf", s.csrf()));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM product WHERE id = ?",
                Integer.class, id));
    }
}
