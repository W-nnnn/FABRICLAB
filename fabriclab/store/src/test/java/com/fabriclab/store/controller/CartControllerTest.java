package com.fabriclab.store.controller;

import com.fabriclab.store.exception.BusinessException;
import com.fabriclab.store.mapper.ProductMapper;
import com.fabriclab.store.mapper.StoreUserMapper;
import com.fabriclab.store.model.CurrentUser;
import com.fabriclab.store.security.CsrfService;
import com.fabriclab.store.service.BrandService;
import com.fabriclab.store.service.CartService;
import com.fabriclab.store.service.CartService.CartLine;
import com.fabriclab.store.service.CartService.CartView;
import com.fabriclab.store.service.CurrentUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * 购物车页与写操作：本人车展示、加入回商品页反馈、修改与移除回购物车页、
 * 业务失败保留原车并提示。未登录拦截由 AuthInterceptor 覆盖，此处登录态请求。
 */
@WebMvcTest(CartController.class)
class CartControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BrandService brandService;

    @MockitoBean
    private CartService cartService;

    @MockitoBean
    private CsrfService csrfService;

    @MockitoBean
    private CurrentUserService currentUserService;

    @MockitoBean
    private StoreUserMapper storeUserMapper;

    /** 购物袋页推荐位依赖商品表；本类不断言推荐内容，未打桩时按空列表渲染。 */
    @MockitoBean
    private ProductMapper productMapper;

    private MockHttpSession session;

    @BeforeEach
    void setUp() {
        session = new MockHttpSession();
        when(currentUserService.from(session))
                .thenReturn(new CurrentUser(1L, "alice", "Alice"));
        // 访问检查每请求查库复核：打桩为启用 CUSTOMER，登录态请求放行
        com.fabriclab.store.entity.StoreUser enabled = new com.fabriclab.store.entity.StoreUser();
        enabled.setId(1L);
        enabled.setRole("CUSTOMER");
        enabled.setStatus("ENABLED");
        lenient().when(storeUserMapper.selectById(1L)).thenReturn(enabled);
        lenient().when(brandService.brand()).thenReturn("FABRICLAB");
        lenient().when(brandService.tagline()).thenReturn("未染");
    }

    private CartLine line(long itemId, int quantity, boolean invalid) {
        return new CartLine(itemId, 10L, quantity, 0L, "亚麻衬衫", "FL-000-01",
                "images/demo.jpg", new BigDecimal("199.00"), 10, invalid,
                invalid ? "商品已下架" : null);
    }

    @Test
    void cartPageRendersLinesAndSummary() throws Exception {
        when(cartService.view(1L)).thenReturn(new CartView(
                List.of(line(1, 2, false)), new BigDecimal("398.00"), 2, false, false));

        mockMvc.perform(get("/cart").session(session))
                .andExpect(status().isOk())
                .andExpect(view().name("cart"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("亚麻衬衫")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("¥ 398.00")));
    }

    @Test
    void cartPageShowsInvalidReasonAndBlocksCheckout() throws Exception {
        when(cartService.view(1L)).thenReturn(new CartView(
                List.of(line(1, 2, true)), new BigDecimal("0.00"), 2, true, false));

        mockMvc.perform(get("/cart").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("商品已下架")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("暂时不能整车结算")));
    }

    @Test
    void cartPageShowsEmptyState() throws Exception {
        when(cartService.view(1L)).thenReturn(new CartView(List.of(), new BigDecimal("0.00"), 0, false, true));

        mockMvc.perform(get("/cart").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("购物车是空的")));
    }

    @Test
    void addToCartRedirectsToProductWithMergedFeedback() throws Exception {
        when(cartService.addToCart(eq(1L), eq("10"), eq("2"))).thenReturn(5);

        mockMvc.perform(post("/cart/add").session(session)
                        .param("productId", "10").param("quantity", "2").param("_csrf", "token"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/product?id=10"));
        verify(cartService).addToCart(1L, "10", "2");
    }

    @Test
    void addToCartFailureKeepsCartAndRedirectsWithMessage() throws Exception {
        when(cartService.addToCart(eq(1L), eq("10"), eq("100")))
                .thenThrow(new BusinessException("库存不足，当前可购 3 件"));

        mockMvc.perform(post("/cart/add").session(session)
                        .param("productId", "10").param("quantity", "100").param("_csrf", "token"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/product?id=10"));

        // 失败不修改原车：服务层抛异常即无写库调用，仅验证无 update/insert 可由服务测试保证
    }

    @Test
    void updateQuantityRedirectsBackToCart() throws Exception {
        mockMvc.perform(post("/cart/update").session(session)
                        .param("itemId", "1").param("version", "0").param("quantity", "3").param("_csrf", "token"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/cart"));
        verify(cartService).updateQuantity(1L, "1", "0", "3");
    }

    @Test
    void removeRedirectsBackToCart() throws Exception {
        mockMvc.perform(post("/cart/remove").session(session)
                        .param("itemId", "1").param("version", "0").param("_csrf", "token"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/cart"));
        verify(cartService).remove(1L, "1", "0");
    }
}
