package com.fabriclab.store;

import com.fabriclab.store.entity.Product;
import com.fabriclab.store.service.BrowseService.BrowseResult;
import com.fabriclab.store.service.BrowseService.HomeView;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 真实库（含种子数据）下的首页两栏与商品列表验收：
 * AC-01 游客可浏览、AC-02 两栏数量与排序、AC-03 分类不串类与翻页条件保留。
 * 仅在提供 MYSQL_PASSWORD 环境变量时执行；无口令时跳过，不伪造通过。
 */
@SpringBootTest
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "MYSQL_PASSWORD", matches = ".+")
class BrowseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private List<Long> ids(String sql) {
        return jdbcTemplate.queryForList(sql, Long.class);
    }

    private BrowseResult resultOf(MvcResult mvc) {
        return (BrowseResult) mvc.getModelAndView().getModel().get("result");
    }

    private List<Long> recordIds(BrowseResult result) {
        return result.page().getRecords().stream().map(Product::getId).toList();
    }

    private String htmlOf(MvcResult mvc) throws Exception {
        return mvc.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    @Test
    void homeSectionsMatchSeedRulesExactly() throws Exception {
        MvcResult mvc = mockMvc.perform(get("/main")).andExpect(status().isOk()).andReturn();
        HomeView home = (HomeView) mvc.getModelAndView().getModel().get("home");

        List<Long> expectedNew = ids(
                "SELECT id FROM product WHERE status='ON_SALE' ORDER BY created_at DESC, id DESC LIMIT 3");
        List<Long> expectedFeatured = ids(
                "SELECT id FROM product WHERE status='ON_SALE' ORDER BY sort_order ASC, id ASC LIMIT 4");

        assertEquals(expectedNew, home.newArrivals().stream().map(Product::getId).toList(),
                "新品上架必须为 ON_SALE 按 created_at、id 倒序前 3 项");
        assertEquals(expectedFeatured, home.featured().stream().map(Product::getId).toList(),
                "精选推荐必须为 ON_SALE 按 sort_order、id 升序前 4 项");

        String html = htmlOf(mvc);
        List<String> draftNames = jdbcTemplate.queryForList(
                "SELECT name FROM product WHERE status='DRAFT'", String.class);
        for (String name : draftNames) {
            assertFalse(html.contains(name), "下架商品不应出现在首页：" + name);
        }
        assertTrue(html.contains("新品上架"));
        assertTrue(html.contains("精选推荐"));
    }

    @Test
    void lineQueryIncludesBothChildrenAndPaginationKeepsConditions() throws Exception {
        List<Long> lineOneIds = ids(
                "SELECT id FROM product WHERE status='ON_SALE' AND category_id IN (101,102) "
                        + "ORDER BY sort_order ASC, id ASC");

        MvcResult page1 = mockMvc.perform(get("/category").queryParam("category", "1"))
                .andExpect(status().isOk()).andReturn();
        BrowseResult result = resultOf(page1);
        assertEquals(lineOneIds.size(), result.page().getTotal(),
                "一级查询总数必须包含两个二级分类的全部上架商品");
        assertEquals(lineOneIds.subList(0, Math.min(6, lineOneIds.size())), recordIds(result));

        MvcResult page2 = mockMvc.perform(get("/category")
                        .queryParam("category", "1").queryParam("page", "2"))
                .andExpect(status().isOk()).andReturn();
        List<Long> pageTwoIds = recordIds(resultOf(page2));
        int from = 6;
        assertEquals(lineOneIds.size() > from ? lineOneIds.subList(from, lineOneIds.size()) : List.of(),
                pageTwoIds, "翻页必须延续同一筛选条件");
    }

    @Test
    void secondLevelQueryDoesNotMixSiblingCategories() throws Exception {
        MvcResult mvc = mockMvc.perform(get("/category").queryParam("category", "101"))
                .andExpect(status().isOk()).andReturn();
        BrowseResult result = resultOf(mvc);
        assertTrue(result.scope().entryIsSecondLevel());
        assertEquals(com.fabriclab.store.model.CategoryType.GARMENTS, result.scope().selectedType());
        for (Product product : result.page().getRecords()) {
            assertEquals(101L, product.getCategoryId(), "二级查询不得混入另一二级分类的商品");
        }
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM product WHERE status='ON_SALE' AND category_id=101", Integer.class);
        assertEquals(count.longValue(), result.page().getTotal());
    }

    @Test
    void typeSwitchFromSecondLevelUpgradesToLineScope() throws Exception {
        MvcResult mvc = mockMvc.perform(get("/category")
                        .queryParam("category", "101").queryParam("productType", "ALL"))
                .andExpect(status().isOk()).andReturn();
        BrowseResult result = resultOf(mvc);
        Integer lineCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM product WHERE status='ON_SALE' AND category_id IN (101,102)",
                Integer.class);
        assertEquals(lineCount.longValue(), result.page().getTotal(),
                "二级入口显式切换类型后应升级为一级范围，不与旧二级 ID 互斥");
        List<Long> childIds = jdbcTemplate.queryForList(
                "SELECT id FROM category WHERE parent_id=1", Long.class);
        for (Product product : result.page().getRecords()) {
            assertTrue(childIds.contains(product.getCategoryId()));
        }
    }

    @Test
    void keywordSearchesNameOnlyAndTreatsWildcardsLiterally() throws Exception {
        MvcResult percent = mockMvc.perform(get("/category").queryParam("keyword", "%%"))
                .andExpect(status().isOk()).andReturn();
        assertEquals(0, resultOf(percent).page().getTotal(),
                "百分号必须按普通字符处理，不得当通配符命中全部商品");

        MvcResult sku = mockMvc.perform(get("/category").queryParam("keyword", "FL-CM"))
                .andExpect(status().isOk()).andReturn();
        assertEquals(0, resultOf(sku).page().getTotal(), "搜索只匹配商品名称，不匹配款号");

        MvcResult named = mockMvc.perform(get("/category").queryParam("keyword", "衬衫"))
                .andExpect(status().isOk()).andReturn();
        Integer expected = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM product WHERE status='ON_SALE' AND name LIKE '%衬衫%'", Integer.class);
        assertEquals(expected.longValue(), resultOf(named).page().getTotal());
    }

    @Test
    void fourSortOptionsMatchSqlOrderingExactly() throws Exception {
        String base = "SELECT id FROM product WHERE status='ON_SALE' AND category_id IN (101,102) ";
        Map<String, String> orderBy = Map.of(
                "DEFAULT", "ORDER BY sort_order ASC, id ASC",
                "PRICE_ASC", "ORDER BY price ASC, id ASC",
                "PRICE_DESC", "ORDER BY price DESC, id ASC",
                "NEWEST", "ORDER BY created_at DESC, id DESC");
        for (Map.Entry<String, String> entry : orderBy.entrySet()) {
            List<Long> expected = ids(base + entry.getValue() + " LIMIT 50");
            MvcResult mvc = mockMvc.perform(get("/category")
                            .queryParam("category", "1")
                            .queryParam("sort", entry.getKey())
                            .queryParam("size", "50"))
                    .andExpect(status().isOk()).andReturn();
            assertEquals(expected, recordIds(resultOf(mvc)), "排序不匹配：" + entry.getKey());
        }
    }

    @Test
    void zeroStockOnSaleProductsStayInListWithSoldOutCopy() throws Exception {
        List<Long> soldOut = ids("SELECT id FROM product WHERE status='ON_SALE' AND stock=0");
        assertFalse(soldOut.isEmpty(), "种子数据必须包含上架售罄样例");

        MvcResult mvc = mockMvc.perform(get("/category").queryParam("size", "50"))
                .andExpect(status().isOk()).andReturn();
        List<Long> listed = recordIds(resultOf(mvc));
        for (Long id : soldOut) {
            assertTrue(listed.contains(id), "零库存上架商品应出现在列表：" + id);
        }
        assertTrue(htmlOf(mvc).contains("暂时缺货"));
    }

    @Test
    void parameterErrorsAndUnavailableCategoryReturnProperStatus() throws Exception {
        mockMvc.perform(get("/category").queryParam("page", "abc")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/category").queryParam("size", "51")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/category").queryParam("keyword", "衣".repeat(121)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/category").queryParam("category", "99999"))
                .andExpect(status().isNotFound())
                .andExpect(result -> assertTrue(htmlOf(result).contains("分类不可用")));
    }
}
