package com.fabriclab.store.controller;

import com.fabriclab.store.entity.Product;
import com.fabriclab.store.service.BrandService;
import com.fabriclab.store.service.BrowseService;
import com.fabriclab.store.service.BrowseService.HomeView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/** 首页渲染与健康检查（Mock 服务层，不依赖数据库）。 */
@WebMvcTest(MainController.class)
class MainControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BrandService brandService;

    @MockitoBean
    private BrowseService browseService;

    @BeforeEach
    void stubBrandAndHome() {
        when(brandService.brand()).thenReturn("FABRICLAB");
        when(brandService.tagline()).thenReturn("未染");

        Product product = new Product();
        product.setId(7L);
        product.setName("亚麻通勤衬衫");
        product.setPrice(new BigDecimal("199.00"));
        product.setStock(10);
        when(browseService.homeView())
                .thenReturn(new HomeView(List.of(), List.of(product), List.of(product)));
    }

    @Test
    void mainPageRendersBrandAndHomeSections() throws Exception {
        mockMvc.perform(get("/main"))
                .andExpect(status().isOk())
                .andExpect(view().name("index"))
                .andExpect(model().attribute("brand", "FABRICLAB"))
                .andExpect(content().string(containsString("未染")))
                .andExpect(content().string(containsString("新品上架")))
                .andExpect(content().string(containsString("精选推荐")))
                .andExpect(content().string(containsString("亚麻通勤衬衫")))
                .andExpect(content().string(containsString("¥ 199.00")));
    }

    @Test
    void indexAndRootShareTheSameView() throws Exception {
        mockMvc.perform(get("/")).andExpect(status().isOk()).andExpect(view().name("index"));
        mockMvc.perform(get("/index")).andExpect(status().isOk()).andExpect(view().name("index"));
    }

    @Test
    void healthReportsCurrentStage() throws Exception {
        mockMvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.stage").value("T5"));
    }
}
