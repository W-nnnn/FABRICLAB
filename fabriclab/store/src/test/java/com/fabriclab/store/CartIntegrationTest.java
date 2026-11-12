package com.fabriclab.store;

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
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * 真实库（含种子数据）下的购物车验收（AC-08~11）：
 * 同商品重复加入合并为一条并累加；0、负数、小数、100 与第 21 种不写库；
 * 加车后退出、重新登录车保留，另一账号无权读写；跨账号修改与移除 404、
 * 旧版本数量不覆盖当前车；下架与售罄条目保留、标注失效原因、无法整车结算、可移除。
 * 仅在提供 MYSQL_PASSWORD 时执行；无口令时跳过，不伪造通过。
 */
@SpringBootTest
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "MYSQL_PASSWORD", matches = ".+")
class CartIntegrationTest {

    private static final String TEST_SKU = "FL-TEST-CART-01";
    private static final String TEST_SKU_2 = "FL-TEST-CART-02";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long testProductId;
    private Long testProductId2;

    @BeforeEach
    void prepareCleanFixture() {
        clearTestCarsAndUsers();
        jdbcTemplate.update("DELETE FROM cart_item WHERE product_id IN (SELECT id FROM product WHERE sku IN (?, ?))",
                TEST_SKU, TEST_SKU_2);
        jdbcTemplate.update("DELETE FROM product WHERE sku IN (?, ?)", TEST_SKU, TEST_SKU_2);
        Long categoryId = jdbcTemplate.queryForObject(
                "SELECT id FROM category WHERE parent_id IS NOT NULL ORDER BY id LIMIT 1", Long.class);
        jdbcTemplate.update("""
                INSERT INTO product (sku, category_id, name, price, stock, status, sort_order, version)
                VALUES (?, ?, '测试亚麻衬衫', 129.00, 200, 'ON_SALE', 999, 0)
                """, TEST_SKU, categoryId);
        jdbcTemplate.update("""
                INSERT INTO product (sku, category_id, name, price, stock, status, sort_order, version)
                VALUES (?, ?, '测试羊毛围巾', 89.00, 200, 'ON_SALE', 999, 0)
                """, TEST_SKU_2, categoryId);
        testProductId = jdbcTemplate.queryForObject(
                "SELECT id FROM product WHERE sku = ?", Long.class, TEST_SKU);
        testProductId2 = jdbcTemplate.queryForObject(
                "SELECT id FROM product WHERE sku = ?", Long.class, TEST_SKU_2);
        assertNotNull(testProductId);
    }

    @AfterEach
    void cleanFixture() {
        clearTestCarsAndUsers();
        jdbcTemplate.update("DELETE FROM cart_item WHERE product_id IN (SELECT id FROM product WHERE sku IN (?, ?))",
                TEST_SKU, TEST_SKU_2);
        jdbcTemplate.update("DELETE FROM product WHERE sku IN (?, ?)", TEST_SKU, TEST_SKU_2);
    }

    private void clearTestCarsAndUsers() {
        jdbcTemplate.update("""
                DELETE ci FROM cart_item ci JOIN store_user u ON ci.user_id = u.id
                WHERE u.username IN ('alice', 'bob')
                """);
        jdbcTemplate.update("UPDATE store_user SET status = 'ENABLED' WHERE username IN ('alice', 'bob')");
    }

    private record Page(String csrf, MockHttpSession session) {
    }

    private record SignIn(String csrf, MockHttpSession session) {
    }

    private Page signonPage() throws Exception {
        MvcResult mvc = mockMvc.perform(get("/signon")).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk()).andReturn();
        return new Page(
                (String) mvc.getModelAndView().getModel().get("csrfToken"),
                (MockHttpSession) mvc.getRequest().getSession());
    }

    /** 登录并返回同一会话的 CSRF 令牌与 Session（令牌为会话级，登录后仍有效）。 */
    private SignIn signIn(String username) throws Exception {
        Page page = signonPage();
        MvcResult login = mockMvc.perform(post("/signon").session(page.session())
                        .param("username", username).param("password", "Passw0rd!2026")
                        .param("_csrf", page.csrf()))
                .andReturn();
        assertEquals(302, login.getResponse().getStatus(), username + " 应登录成功");
        return new SignIn(page.csrf(), (MockHttpSession) login.getRequest().getSession());
    }

    private MvcResult addToCart(MockHttpSession session, String csrf, long productId, String quantity) throws Exception {
        return mockMvc.perform(post("/cart/add").session(session)
                        .param("productId", String.valueOf(productId))
                        .param("quantity", quantity)
                        .param("_csrf", csrf))
                .andReturn();
    }

    private Integer cartRowCount(long userId, long productId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM cart_item WHERE user_id = ? AND product_id = ?", Integer.class, userId, productId);
    }

    private Integer cartQuantity(long userId, long productId) {
        return jdbcTemplate.queryForObject(
                "SELECT quantity FROM cart_item WHERE user_id = ? AND product_id = ?", Integer.class, userId, productId);
    }

    private Long userId(String username) {
        return jdbcTemplate.queryForObject("SELECT id FROM store_user WHERE username = ?", Long.class, username);
    }

    private String htmlOf(MvcResult mvc) throws Exception {
        return mvc.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    @Test
    void ac08DuplicateAddMergesIntoSingleRecordAndAccumulates() throws Exception {
        SignIn signed = signIn("alice");
        MockHttpSession session = signed.session();
        String csrf = signed.csrf();
        long alice = userId("alice");

        addToCart(session, csrf, testProductId, "2");
        assertEquals(1, cartRowCount(alice, testProductId), "首次加入写入一条记录");
        assertEquals(2, cartQuantity(alice, testProductId));

        MvcResult again = addToCart(session, csrf, testProductId, "3");
        assertEquals(302, again.getResponse().getStatus());
        assertEquals(1, cartRowCount(alice, testProductId), "同商品重复加入仍只有一条记录");
        assertEquals(5, cartQuantity(alice, testProductId), "合并累加数量");

        MockHttpSession fresh = signIn("alice").session();
        MvcResult cart = mockMvc.perform(get("/cart").session(fresh)).andReturn();
        String html = htmlOf(cart);
        assertTrue(html.contains("测试亚麻衬衫"), "购物车页展示合并后条目");
        assertTrue(html.contains("已加入购物车，当前该款共 5 件") || html.contains("¥ 645.00"),
                "反馈合并数量或正确小计");
    }

    @Test
    void ac08InvalidQuantitiesAndTwentyFirstKindNeverWrite() throws Exception {
        SignIn signed = signIn("alice");
        MockHttpSession session = signed.session();
        String csrf = signed.csrf();
        long alice = userId("alice");

        for (String bad : new String[]{"0", "-1", "1.5", "100", "abc"}) {
            MvcResult mvc = addToCart(session, csrf, testProductId, bad);
            assertEquals(302, mvc.getResponse().getStatus(), "非法数量应回原页提示");
            assertEquals(0, cartRowCount(alice, testProductId), "非法数量 " + bad + " 不得写库");
        }

        // 第 21 种：直插 20 条不同商品后，再走页面加入被拒绝
        Long categoryId = jdbcTemplate.queryForObject(
                "SELECT id FROM category WHERE parent_id IS NOT NULL ORDER BY id LIMIT 1", Long.class);
        for (int i = 1; i <= 20; i++) {
            jdbcTemplate.update("""
                    INSERT INTO product (sku, category_id, name, price, stock, status, sort_order, version)
                    VALUES (?, ?, ?, 10.00, 50, 'ON_SALE', 999, 0)
                    """, "FL-TEST-FILL-" + i, categoryId, "填充商品" + i);
            Long id = jdbcTemplate.queryForObject(
                    "SELECT id FROM product WHERE sku = ?", Long.class, "FL-TEST-FILL-" + i);
            jdbcTemplate.update("INSERT INTO cart_item (user_id, product_id, quantity, version) VALUES (?, ?, 1, 0)",
                    alice, id);
        }
        MvcResult twentyFirst = addToCart(session, csrf, testProductId, "1");
        assertEquals(302, twentyFirst.getResponse().getStatus());
        assertEquals(0, cartRowCount(alice, testProductId), "第 21 种不得写库");
        Integer kinds = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM cart_item WHERE user_id = ?", Integer.class, alice);
        assertEquals(20, kinds, "车仍为 20 种");

        jdbcTemplate.update("""
                DELETE ci FROM cart_item ci JOIN product p ON ci.product_id = p.id
                WHERE p.sku LIKE 'FL-TEST-FILL-%'
                """);
        jdbcTemplate.update("DELETE FROM product WHERE sku LIKE 'FL-TEST-FILL-%'");
    }

    @Test
    void ac09CartSurvivesSignoutReloginAndStaysIsolated() throws Exception {
        long alice = userId("alice");
        long bob = userId("bob");

        SignIn signed = signIn("alice");
        MockHttpSession session = signed.session();
        String csrf = signed.csrf();
        addToCart(session, csrf, testProductId, "2");

        // 退出后会话失效，再重新登录：车保留
        mockMvc.perform(post("/signout").session(session).param("_csrf", csrf))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().is3xxRedirection());
        assertTrue(session.isInvalid(), "退出必须使会话失效");

        MockHttpSession relogin = signIn("alice").session();
        MvcResult cart = mockMvc.perform(get("/cart").session(relogin)).andReturn();
        String html = htmlOf(cart);
        assertTrue(html.contains("测试亚麻衬衫"), "重新登录后购物车保留");

        // 跨账号隔离：bob 车为空，页面不含 alice 的条目
        MockHttpSession bobSession = signIn("bob").session();
        MvcResult bobCart = mockMvc.perform(get("/cart").session(bobSession)).andReturn();
        String bobHtml = htmlOf(bobCart);
        assertTrue(bobHtml.contains("购物车是空的"), "bob 车应为空");
        assertFalse(bobHtml.contains("测试亚麻衬衫"), "bob 不得看到 alice 条目");
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM cart_item WHERE user_id = ?", Integer.class, bob),
                "bob 数据库车为空");
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM cart_item WHERE user_id = ?", Integer.class, alice),
                "alice 车条目仍在");
    }

    @Test
    void ac10CrossAccountWriteIsRejectedAsNotFound() throws Exception {
        long alice = userId("alice");
        long bob = userId("bob");
        SignIn signedBob = signIn("bob");
        MockHttpSession bobSession = signedBob.session();
        String bobCsrf = signedBob.csrf();
        addToCart(bobSession, bobCsrf, testProductId, "2");
        Long bobItemId = jdbcTemplate.queryForObject(
                "SELECT id FROM cart_item WHERE user_id = ? AND product_id = ?", Long.class, bob, testProductId);
        Long bobVersion = jdbcTemplate.queryForObject(
                "SELECT version FROM cart_item WHERE id = ?", Long.class, bobItemId);

        // alice 修改与移除 bob 的条目：404，不泄露目标详情
        SignIn signedAlice = signIn("alice");
        MockHttpSession aliceSession = signedAlice.session();
        String aliceCsrf = signedAlice.csrf();
        MvcResult update = mockMvc.perform(post("/cart/update").session(aliceSession)
                        .param("itemId", String.valueOf(bobItemId)).param("version", String.valueOf(bobVersion))
                        .param("quantity", "9").param("_csrf", aliceCsrf))
                .andReturn();
        assertEquals(404, update.getResponse().getStatus(), "跨账号修改必须 404");
        assertEquals(2, cartQuantity(bob, testProductId), "bob 条目数量不变");

        MvcResult remove = mockMvc.perform(post("/cart/remove").session(aliceSession)
                .param("itemId", String.valueOf(bobItemId)).param("version", String.valueOf(bobVersion))
                .param("_csrf", aliceCsrf)).andReturn();
        assertEquals(404, remove.getResponse().getStatus(), "跨账号移除必须 404");
        assertEquals(1, cartRowCount(bob, testProductId), "bob 条目未被删除");
        assertEquals(bob, jdbcTemplate.queryForObject(
                "SELECT user_id FROM cart_item WHERE id = ?", Long.class, bobItemId), "条目仍属 bob");
        assertEquals(0, cartRowCount(alice, testProductId), "alice 无该商品条目");
    }

    @Test
    void ac10StaleVersionDoesNotOverwriteCurrentCart() throws Exception {
        long alice = userId("alice");
        SignIn signed = signIn("alice");
        MockHttpSession session = signed.session();
        String csrf = signed.csrf();
        addToCart(session, csrf, testProductId, "2");
        Long itemId = jdbcTemplate.queryForObject(
                "SELECT id FROM cart_item WHERE user_id = ? AND product_id = ?", Long.class, alice, testProductId);
        Long currentVersion = jdbcTemplate.queryForObject(
                "SELECT version FROM cart_item WHERE id = ?", Long.class, itemId);

        // 模拟另一页面已更新：版本前进到 current+1，随后提交旧版本
        MvcResult otherPage = mockMvc.perform(post("/cart/update").session(session)
                        .param("itemId", String.valueOf(itemId)).param("version", String.valueOf(currentVersion))
                        .param("quantity", "4").param("_csrf", csrf))
                .andReturn();
        assertEquals(302, otherPage.getResponse().getStatus());
        assertEquals(4, cartQuantity(alice, testProductId));
        Long advancedVersion = jdbcTemplate.queryForObject(
                "SELECT version FROM cart_item WHERE id = ?", Long.class, itemId);

        MvcResult stale = mockMvc.perform(post("/cart/update").session(session)
                        .param("itemId", String.valueOf(itemId)).param("version", String.valueOf(currentVersion))
                        .param("quantity", "9").param("_csrf", csrf))
                .andReturn();
        assertEquals(302, stale.getResponse().getStatus(), "旧版本提交回购物车页并提示");
        assertEquals(4, cartQuantity(alice, testProductId), "旧版本不得覆盖当前车");
        assertEquals(advancedVersion, jdbcTemplate.queryForObject(
                "SELECT version FROM cart_item WHERE id = ?", Long.class, itemId), "版本不再变化");
    }

    @Test
    void ac11OffSaleItemIsKeptMarkedUnsettleableAndRemovable() throws Exception {
        long alice = userId("alice");
        SignIn signed = signIn("alice");
        MockHttpSession session = signed.session();
        String csrf = signed.csrf();
        addToCart(session, csrf, testProductId, "2");
        addToCart(session, csrf, testProductId2, "1");
        Long itemId = jdbcTemplate.queryForObject(
                "SELECT id FROM cart_item WHERE user_id = ? AND product_id = ?", Long.class, alice, testProductId);

        // 下架（DRAFT）后：条目保留、标注失效原因、整车结算被阻止
        jdbcTemplate.update("UPDATE product SET status = 'DRAFT' WHERE id = ?", testProductId);
        MvcResult cart = mockMvc.perform(get("/cart").session(session)).andReturn();
        String html = htmlOf(cart);
        assertTrue(html.contains("测试亚麻衬衫"), "失效条目保留展示");
        assertTrue(html.contains("商品已下架"), "展示失效原因");
        assertTrue(html.contains("暂时不能整车结算"), "阻止整车结算提示");
        assertEquals(2, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM cart_item WHERE user_id = ?", Integer.class, alice), "两条目均保留，失效条目未被自动删除");

        // 仍然可移除失效条目
        Long itemVersion = jdbcTemplate.queryForObject("SELECT version FROM cart_item WHERE id = ?", Long.class, itemId);
        MvcResult remove = mockMvc.perform(post("/cart/remove").session(session)
                        .param("itemId", String.valueOf(itemId)).param("version", String.valueOf(itemVersion))
                        .param("_csrf", csrf))
                .andReturn();
        assertEquals(302, remove.getResponse().getStatus());
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM cart_item WHERE id = ?", Integer.class, itemId), "失效条目被移除");
    }

    @Test
    void ac11SoldOutItemShowsReasonAndCannotBeAdded() throws Exception {
        long alice = userId("alice");
        SignIn signed = signIn("alice");
        MockHttpSession session = signed.session();
        String csrf = signed.csrf();

        jdbcTemplate.update("UPDATE product SET stock = 0 WHERE id = ?", testProductId2);
        MvcResult add = addToCart(session, csrf, testProductId2, "1");
        assertEquals(302, add.getResponse().getStatus(), "售罄加入被拒绝回原页");
        assertEquals(0, cartRowCount(alice, testProductId2), "售罄不得写库");

        // 车内已有条目随后售罄：保留并标注
        jdbcTemplate.update("UPDATE product SET stock = 200 WHERE id = ?", testProductId2);
        addToCart(session, csrf, testProductId2, "1");
        jdbcTemplate.update("UPDATE product SET stock = 0 WHERE id = ?", testProductId2);
        MvcResult cart = mockMvc.perform(get("/cart").session(session)).andReturn();
        String html = htmlOf(cart);
        assertTrue(html.contains("商品售罄"), "售罄条目标注失效原因");
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM cart_item WHERE user_id = ? AND product_id = ?", Integer.class, alice, testProductId2),
                "售罄条目保留");
    }

    @Test
    void ac09BadgeSumsQuantitiesIncludingInvalidEntries() throws Exception {
        SignIn signed = signIn("alice");
        MockHttpSession session = signed.session();
        String csrf = signed.csrf();
        addToCart(session, csrf, testProductId, "2");

        jdbcTemplate.update("UPDATE product SET status = 'DRAFT' WHERE id = ?", testProductId);
        MvcResult cart = mockMvc.perform(get("/cart").session(session)).andReturn();
        String html = htmlOf(cart);
        // 导航徽标计入失效条目
        assertTrue(html.contains("nav-cart__badge") && html.contains("2"),
                "徽标显示数量总和（含失效条目）");
    }
}
