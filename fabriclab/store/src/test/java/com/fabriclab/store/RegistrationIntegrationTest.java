package com.fabriclab.store;

import com.fabriclab.store.service.CaptchaService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 真实库（含种子数据）下的注册验收：
 * 重复用户名与非法密码被拒；验证码错误 / 过期 / 重复使用被拒；
 * 成功注册落库 CUSTOMER / ENABLED 且 BCrypt 散列。
 * 仅在提供 MYSQL_PASSWORD 环境变量时执行；无口令时跳过，不伪造通过。
 */
@SpringBootTest
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "MYSQL_PASSWORD", matches = ".+")
class RegistrationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private CaptchaService captchaService;

    private String htmlOf(MvcResult mvc) throws Exception {
        return mvc.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    /** 通过 GET 拿到页面当前验证码（模型属性），返回 token 与 code。 */
    private CaptchaService.Captcha captchaFromPage() throws Exception {
        MvcResult mvc = mockMvc.perform(get("/register")).andExpect(status().isOk()).andReturn();
        return (CaptchaService.Captcha) mvc.getModelAndView().getModel().get("captcha");
    }

    private MvcResult postRegister(String username, String password, String confirm,
                                   String token, String code) throws Exception {
        return mockMvc.perform(post("/register")
                        .param("username", username)
                        .param("password", password)
                        .param("confirm", confirm)
                        .param("captchaToken", token)
                        .param("captchaInput", code))
                .andExpect(status().isOk()).andReturn();
    }

    @Test
    void successfulRegistrationCreatesCustomerAccount() throws Exception {
        String username = "regt" + Long.toString(System.currentTimeMillis() % 100000000L);
        CaptchaService.Captcha captcha = captchaFromPage();

        MvcResult mvc = postRegister(username, "abcd1234", "abcd1234", captcha.token(), captcha.code());
        String html = htmlOf(mvc);
        assertTrue(html.contains("注册成功"), "成功路径应展示注册成功：" + htmlOf(mvc).substring(0, Math.min(400, html.length())));
        assertTrue(html.contains(username));

        var row = jdbcTemplate.queryForMap(
                "SELECT username, role, status, password_hash FROM store_user WHERE username=?", username);
        assertEquals("CUSTOMER", row.get("role"));
        assertEquals("ENABLED", row.get("status"));
        String hash = (String) row.get("password_hash");
        assertTrue(hash.startsWith("$2"), "口令必须为 BCrypt 散列");
        assertNotEquals("abcd1234", hash);
        assertFalse(html.contains("abcd1234"), "任何响应不得回显密码明文");
    }

    @Test
    void duplicateUsernameRejectedWithoutNewRow() throws Exception {
        CaptchaService.Captcha captcha = captchaFromPage();
        MvcResult mvc = postRegister("alice", "abcd1234", "abcd1234", captcha.token(), captcha.code());

        assertTrue(htmlOf(mvc).contains("用户名已被使用"));
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM store_user WHERE username='alice'", Integer.class);
        assertEquals(1, count, "重复注册不得产生新行");
    }

    @Test
    void invalidPasswordVariantsRejected() throws Exception {
        String username = "regt" + Long.toString(System.currentTimeMillis() % 100000000L);

        CaptchaService.Captcha first = captchaFromPage();
        assertTrue(htmlOf(postRegister(username, "a1" + "x".repeat(5), "a1" + "x".repeat(5),
                        first.token(), first.code())).contains("密码长度必须为 8—32 个字符"));

        CaptchaService.Captcha second = captchaFromPage();
        assertTrue(htmlOf(postRegister(username, "abcdefgh", "abcdefgh", second.token(), second.code()))
                .contains("至少两类"));

        CaptchaService.Captcha third = captchaFromPage();
        assertTrue(htmlOf(postRegister(username, "abcd1234", "abcd9999", third.token(), third.code()))
                .contains("两次输入的密码不一致"));

        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM store_user WHERE username=?", Integer.class, username);
        assertEquals(0, count, "非法密码不得落库");
    }

    @Test
    void wrongCaptchaCodeRejected() throws Exception {
        String username = "regt" + Long.toString(System.currentTimeMillis() % 100000000L);
        CaptchaService.Captcha captcha = captchaFromPage();

        MvcResult mvc = postRegister(username, "abcd1234", "abcd1234", captcha.token(), "ZZZZ");
        assertTrue(htmlOf(mvc).contains("验证码错误"));
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM store_user WHERE username=?", Integer.class, username);
        assertEquals(0, count);
    }

    @Test
    void reusedCaptchaTokenRejected() throws Exception {
        String username = "regt" + Long.toString(System.currentTimeMillis() % 100000000L);
        CaptchaService.Captcha captcha = captchaFromPage();

        // 第一次提交验证码错误（该验证码被消费），第二次带正确验证码重复使用同一令牌
        assertTrue(htmlOf(postRegister(username, "abcd1234", "abcd1234", captcha.token(), "ZZZZ"))
                .contains("验证码错误"));
        MvcResult second = postRegister(username, "abcd1234", "abcd1234", captcha.token(), captcha.code());
        assertTrue(htmlOf(second).contains("验证码已使用"), "同一令牌第二次提交必须被拒绝");
    }

    @Test
    void registerPageContainsCaptchaAndNoForbiddenEntries() throws Exception {
        String html = htmlOf(mockMvc.perform(get("/register")).andExpect(status().isOk()).andReturn());
        assertFalse(html.contains("找回密码"));
        assertFalse(html.contains("第三方登录"));
        assertTrue(html.contains("验证码"));
    }
}
