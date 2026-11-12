package com.fabriclab.store.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fabriclab.store.entity.SalesOrder;
import com.fabriclab.store.entity.SalesOrderItem;
import com.fabriclab.store.entity.StoreUser;
import com.fabriclab.store.exception.NotFoundException;
import com.fabriclab.store.mapper.StoreUserMapper;
import com.fabriclab.store.model.CurrentUser;
import com.fabriclab.store.model.OrderStatus;
import com.fabriclab.store.security.CsrfService;
import com.fabriclab.store.service.BrandService;
import com.fabriclab.store.service.CartService;
import com.fabriclab.store.service.CurrentUserService;
import com.fabriclab.store.service.OrderService;
import com.fabriclab.store.service.OrderService.OrderDetail;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
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
 * 我的订单列表、详情与取消（FR-O-03、FR-O-04）：五状态与订单号参数、
 * 非法参数 400、越权 404、取消后回详情。
 */
@WebMvcTest(OrderController.class)
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BrandService brandService;

    @MockitoBean
    private OrderService orderService;

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

    private SalesOrder order(long id, String status) {
        SalesOrder order = new SalesOrder();
        order.setId(id);
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
    void listRendersOrdersWithStatusLabelsAndFilters() throws Exception {
        Page<SalesOrder> page = new Page<>(1, 10);
        page.setRecords(List.of(order(7L, "PENDING_PAYMENT"), order(8L, "CANCELLED")));
        page.setTotal(2);
        when(orderService.list(eq(1L), any())).thenReturn(page);

        mockMvc.perform(get("/orders").session(session))
                .andExpect(status().isOk())
                .andExpect(view().name("orders"))
                .andExpect(content().string(containsString("FL20260101000000000001")))
                .andExpect(content().string(containsString("待支付")))
                .andExpect(content().string(containsString("已取消")))
                .andExpect(content().string(containsString("输入完整订单号")));
    }

    @Test
    void listShowsEmptyStateWhenNoOrderMatches() throws Exception {
        Page<SalesOrder> page = new Page<>(1, 10);
        page.setRecords(List.of());
        page.setTotal(0);
        when(orderService.list(eq(1L), any())).thenReturn(page);

        mockMvc.perform(get("/orders").session(session).param("orderNo", "FL0000"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("没有匹配该订单号的订单")));
    }

    @Test
    void invalidStatusParamReturns400() throws Exception {
        mockMvc.perform(get("/orders").session(session).param("status", "DELIVERING"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void invalidPageSizeReturns400() throws Exception {
        mockMvc.perform(get("/orders").session(session).param("size", "100"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void detailRendersSnapshotAndActions() throws Exception {
        SalesOrder order = order(7L, "PENDING_PAYMENT");
        when(orderService.detail(1L, "7")).thenReturn(new OrderDetail(
                order, List.of(item()), OrderStatus.PENDING_PAYMENT));

        mockMvc.perform(get("/orders/7").session(session))
                .andExpect(status().isOk())
                .andExpect(view().name("order"))
                .andExpect(content().string(containsString("亚麻衬衫")))
                .andExpect(content().string(containsString("应付")))
                .andExpect(content().string(containsString("取消订单")))
                .andExpect(content().string(containsString("教学模拟支付")));
    }

    @Test
    void detailOfOtherUserReturns404() throws Exception {
        when(orderService.detail(1L, "9"))
                .thenThrow(new NotFoundException("内容不存在", "内容不存在或已被移除"));

        mockMvc.perform(get("/orders/9").session(session))
                .andExpect(status().isNotFound())
                .andExpect(content().string(containsString("内容不存在")));
    }

    @Test
    void cancelRedirectsBackToDetail() throws Exception {
        mockMvc.perform(post("/orders/7/cancel").session(session).param("_csrf", "token"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/orders/7"));
        verify(orderService).cancel(1L, "7");
    }

    private SalesOrderItem item() {
        SalesOrderItem item = new SalesOrderItem();
        item.setOrderId(7L);
        item.setProductId(21L);
        item.setSkuSnapshot("FL-000-01");
        item.setProductNameSnapshot("亚麻衬衫");
        item.setCoverImageSnapshot("images/demo.jpg");
        item.setUnitPrice(new BigDecimal("199.00"));
        item.setQuantity(2);
        item.setLineAmount(new BigDecimal("398.00"));
        return item;
    }
}
