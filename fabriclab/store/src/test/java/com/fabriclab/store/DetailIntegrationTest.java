package com.fabriclab.store;

import com.fabriclab.store.entity.Product;
import com.fabriclab.store.service.DetailService.ProductDetailView;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 真实库（含种子数据）下的商品详情验收（AC-04）：
 * 下架与不存在不可购买、零库存售罄展示、相册有序与封面回退、ID 参数校验。
 * 仅在提供 MYSQL_PASSWORD 环境变量时执行；无口令时跳过，不伪造通过。
 */
@SpringBootTest
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "MYSQL_PASSWORD", matches = ".+")
class DetailIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String htmlOf(MvcResult mvc) throws Exception {
        return mvc.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    @Test
    void albumImagesShowInDatabaseOrder() throws Exception {
        Long productId = jdbcTemplate.queryForObject(
                "SELECT p.id FROM product p JOIN product_image pi ON pi.product_id=p.id "
                        + "WHERE p.status='ON_SALE' GROUP BY p.id HAVING COUNT(pi.id) > 1 LIMIT 1", Long.class);
        MvcResult mvc = mockMvc.perform(get("/product").queryParam("id", String.valueOf(productId)))
                .andExpect(status().isOk()).andReturn();
        ProductDetailView view = (ProductDetailView) mvc.getModelAndView().getModel().get("view");

        List<String> expected = jdbcTemplate.queryForList(
                "SELECT image_path FROM product_image WHERE product_id=" + productId
                        + " ORDER BY sort_order ASC, id ASC", String.class);
        assertEquals(expected, view.gallery().stream().map(g -> g.path()).toList(),
                "相册必须按 sort_order、id 升序展示");

        String html = htmlOf(mvc);
        Product product = view.product();
        assertTrue(html.contains(product.getName()));
        assertTrue(html.contains(product.getSku()));
        assertTrue(html.contains("¥ " + product.getPrice().toPlainString()));
        assertTrue(html.contains("现货"), "有库存商品展示现货");
        assertTrue(html.contains("返回商品列表"));
    }

    @Test
    void noAlbumProductFallsBackToCover() throws Exception {
        Long productId = jdbcTemplate.queryForObject(
                "SELECT p.id FROM product p WHERE p.status='ON_SALE' "
                        + "AND NOT EXISTS (SELECT 1 FROM product_image pi WHERE pi.product_id=p.id) LIMIT 1",
                Long.class);
        MvcResult mvc = mockMvc.perform(get("/product").queryParam("id", String.valueOf(productId)))
                .andExpect(status().isOk()).andReturn();
        ProductDetailView view = (ProductDetailView) mvc.getModelAndView().getModel().get("view");

        String cover = jdbcTemplate.queryForObject(
                "SELECT cover_image FROM product WHERE id=" + productId, String.class);
        assertEquals(1, view.gallery().size(), "无相册商品回退到封面");
        assertEquals(cover, view.gallery().get(0).path());
        assertTrue(htmlOf(mvc).contains("暂无相册"));
    }

    @Test
    void draftAndMissingProductsReturn404WithoutBuyableDetail() throws Exception {
        Long draftId = jdbcTemplate.queryForObject(
                "SELECT id FROM product WHERE status='DRAFT' LIMIT 1", Long.class);
        String draftName = jdbcTemplate.queryForObject(
                "SELECT name FROM product WHERE id=" + draftId, String.class);

        MvcResult draft = mockMvc.perform(get("/product").queryParam("id", String.valueOf(draftId)))
                .andExpect(status().isNotFound()).andReturn();
        String draftHtml = htmlOf(draft);
        assertTrue(draftHtml.contains("商品不存在或已下架"));
        assertFalse(draftHtml.contains(draftName), "下架商品不得渲染可购买详情");

        mockMvc.perform(get("/product").queryParam("id", "999999"))
                .andExpect(status().isNotFound())
                .andExpect(result -> assertTrue(htmlOf(result).contains("商品不存在或已下架")));
    }

    @Test
    void zeroStockProductViewableAndNotDeducted() throws Exception {
        Long soldOutId = jdbcTemplate.queryForObject(
                "SELECT id FROM product WHERE status='ON_SALE' AND stock=0 LIMIT 1", Long.class);

        MvcResult mvc = mockMvc.perform(get("/product").queryParam("id", String.valueOf(soldOutId)))
                .andExpect(status().isOk()).andReturn();
        String html = htmlOf(mvc);
        assertTrue(html.contains("售罄"));
        assertTrue(html.contains("该商品暂时无法购买"));

        Integer stockAfter = jdbcTemplate.queryForObject(
                "SELECT stock FROM product WHERE id=" + soldOutId, Integer.class);
        assertEquals(0, stockAfter, "浏览详情不得扣减库存");
    }

    @Test
    void invalidProductIdReturns400() throws Exception {
        mockMvc.perform(get("/product").queryParam("id", "abc")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/product")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/product").queryParam("id", "99999999999999999999"))
                .andExpect(status().isBadRequest());
    }
}
