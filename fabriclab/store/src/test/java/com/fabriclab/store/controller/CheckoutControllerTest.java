package com.fabriclab.store.controller;

import com.fabriclab.store.entity.StoreUser;
import com.fabriclab.store.exception.BusinessException;
import com.fabriclab.store.mapper.StoreUserMapper;
import com.fabriclab.store.model.CheckoutConfirmation;
import com.fabriclab.store.model.CurrentUser;
import com.fabriclab.store.security.CsrfService;
import com.fabriclab.store.service.BrandService;
import com.fabriclab.store.service.CartService;
import com.fabriclab.store.service.CartService.CartLine;
import com.fabriclab.store.service.CartService.CartView;
import com.fabriclab.store.service.CheckoutService;
import com.fabriclab.store.service.CheckoutService.CheckoutView;
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

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * 结算确认与提交（FR-O-01、FR-O-02）：确认只存会话、提交成功清确认并跳详情、
 * 收货校验失败保留输入、确认缺失或下单失败回购物车提示。
 */
@WebMvcTest(CheckoutController.class)
class CheckoutControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BrandService brandService;

    @MockitoBean
    private CheckoutService checkoutService;

    @MockitoBean
    private CartService cartService;

    @MockitoBean
    private CsrfService csrfService;

    @MockitoBean
    private CurrentUserService currentUserService;

    @MockitoBean
    private StoreUserMapper storeUserMapper;

    private MockHttpSession session;

    @BeforeEach
    void setUp() {
        session = new MockHttpSession();
        // 登录会话带口令散列指纹；后台重置口令后该请求会被判定为凭证已变
        session.setAttribute(CurrentUserService.KEY_CREDENTIAL_TAG, CurrentUserService.credentialTag("$2a$10$AZQW9H0zzpstWCih7ev.rubTNT2spCFm8iydr01pdtiTnxJpA1A76"));
        when(currentUserService.from(session)).thenReturn(new CurrentUser(1L, "alice", "Alice"));
        StoreUser enabled = new StoreUser();
        enabled.setId(1L);
        enabled.setRole("CUSTOMER");
        enabled.setStatus("ENABLED");
        enabled.setPasswordHash("$2a$10$AZQW9H0zzpstWCih7ev.rubTNT2spCFm8iydr01pdtiTnxJpA1A76");
        lenient().when(storeUserMapper.selectById(1L)).thenReturn(enabled);
        lenient().when(brandService.brand()).thenReturn("FABRICLAB");
        lenient().when(brandService.tagline()).thenReturn("未染");
    }

    private CheckoutView stubView() {
        CartLine line = new CartLine(11L, 21L, 2, 0L, "亚麻衬衫", "FL-000-01",
                "images/demo.jpg", new BigDecimal("199.00"), 10, false, null);
        CartView cart = new CartView(List.of(line), new BigDecimal("398.00"), 2, false, false);
        CheckoutConfirmation confirmation = new CheckoutConfirmation(1L,
                List.of(new CheckoutConfirmation.Line(11L, 21L, 2, 0L, new BigDecimal("199.00"))),
                new BigDecimal("398.00"));
        return new CheckoutView(cart, confirmation);
    }

    @Test
    void checkoutPageRendersCartAndStoresConfirmationInSession() throws Exception {
        when(checkoutService.prepare(1L)).thenReturn(stubView());

        mockMvc.perform(get("/checkout").session(session))
                .andExpect(status().isOk())
                .andExpect(view().name("checkout"))
                .andExpect(content().string(containsString("亚麻衬衫")))
                .andExpect(content().string(containsString("¥ 398.00")))
                .andExpect(content().string(containsString("收货人")));

        assertNotNull(session.getAttribute(CheckoutController.KEY_CONFIRMATION));
    }

    @Test
    void checkoutRedirectsToCartWhenCartNotCheckoutable() throws Exception {
        when(checkoutService.prepare(1L)).thenThrow(new BusinessException("购物车包含不可结算条目，请先移除或修正后再结算"));

        mockMvc.perform(get("/checkout").session(session))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/cart"));
    }

    @Test
    void submitWithoutConfirmationRedirectsToCart() throws Exception {
        mockMvc.perform(post("/checkout/submit").session(session)
                        .param("receiverName", "林未").param("receiverPhone", "138")
                        .param("receiverAddress", "地址").param("_csrf", "token"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/cart"));
    }

    @Test
    void submitCreatesOrderAndClearsConfirmation() throws Exception {
        CheckoutView stub = stubView();
        session.setAttribute(CheckoutController.KEY_CONFIRMATION, stub.confirmation());
        when(checkoutService.prepare(1L)).thenReturn(stub);
        when(checkoutService.placeOrder(eq(1L), any(), any())).thenReturn(42L);

        mockMvc.perform(post("/checkout/submit").session(session)
                        .param("receiverName", "  林未  ").param("receiverPhone", " 13800001234 ")
                        .param("receiverAddress", " 上海市静安区某路 1 号 ").param("_csrf", "token"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/orders/42"));

        assertNull(session.getAttribute(CheckoutController.KEY_CONFIRMATION));
    }

    @Test
    void submitWithInvalidReceiverKeepsInputAndPage() throws Exception {
        CheckoutView stub = stubView();
        session.setAttribute(CheckoutController.KEY_CONFIRMATION, stub.confirmation());
        when(checkoutService.prepare(1L)).thenReturn(stub);

        mockMvc.perform(post("/checkout/submit").session(session)
                        .param("receiverName", "林未").param("receiverPhone", "138")
                        .param("receiverAddress", "   ").param("_csrf", "token"))
                .andExpect(status().isOk())
                .andExpect(view().name("checkout"))
                .andExpect(model().attribute("checkoutError", "详细地址不能为空"))
                .andExpect(content().string(containsString("详细地址不能为空")))
                .andExpect(content().string(containsString("value=\"林未\"")));
    }

    @Test
    void submitFailureKeepsConfirmationForRetry() throws Exception {
        CheckoutView stub = stubView();
        session.setAttribute(CheckoutController.KEY_CONFIRMATION, stub.confirmation());
        when(checkoutService.prepare(1L)).thenReturn(stub);
        when(checkoutService.placeOrder(eq(1L), any(), any()))
                .thenThrow(new BusinessException("购物车中有商品价格已变化，请重新确认"));

        mockMvc.perform(post("/checkout/submit").session(session)
                        .param("receiverName", "林未").param("receiverPhone", "138")
                        .param("receiverAddress", "地址").param("_csrf", "token"))
                .andExpect(status().isOk())
                .andExpect(view().name("checkout"))
                .andExpect(content().string(containsString("价格已变化")));

        assertNotNull(session.getAttribute(CheckoutController.KEY_CONFIRMATION));
    }
}
