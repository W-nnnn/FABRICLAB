package com.fabriclab.store.controller;

import com.fabriclab.store.entity.SalesOrder;
import com.fabriclab.store.entity.StoreUser;
import com.fabriclab.store.exception.BusinessException;
import com.fabriclab.store.exception.NotFoundException;
import com.fabriclab.store.mapper.StoreUserMapper;
import com.fabriclab.store.model.CurrentUser;
import com.fabriclab.store.model.OrderStatus;
import com.fabriclab.store.security.CsrfService;
import com.fabriclab.store.service.BrandService;
import com.fabriclab.store.service.CartService;
import com.fabriclab.store.service.CurrentUserService;
import com.fabriclab.store.service.PaymentService;
import com.fabriclab.store.service.PaymentService.PayOutcome;
import com.fabriclab.store.service.PaymentService.PaymentView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * 模拟支付页与两个动作（FR-P-01）：页面只呈现订单号与订单总额、非待支付回详情、
 * 成功与重复成功提示不同、失败回到待支付并提示可重试、越权 404。
 */
@WebMvcTest(PaymentController.class)
class PaymentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BrandService brandService;

    @MockitoBean
    private PaymentService paymentService;

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
        when(currentUserService.from(session)).thenReturn(new CurrentUser(1L, "alice", "Alice"));
        StoreUser enabled = new StoreUser();
        enabled.setId(1L);
        enabled.setRole("CUSTOMER");
        enabled.setStatus("ENABLED");
        lenient().when(storeUserMapper.selectById(1L)).thenReturn(enabled);
        lenient().when(brandService.brand()).thenReturn("FABRICLAB");
        lenient().when(brandService.tagline()).thenReturn("未染");
    }

    private SalesOrder order(String status) {
        SalesOrder order = new SalesOrder();
        order.setId(7L);
        order.setOrderNo("FL20260101000000000001");
        order.setUserId(1L);
        order.setStatus(status);
        order.setTotalAmount(new BigDecimal("456.00"));
        order.setReceiverName("林未");
        order.setReceiverPhone("13800001234");
        order.setReceiverAddress("上海市静安区某路 1 号");
        order.setCreatedAt(LocalDateTime.of(2026, 1, 1, 10, 0));
        return order;
    }

    @Test
    void pageRendersOrderNoFixedAmountAndTwoActions() throws Exception {
        when(paymentService.prepare(1L, "7"))
                .thenReturn(new PaymentView(order("PENDING_PAYMENT"), OrderStatus.PENDING_PAYMENT));

        mockMvc.perform(get("/orders/7/payment").session(session))
                .andExpect(status().isOk())
                .andExpect(view().name("payment"))
                .andExpect(content().string(containsString("FL20260101000000000001")))
                .andExpect(content().string(containsString("¥ 456.00")))
                .andExpect(content().string(containsString("本站为品牌演示站点，支付不产生真实扣款")))
                .andExpect(content().string(containsString("确认支付")))
                .andExpect(content().string(containsString("支付未完成")));
    }

    @Test
    void pageOfNonPendingOrderRedirectsToDetail() throws Exception {
        when(paymentService.prepare(1L, "7"))
                .thenReturn(new PaymentView(order("PAID"), OrderStatus.PAID));

        mockMvc.perform(get("/orders/7/payment").session(session))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/orders/7"))
                .andExpect(flash().attribute("orderError", containsString("只有待支付订单")));
    }

    @Test
    void pageOfOtherUsersOrderReturns404() throws Exception {
        when(paymentService.prepare(1L, "9"))
                .thenThrow(new NotFoundException("内容不存在", "内容不存在或已被移除"));

        mockMvc.perform(get("/orders/9/payment").session(session))
                .andExpect(status().isNotFound())
                .andExpect(content().string(containsString("内容不存在")));
    }

    @Test
    void successRedirectsToDetailWithVoucher() throws Exception {
        when(paymentService.pay(1L, "7")).thenReturn(new PayOutcome(true, "MOCK20260101100000000AAAAAAA1"));

        mockMvc.perform(post("/orders/7/payment").session(session).param("_csrf", "token"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/orders/7"))
                .andExpect(flash().attribute("orderFlash", containsString("支付成功")))
                .andExpect(flash().attribute("orderFlash", containsString("MOCK20260101100000000AAAAAAA1")));
        verify(paymentService).pay(1L, "7");
    }

    @Test
    void repeatSuccessTellsFirstVoucherKept() throws Exception {
        when(paymentService.pay(1L, "7")).thenReturn(new PayOutcome(false, "MOCK20260101100000000AAAAAAA1"));

        mockMvc.perform(post("/orders/7/payment").session(session).param("_csrf", "token"))
                .andExpect(redirectedUrl("/orders/7"))
                .andExpect(flash().attribute("orderFlash", containsString("保留首次支付凭证与时间")));
    }

    @Test
    void forgedAmountParameterIsIgnored() throws Exception {
        when(paymentService.pay(1L, "7")).thenReturn(new PayOutcome(true, "MOCK20260101100000000AAAAAAA1"));

        mockMvc.perform(post("/orders/7/payment").session(session)
                        .param("_csrf", "token").param("amount", "0.01").param("totalAmount", "0.01"))
                .andExpect(redirectedUrl("/orders/7"));
        verify(paymentService).pay(1L, "7");
    }

    @Test
    void failureActionKeepsOrderPendingWithRetryHint() throws Exception {
        mockMvc.perform(post("/orders/7/payment/fail").session(session).param("_csrf", "token"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/orders/7"))
                .andExpect(flash().attribute("orderError", containsString("订单仍为待支付")));
        verify(paymentService).recordFailure(1L, "7");
    }

    @Test
    void failureActionOnPaidOrderReportsError() throws Exception {
        org.mockito.Mockito.doThrow(new BusinessException("当前状态不能发起支付"))
                .when(paymentService).recordFailure(1L, "7");

        mockMvc.perform(post("/orders/7/payment/fail").session(session).param("_csrf", "token"))
                .andExpect(redirectedUrl("/orders/7"))
                .andExpect(flash().attribute("orderError", containsString("不能发起支付")));
    }
}
