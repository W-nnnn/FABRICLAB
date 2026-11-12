package com.fabriclab.store.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fabriclab.store.entity.Product;
import com.fabriclab.store.entity.SalesOrder;
import com.fabriclab.store.entity.SalesOrderItem;
import com.fabriclab.store.entity.StoreUser;
import com.fabriclab.store.exception.BusinessException;
import com.fabriclab.store.exception.NotFoundException;
import com.fabriclab.store.mapper.ProductMapper;
import com.fabriclab.store.mapper.SalesOrderItemMapper;
import com.fabriclab.store.mapper.SalesOrderMapper;
import com.fabriclab.store.mapper.StoreUserMapper;
import com.fabriclab.store.model.OrderQuery;
import com.fabriclab.store.model.OrderStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 本人订单查询与取消规则（FR-O-03、FR-O-04）：归属 404、状态与订单号筛选、
 * 取消仅待支付且只恢复一次库存、状态迁移带原状态条件防并发双写。
 */
class OrderServiceTest {

    private static final long USER = 1L;

    private SalesOrderMapper salesOrderMapper;
    private SalesOrderItemMapper salesOrderItemMapper;
    private ProductMapper productMapper;
    private StoreUserMapper storeUserMapper;
    private OrderService orderService;

    @BeforeEach
    void setUp() {
        salesOrderMapper = mock(SalesOrderMapper.class);
        salesOrderItemMapper = mock(SalesOrderItemMapper.class);
        productMapper = mock(ProductMapper.class);
        storeUserMapper = mock(StoreUserMapper.class);
        orderService = new OrderService(salesOrderMapper, salesOrderItemMapper, productMapper,
                storeUserMapper, new CancelFaultPoint());
        when(storeUserMapper.selectList(any())).thenReturn(List.of(new StoreUser()));
    }

    private SalesOrder order(long id, long userId, String status) {
        SalesOrder order = new SalesOrder();
        order.setId(id);
        order.setOrderNo("FL20260101000000000001");
        order.setUserId(userId);
        order.setStatus(status);
        order.setTotalAmount(new BigDecimal("456.00"));
        return order;
    }

    private SalesOrderItem item(long productId, int quantity) {
        SalesOrderItem item = new SalesOrderItem();
        item.setProductId(productId);
        item.setQuantity(quantity);
        item.setUnitPrice(new BigDecimal("199.00"));
        item.setLineAmount(new BigDecimal("398.00"));
        return item;
    }

    @Test
    void listAppliesStatusAndExactOrderNoWithPaging() {
        when(salesOrderMapper.selectPage(any(), any())).thenReturn(new Page<>(1, 10));

        orderService.list(USER, OrderQuery.parse("PAID", " FL20260101000000000001 ", "2", "20"));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Page<SalesOrder>> pageCaptor = ArgumentCaptor.forClass(Page.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Wrapper<SalesOrder>> wrapperCaptor = ArgumentCaptor.forClass(Wrapper.class);
        verify(salesOrderMapper).selectPage(pageCaptor.capture(), wrapperCaptor.capture());
        assertEquals(2, pageCaptor.getValue().getCurrent());
        assertEquals(20, pageCaptor.getValue().getSize());
        // 条件数：本人 + 状态 + 订单号；排序固定为创建时间与 ID 倒序
        LambdaQueryWrapper<SalesOrder> wrapper = (LambdaQueryWrapper<SalesOrder>) wrapperCaptor.getValue();
        // 先取 SQL 片段以生成占位参数，再核对条件数：本人 + 状态 + 订单号
        assertTrue(wrapper.getSqlSegment().contains("ORDER BY"));
        assertEquals(3, wrapper.getParamNameValuePairs().size());
    }

    @Test
    void listWithoutFiltersOnlyLimitsToCurrentUser() {
        when(salesOrderMapper.selectPage(any(), any())).thenReturn(new Page<>(1, 10));

        orderService.list(USER, OrderQuery.parse(null, null, null, null));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Wrapper<SalesOrder>> wrapperCaptor = ArgumentCaptor.forClass(Wrapper.class);
        verify(salesOrderMapper).selectPage(any(), wrapperCaptor.capture());
        LambdaQueryWrapper<SalesOrder> wrapper = (LambdaQueryWrapper<SalesOrder>) wrapperCaptor.getValue();
        wrapper.getSqlSegment();
        assertEquals(1, wrapper.getParamNameValuePairs().size(), "仅本人条件，无状态与订单号筛选");
    }

    @Test
    void detailRejectsOtherUsersOrder() {
        when(salesOrderMapper.selectById(7L)).thenReturn(order(7L, 2L, "PENDING_PAYMENT"));
        assertThrows(NotFoundException.class, () -> orderService.detail(USER, "7"));
        verify(salesOrderItemMapper, never()).selectList(any());
    }

    @Test
    void detailRejectsMissingOrder() {
        assertThrows(NotFoundException.class, () -> orderService.detail(USER, "7"));
    }

    @Test
    void detailReturnsSnapshotItemsAndStatus() {
        when(salesOrderMapper.selectById(7L)).thenReturn(order(7L, USER, "SHIPPED"));
        when(salesOrderItemMapper.selectList(any())).thenReturn(List.of(item(21L, 2), item(22L, 1)));

        OrderService.OrderDetail detail = orderService.detail(USER, "7");

        assertEquals(OrderStatus.SHIPPED, detail.status());
        assertEquals(3, detail.totalQuantity());
        assertEquals(2, detail.items().size());
    }

    @Test
    void detailRejectsNonNumericId() {
        assertThrows(BusinessException.class, () -> orderService.detail(USER, "abc"));
        assertThrows(BusinessException.class, () -> orderService.detail(USER, " "));
    }

    @Test
    void cancelRestoresStockOncePerItemAndMarksCancelled() {
        when(salesOrderMapper.selectById(7L)).thenReturn(order(7L, USER, "PENDING_PAYMENT"));
        when(salesOrderMapper.update(isNull(), any())).thenReturn(1);
        when(salesOrderItemMapper.selectList(any())).thenReturn(List.of(item(21L, 2), item(22L, 1)));

        orderService.cancel(USER, "7");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Wrapper<SalesOrder>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(salesOrderMapper).update(isNull(), captor.capture());
        String sqlSet = captor.getValue().getSqlSet();
        assertTrue(sqlSet.contains("status"), sqlSet);
        assertTrue(sqlSet.contains("NOW()"), sqlSet);
        String where = captor.getValue().getSqlSegment();
        assertTrue(where.contains("status"), where);
        verify(productMapper, times(2)).update(isNull(), any());
    }

    @Test
    void cancelRejectsAlreadyCancelledOrderWithoutRestoringStock() {
        when(salesOrderMapper.selectById(7L)).thenReturn(order(7L, USER, "CANCELLED"));

        BusinessException error = assertThrows(BusinessException.class,
                () -> orderService.cancel(USER, "7"));
        assertTrue(error.getMessage().contains("已取消"));
        verify(salesOrderMapper, never()).update(any(), any());
        verify(productMapper, never()).update(any(), any());
    }

    @Test
    void cancelRejectsPaidOrder() {
        when(salesOrderMapper.selectById(7L)).thenReturn(order(7L, USER, "PAID"));
        assertThrows(BusinessException.class, () -> orderService.cancel(USER, "7"));
        verify(productMapper, never()).update(any(), any());
    }

    @Test
    void cancelRejectsOtherUsersOrder() {
        when(salesOrderMapper.selectById(7L)).thenReturn(order(7L, 2L, "PENDING_PAYMENT"));
        assertThrows(NotFoundException.class, () -> orderService.cancel(USER, "7"));
        verify(salesOrderMapper, never()).update(any(), any());
    }

    @Test
    void cancelStopsWhenStatusTransitionMisses() {
        // 并发支付已把订单移出待支付：状态更新未命中即不恢复库存
        when(salesOrderMapper.selectById(7L)).thenReturn(order(7L, USER, "PENDING_PAYMENT"));
        when(salesOrderMapper.update(isNull(), any())).thenReturn(0);

        assertThrows(BusinessException.class, () -> orderService.cancel(USER, "7"));
        verify(productMapper, never()).update(any(), any());
    }

    @Test
    void queryWrapperSortsByCreatedAtThenIdDescending() {
        when(salesOrderMapper.selectPage(any(), any())).thenReturn(new Page<>(1, 10));
        orderService.list(USER, OrderQuery.parse(null, null, null, null));
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Wrapper<SalesOrder>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(salesOrderMapper).selectPage(any(), captor.capture());
        assertTrue(captor.getValue() instanceof LambdaQueryWrapper);
        String sql = captor.getValue().getSqlSegment();
        assertTrue(sql.contains("ORDER BY"), sql);
        assertTrue(sql.endsWith("DESC"), sql);
    }
}
