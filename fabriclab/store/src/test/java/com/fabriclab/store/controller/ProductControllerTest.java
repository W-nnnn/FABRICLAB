package com.fabriclab.store.controller;

import com.fabriclab.store.entity.Category;
import com.fabriclab.store.entity.Product;
import com.fabriclab.store.exception.BusinessException;
import com.fabriclab.store.exception.NotFoundException;
import com.fabriclab.store.service.BrandService;
import com.fabriclab.store.service.DetailService;
import com.fabriclab.store.service.DetailService.GalleryImage;
import com.fabriclab.store.service.DetailService.ProductDetailView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import com.fabriclab.store.mapper.StoreUserMapper;
import com.fabriclab.store.security.CsrfService;
import com.fabriclab.store.service.CartService;
import com.fabriclab.store.service.CurrentUserService;

import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/** 商品详情页：正常渲染、ID 参数错误 400、不存在或下架 404、零库存售罄展示。 */
@WebMvcTest(ProductController.class)
class ProductControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BrandService brandService;

    @MockitoBean
    private DetailService detailService;


    @MockitoBean
    private CsrfService csrfService;

    @MockitoBean
    private CurrentUserService currentUserService;

    @MockitoBean
    private CartService cartService;

    @MockitoBean
    private StoreUserMapper storeUserMapper;

    @BeforeEach
    void stubBrand() {
        lenient().when(csrfService.token(any())).thenReturn("token-x");
        lenient().when(currentUserService.from(any())).thenReturn(null);
        lenient().when(brandService.brand()).thenReturn("FABRICLAB");
        lenient().when(brandService.tagline()).thenReturn("未染");
    }

    private ProductDetailView detailView(Product product) {
        Category line = new Category();
        line.setId(1L);
        line.setName("通勤出行");
        Category second = new Category();
        second.setId(101L);
        second.setName("通勤成衣");
        List<GalleryImage> gallery = List.of(
                new GalleryImage("images/products/FL-CM-01/1.jpg", "正面"),
                new GalleryImage("images/products/FL-CM-01/2.jpg", "上身"));
        return new ProductDetailView(product, line, second, gallery);
    }

    private Product onSale(int stock) {
        Product product = new Product();
        product.setId(5L);
        product.setSku("FL-CM-01");
        product.setCategoryId(101L);
        product.setName("云感直筒西装外套");
        product.setSummary("弹力面料。");
        product.setDescription("第一段\n第二段");
        product.setCoverImage("images/products/FL-CM-01/1.jpg");
        product.setPrice(new BigDecimal("429.00"));
        product.setStock(stock);
        product.setStatus("ON_SALE");
        return product;
    }

    @Test
    void detailRendersNameSkuPriceBreadcrumbAndGallery() throws Exception {
        when(detailService.detail("5")).thenReturn(detailView(onSale(25)));

        mockMvc.perform(get("/product").queryParam("id", "5"))
                .andExpect(status().isOk())
                .andExpect(view().name("product"))
                .andExpect(content().string(containsString("云感直筒西装外套")))
                .andExpect(content().string(containsString("FL-CM-01")))
                .andExpect(content().string(containsString("¥ 429.00")))
                .andExpect(content().string(containsString("通勤出行")))
                .andExpect(content().string(containsString("通勤成衣")))
                .andExpect(content().string(containsString("images/products/FL-CM-01/2.jpg")))
                .andExpect(content().string(containsString("第一段")))
                .andExpect(content().string(containsString("返回商品列表")));
    }

    @Test
    void zeroStockShowsSoldOutWithoutBuyEntry() throws Exception {
        when(detailService.detail("5")).thenReturn(detailView(onSale(0)));

        mockMvc.perform(get("/product").queryParam("id", "5"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("售罄")))
                .andExpect(content().string(containsString("该商品暂时无法购买")));
    }

    @Test
    void invalidIdReturns400WithErrorCopy() throws Exception {
        when(detailService.detail(null)).thenThrow(new BusinessException("缺少商品 ID"));
        when(detailService.detail("abc")).thenThrow(new BusinessException("商品参数必须是整数 ID"));
        when(detailService.detail("99999999999999999999"))
                .thenThrow(new BusinessException("商品参数必须是整数 ID"));

        mockMvc.perform(get("/product"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("缺少商品 ID")));
        mockMvc.perform(get("/product").queryParam("id", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("商品参数必须是整数 ID")));
        mockMvc.perform(get("/product").queryParam("id", "99999999999999999999"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void missingOrOfflineProductReturns404WithoutBuyableDetail() throws Exception {
        when(detailService.detail("99999")).thenThrow(
                new NotFoundException("商品不存在或已下架", "该商品不存在或已下架，可返回商品列表浏览其他商品"));

        mockMvc.perform(get("/product").queryParam("id", "99999"))
                .andExpect(status().isNotFound())
                .andExpect(content().string(containsString("商品不存在或已下架")))
                .andExpect(content().string(containsString("返回商品列表")));
    }
}
