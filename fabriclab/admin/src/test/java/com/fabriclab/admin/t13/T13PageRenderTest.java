package com.fabriclab.admin.t13;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** T13 页面渲染冒烟：全部入口在真库下可正常渲染，中英两版均无模板错误。 */
@SpringBootTest
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "MYSQL_PASSWORD", matches = ".+")
class T13PageRenderTest {

    @Autowired private MockMvc mvc;

    private MockHttpSession login() throws Exception {
        MvcResult page = mvc.perform(get("/admin/signon")).andReturn();
        String csrf = (String) page.getModelAndView().getModel().get("csrfToken");
        MockHttpSession session = (MockHttpSession) page.getRequest().getSession();
        MvcResult ok = mvc.perform(post("/admin/signon").session(session)
                .param("username", "admin").param("password", "Passw0rd!2026")
                .param("_csrf", csrf)).andReturn();
        assertEquals(302, ok.getResponse().getStatus());
        return (MockHttpSession) ok.getRequest().getSession();
    }

    @Test
    void allT13PagesRenderInBothLanguages() throws Exception {
        MockHttpSession session = login();
        String[] paths = {
                "/admin/console", "/admin/categories", "/admin/categories/new",
                "/admin/categories/1/edit", "/admin/products", "/admin/products/new",
                "/admin/products/1/edit", "/admin/products?filter=draft",
                "/admin/products?kw=FL-", "/admin/products?page=2",
                "/admin/products?size=10", "/admin/products?filter=sold"
        };
        for (String path : paths) {
            MvcResult r = mvc.perform(get(path).session(session)).andReturn();
            assertEquals(200, r.getResponse().getStatus(), path + " 中文版渲染失败");
        }
        MockHttpSession en = login();
        mvc.perform(get("/admin/products?lang=en").session(en)).andReturn();
        for (String path : paths) {
            String withLang = path + (path.contains("?") ? "&" : "?") + "lang=en";
            MvcResult r = mvc.perform(get(withLang).session(en)).andReturn();
            assertEquals(200, r.getResponse().getStatus(), withLang + " 英文版渲染失败");
        }
    }
}
