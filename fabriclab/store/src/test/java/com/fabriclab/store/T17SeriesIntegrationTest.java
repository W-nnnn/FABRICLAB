package com.fabriclab.store;

import com.fabriclab.store.entity.Product;
import com.fabriclab.store.mapper.ProductMapper;
import com.fabriclab.store.model.CareGuide;
import com.fabriclab.store.service.SeriesService;
import org.junit.jupiter.api.AfterEach;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * T17 服装类扩展（真库）：面料档案按纤维归类且只收在售、系列专题按二级品类归组、
 * 参数 400 / 不存在 404；扩展页新增不改变既有交易路径。
 * 仅在提供 MYSQL_PASSWORD 时执行；无口令时跳过，不伪造通过。
 */
@SpringBootTest
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "MYSQL_PASSWORD", matches = ".+")
class T17SeriesIntegrationTest {

    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private SeriesService seriesService;
    @Autowired private ProductMapper productMapper;

    @AfterEach
    void tearDown() {
        jdbc.update("DELETE FROM product WHERE sku = 'T17-FAB'");
    }

    private String htmlOf(MvcResult mvc) throws Exception {
        return mvc.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    @Test
    void fabricsPageGroupsOnlyOnSaleProductsByFibre() throws Exception {
        String html = htmlOf(mvc.perform(get("/fabrics")).andExpect(status().isOk()).andReturn());
        assertTrue(html.contains("面料档案"));
        assertTrue(html.contains("羊绒与羊毛"));
        assertTrue(html.contains("/product"), "面料档案应展示在售款卡片");
        assertTrue(html.contains("/series/1"), "页脚应带系列专题入口");

        // 归类口径与详情页洗护建议一致：同一商品在档案分组里的纤维类别 == CareGuide.keyOf
        for (SeriesService.FabricGroup g : seriesService.fabrics()) {
            for (Product p : g.products()) {
                assertEquals(g.careKey(), CareGuide.keyOf(p),
                        "档案分组必须与洗护判定使用同一套纤维归类");
                assertEquals("ON_SALE", p.getStatus(), "下架商品不得出现在档案");
            }
        }
    }

    @Test
    void fabricsPageExcludesDraftProducts() throws Exception {
        Long categoryId = jdbc.queryForObject(
                "SELECT id FROM category WHERE parent_id IS NOT NULL LIMIT 1", Long.class);
        assertNotNull(categoryId);
        jdbc.update("INSERT INTO product (sku, category_id, name, price, stock, status, cover_image, "
                        + "description) VALUES ('T17-FAB', ?, 'T17 草稿棉衫', 10.00, 5, 'DRAFT', "
                        + "'images/mark.png', '纯棉 T17 草稿款，不应出现在面料档案')", categoryId);
        for (SeriesService.FabricGroup g : seriesService.fabrics()) {
            for (Product p : g.products()) {
                assertTrue(!"T17-FAB".equals(p.getSku()), "草稿商品不得出现在面料档案");
            }
        }
    }

    @Test
    void craftsPageShowsFourSectionsAndSamples() throws Exception {
        String html = htmlOf(mvc.perform(get("/crafts")).andExpect(status().isOk()).andReturn());
        assertTrue(html.contains("工艺说明"));
        assertTrue(html.contains("不过染缸"));
        assertTrue(html.contains("终身免费修补"));
        assertTrue(html.contains("当季在售"));
    }

    @Test
    void seriesPageGroupsBySecondCategory() throws Exception {
        String html = htmlOf(mvc.perform(get("/series/1")).andExpect(status().isOk()).andReturn());
        assertTrue(html.contains("通勤出行"), "专题页应呈现系列名");
        assertTrue(html.contains("在售件数"));
        assertTrue(html.contains("/category"), "每个分组应链接到分类页");
        assertTrue(html.contains("/product"), "分组应展示在售款");

        SeriesService.SeriesView view = seriesService.series("1");
        assertEquals("通勤出行", view.line().getName());
        assertTrue(view.total() >= 0);
        for (SeriesService.SeriesGroup g : view.groups()) {
            assertEquals(1L, g.second().getParentId(), "分组只能是一级系列下的二级品类");
        }
    }

    @Test
    void seriesRejectsBadParamsAndUnknownOrSecondLevelIds() throws Exception {
        mvc.perform(get("/series/abc")).andExpect(status().isBadRequest());
        mvc.perform(get("/series/999999")).andExpect(status().isNotFound());
        Long secondId = jdbc.queryForObject(
                "SELECT id FROM category WHERE parent_id IS NOT NULL LIMIT 1", Long.class);
        mvc.perform(get("/series/" + secondId)).andExpect(status().isNotFound());
    }

    @Test
    void extensionPagesSwitchToEnglish() throws Exception {
        String fabrics = htmlOf(mvc.perform(get("/fabrics?lang=en"))
                .andExpect(status().isOk()).andReturn());
        assertTrue(fabrics.contains("Fabric Archive"));

        String crafts = htmlOf(mvc.perform(get("/crafts?lang=en"))
                .andExpect(status().isOk()).andReturn());
        assertTrue(crafts.contains("Craft Notes"));

        String series = htmlOf(mvc.perform(get("/series/1?lang=en"))
                .andExpect(status().isOk()).andReturn());
        assertTrue(series.contains("On sale"));
    }

    @Test
    void existingCategoryPageStillRendersAfterFooterChange() throws Exception {
        mvc.perform(get("/category")).andExpect(status().isOk());
        String html = htmlOf(mvc.perform(get("/category")).andReturn());
        assertTrue(html.contains("/fabrics"), "页脚服务列应带面料档案入口");
        assertTrue(html.contains("/crafts"), "页脚服务列应带工艺说明入口");
    }
}
