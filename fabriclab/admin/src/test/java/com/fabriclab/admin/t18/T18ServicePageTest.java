package com.fabriclab.admin.t18;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** 后台咨询页面与写操作：登录检查、列表 / 详情渲染、CSRF、回复反馈与状态迁移。 */
@SpringBootTest
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "MYSQL_PASSWORD", matches = ".+")
class T18ServicePageTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;

    private String csrf;
    private MockHttpSession session;
    private long threadId;

    private record Admin(String csrf, MockHttpSession session) {}

    @BeforeEach
    void setUp() throws Exception {
        clean();
        MvcResult page = mockMvc.perform(get("/admin/signon")).andReturn();
        csrf = (String) page.getModelAndView().getModel().get("csrfToken");
        MockHttpSession before = (MockHttpSession) page.getRequest().getSession();
        MvcResult login = mockMvc.perform(post("/admin/signon").session(before)
                .param("username", "admin").param("password", "Passw0rd!2026")
                .param("_csrf", csrf)).andReturn();
        assertEquals(302, login.getResponse().getStatus());
        session = (MockHttpSession) login.getRequest().getSession();

        Long userId = jdbc.queryForObject("SELECT id FROM store_user WHERE username='alice'", Long.class);
        jdbc.update("INSERT INTO service_thread (thread_no,user_id,category,subject,status) "
                + "VALUES ('T18-C-01',?,'ORDER','T18 页面验收','OPEN')", userId);
        threadId = jdbc.queryForObject("SELECT id FROM service_thread WHERE thread_no='T18-C-01'", Long.class);
        jdbc.update("INSERT INTO service_message (thread_id,sender,author_id,author_name,body) "
                + "VALUES (?,'CUSTOMER',?,'林晓','T18 页面测试消息')", threadId, userId);
    }

    @AfterEach
    void tearDown() {
        clean();
    }

    private void clean() {
        jdbc.update("DELETE m FROM service_message m JOIN service_thread t ON m.thread_id=t.id "
                + "WHERE t.thread_no LIKE 'T18-C-%'");
        jdbc.update("DELETE FROM service_thread WHERE thread_no LIKE 'T18-C-%'");
    }

    private String html(MvcResult mvc) throws Exception {
        return mvc.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private String status() {
        return jdbc.queryForObject("SELECT status FROM service_thread WHERE id=?", String.class, threadId);
    }

    @Test
    void listAndDetailRenderRealData() throws Exception {
        MvcResult list = mockMvc.perform(get("/admin/service").session(session)).andReturn();
        assertEquals(200, list.getResponse().getStatus());
        assertTrue(html(list).contains("T18-C-01"));
        assertTrue(html(list).contains("T18 页面验收"));

        MvcResult detail = mockMvc.perform(get("/admin/service/" + threadId).session(session)).andReturn();
        assertEquals(200, detail.getResponse().getStatus());
        assertTrue(html(detail).contains("T18 页面测试消息"));
        assertTrue(html(detail).contains("alice"));
        assertTrue(html(detail).contains("发送回复"));

        MvcResult filtered = mockMvc.perform(get("/admin/service?status=open").session(session)).andReturn();
        assertEquals(200, filtered.getResponse().getStatus());
        assertTrue(html(filtered).contains("T18-C-01"));
    }

    @Test
    void protectedPagesRequireAdminSession() throws Exception {
        MvcResult list = mockMvc.perform(get("/admin/service")).andReturn();
        assertEquals(302, list.getResponse().getStatus());
        assertTrue(list.getResponse().getRedirectedUrl().startsWith("/admin/signon"));
        MvcResult detail = mockMvc.perform(get("/admin/service/" + threadId)).andReturn();
        assertEquals(302, detail.getResponse().getStatus());
    }

    @Test
    void replyFlowValidatesCsrfAndKeepsDraftOnError() throws Exception {
        MvcResult noCsrf = mockMvc.perform(post("/admin/service/" + threadId + "/reply")
                .session(session).param("body", "客服回复内容")).andReturn();
        assertEquals(403, noCsrf.getResponse().getStatus());
        assertEquals("OPEN", status());

        MvcResult empty = mockMvc.perform(post("/admin/service/" + threadId + "/reply")
                .session(session).param("_csrf", csrf).param("body", "  ")).andReturn();
        assertEquals(302, empty.getResponse().getStatus());
        assertEquals("err", empty.getFlashMap().get("noticeKind"));
        assertEquals("  ", empty.getFlashMap().get("replyDraft"));
        assertEquals("OPEN", status());

        MvcResult ok = mockMvc.perform(post("/admin/service/" + threadId + "/reply")
                .session(session).param("_csrf", csrf).param("body", "已核对，状态待发货")).andReturn();
        assertEquals(302, ok.getResponse().getStatus());
        assertEquals("REPLIED", status());
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM service_message WHERE thread_id=?",
                Integer.class, threadId));

        mockMvc.perform(post("/admin/service/" + threadId + "/close").session(session)
                .param("_csrf", csrf)).andReturn();
        assertEquals("CLOSED", status());
        mockMvc.perform(post("/admin/service/" + threadId + "/reopen").session(session)
                .param("_csrf", csrf)).andReturn();
        assertEquals("OPEN", status());
    }

    @Test
    void globalSearchRoutesToEnquiryList() throws Exception {
        MvcResult result = mockMvc.perform(get("/admin/search").session(session)
                .param("kw", "T18-C-01").param("scope", "service")).andReturn();
        assertEquals(302, result.getResponse().getStatus());
        assertTrue(result.getResponse().getRedirectedUrl().startsWith("/admin/service?kw="));
    }
}
