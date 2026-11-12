package com.fabriclab.store.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fabriclab.store.entity.Product;
import com.fabriclab.store.exception.NotFoundException;
import com.fabriclab.store.model.CategoryType;
import com.fabriclab.store.service.BrandService;
import com.fabriclab.store.service.BrowseService;
import com.fabriclab.store.service.BrowseService.BrowseResult;
import com.fabriclab.store.service.BrowseService.CategoryScope;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/** 商品列表页：参数校验 400、分类不可用 404、正常渲染与条件回显。 */
@WebMvcTest(BrowseController.class)
class BrowseControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BrandService brandService;

    @MockitoBean
    private BrowseService browseService;

    @BeforeEach
    void stubBrand() {
        lenient().when(brandService.brand()).thenReturn("FABRICLAB");
        lenient().when(brandService.tagline()).thenReturn("未染");
    }

    private BrowseResult emptyAllResult() {
        CategoryScope scope = new CategoryScope(null, null, CategoryType.ALL, false, List.of());
        return new BrowseResult(scope, new Page<Product>(1, 6));
    }

    @Test
    void categoryPageRendersAndEchoesKeyword() throws Exception {
        when(browseService.browse(any())).thenReturn(emptyAllResult());
        when(browseService.categoryTree()).thenReturn(List.of());

        mockMvc.perform(get("/category").queryParam("keyword", " 亚麻 "))
                .andExpect(status().isOk())
                .andExpect(view().name("category"))
                .andExpect(content().string(containsString("亚麻")))
                .andExpect(content().string(containsString("清空筛选")));
    }

    @Test
    void invalidParametersReturn400WithErrorPage() throws Exception {
        mockMvc.perform(get("/category").queryParam("page", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("页码必须是整数")));
        mockMvc.perform(get("/category").queryParam("size", "0"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/category").queryParam("size", "51"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/category").queryParam("sort", "SALES"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/category").queryParam("productType", "PETS"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/category").queryParam("category", "1.5"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("分类参数必须是整数 ID")));
        mockMvc.perform(get("/category").queryParam("keyword", "衣".repeat(121)))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("最长 120 字符")));
    }

    @Test
    void missingCategoryReturns404WithFriendlyMessage() throws Exception {
        when(browseService.browse(any())).thenThrow(new NotFoundException("分类不可用"));

        mockMvc.perform(get("/category").queryParam("category", "99999"))
                .andExpect(status().isNotFound())
                .andExpect(content().string(containsString("分类不可用")))
                .andExpect(content().string(containsString("返回商品列表")));
    }
}
