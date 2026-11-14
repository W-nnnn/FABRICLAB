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

/** T14 页面链路：相册三组与回退提示、补货入口与提示、端到端库存算式。 */
@SpringBootTest
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "MYSQL_PASSWORD", matches = ".+")
class T14PageTest {

    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;

    private static final String SKU = "FL-T14-WEB-01";

    @BeforeEach void zh() { LocaleContextHolder.setLocale(Locale.SIMPLIFIED_CHINESE); }
    @AfterEach void reset() {
        LocaleContextHolder.resetLocaleContext();
        jdbc.update("DELETE FROM product_image WHERE product_id IN (SELECT id FROM product WHERE sku LIKE 'FL-T14%')");
        jdbc.update("DELETE FROM product WHERE sku LIKE 'FL-T14%'");
        jdbc.update("DELETE FROM category WHERE code LIKE 'T14%'");
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
        jdbc.update("INSERT INTO category (code, name, parent_id, sort_order) VALUES (?, ?, NULL, 94)",
                "T14PTOP", "T14 页面一级");
        Long top = jdbc.queryForObject("SELECT id FROM category WHERE code = ?", Long.class, "T14PTOP");
        jdbc.update("INSERT INTO category (code, name, parent_id, sort_order) VALUES (?, ?, ?, 94)",
                "T14PSUB", "T14 页面二级", top);
        Long sub = jdbc.queryForObject("SELECT id FROM category WHERE code = ?", Long.class, "T14PSUB");
        jdbc.update("INSERT INTO product (sku, category_id, name, price, stock, status, cover_image, sort_order) "
                + "VALUES (?, ?, 'T14 羊毛西裤', 620.00, 10, 'ON_SALE', 'images/logo.png', 3)", SKU, sub);
        return jdbc.queryForObject("SELECT id FROM product WHERE sku = ?", Long.class, SKU);
    }

    @Test
    void albumPageShowsThreeGroupsAndFallbackNotice() throws Exception {
        long id = product();
        Session s = login();
        MvcResult r = mvc.perform(get("/admin/products/" + id + "/album").session(s.session())).andReturn();
        assertEquals(200, r.getResponse().getStatus());
        String html = r.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertTrue(html.contains("平铺图") && html.contains("上身图") && html.contains("细节图"));
        assertTrue(html.contains("这一组还没有图片"), "空组应出空槽");
        assertTrue(html.contains("相册为空时回退封面"), "空相册必须写明回退封面");
        assertTrue(html.contains("images/logo.png"), "回退提示应给出当前封面路径");
    }

    @Test
    void albumPageRendersCreditAndCoverAfterSet() throws Exception {
        long id = product();
        Session s = login();
        mvc.perform(post("/admin/products/" + id + "/album").session(s.session())
                .param("_csrf", s.csrf()).param("kind", "W")
                .param("path", "images/products/FL-CM-01/2.jpg")
                .param("credit", "© FABRICLAB").param("alt", "上身正面").param("sortOrder", "1"));
        Long imageId = jdbc.queryForObject("SELECT id FROM product_image WHERE product_id = ?",
                Long.class, id);
        MvcResult page = mvc.perform(get("/admin/products/" + id + "/album").session(s.session())).andReturn();
        String html = page.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertTrue(html.contains("© FABRICLAB"));
        assertTrue(html.contains("上身正面"));
        assertTrue(html.contains("设为封面"));

        mvc.perform(post("/admin/products/" + id + "/album/" + imageId + "/cover")
                .session(s.session()).param("_csrf", s.csrf()));
        assertEquals("images/products/FL-CM-01/2.jpg",
                jdbc.queryForObject("SELECT cover_image FROM product WHERE id = ?", String.class, id));
        String after = mvc.perform(get("/admin/products/" + id + "/album").session(s.session()))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertTrue(after.contains("当前封面"), "设为封面后应显示封面标记");
    }

    @Test
    void albumPageSwitchesToEnglish() throws Exception {
        long id = product();
        Session s = login();
        String html = mvc.perform(get("/admin/products/" + id + "/album").session(s.session())
                        .param("lang", "en"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertTrue(html.contains("Garment album"));
        assertTrue(html.contains("Flat lays") && html.contains("On body") && html.contains("Details"));
        assertTrue(html.contains("Empty album falls back to the cover"));
    }

    @Test
    void restockFromListRedirectsBackWithNotice() throws Exception {
        long id = product();
        Session s = login();
        MvcResult r = mvc.perform(post("/admin/products/" + id + "/restock").session(s.session())
                .param("_csrf", s.csrf()).param("delta", "3")
                .header("Referer", "http://localhost:8081/admin/products?filter=live")).andReturn();
        assertEquals(302, r.getResponse().getStatus());
        assertEquals("/admin/products", r.getResponse().getRedirectedUrl(), "带查询串的来源页应去掉参数");
        assertEquals(13, jdbc.queryForObject("SELECT stock FROM product WHERE id = ?", Integer.class, id));

        String html = mvc.perform(get("/admin/products").session(s.session()))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertTrue(html.contains("已补货"), "补货后应在列表页给出一次性提示");
    }

    @Test
    void restockRejectsInvalidDeltaThroughHttp() throws Exception {
        long id = product();
        Session s = login();
        for (String bad : new String[]{"0", "-2", "abc", ""}) {
            mvc.perform(post("/admin/products/" + id + "/restock").session(s.session())
                    .param("_csrf", s.csrf()).param("delta", bad));
        }
        assertEquals(10, jdbc.queryForObject("SELECT stock FROM product WHERE id = ?", Integer.class, id),
                "非法增量不得改动库存");
        String html = mvc.perform(get("/admin/products").session(s.session()))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertTrue(html.contains("补货数量必须是正整数"));
    }

    @Test
    void editPageShowsRestockAndAlbumEntries() throws Exception {
        long id = product();
        Session s = login();
        String html = mvc.perform(get("/admin/products/" + id + "/edit").session(s.session()))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertTrue(html.contains("补货"), "编辑页库存区应有补货入口");
        assertTrue(html.contains("/admin/products/" + id + "/album"), "编辑页应能进相册");
        assertTrue(html.contains("库存只能通过补货入口增加"));
    }
}
