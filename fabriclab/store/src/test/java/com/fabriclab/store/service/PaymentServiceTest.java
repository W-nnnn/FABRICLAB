package com.fabriclab.store.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.fabriclab.store.entity.SalesOrder;
import com.fabriclab.store.exception.BusinessException;
import com.fabriclab.store.exception.NotFoundException;
import com.fabriclab.store.mapper.SalesOrderMapper;
import com.fabriclab.store.model.OrderStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 教学模拟支付规则（FR-P-01）：归属 404、仅待支付可付、成功一次写成组付款信息、
 * 重复成功保留首次凭证与时间、失败不写库、已取消与其他状态拒绝付款、
 * 条件更新是支付与取消的唯一互斥点。
 */
class PaymentServiceTest {

    private static final long USER = 1L;

    private SalesOrderMapper salesOrderMapper;
    private PaymentService paymentService;

    @BeforeEach
    void setUp() {
        salesOrderMapper = mock(SalesOrderMapper.class);
        paymentService = new PaymentService(salesOrderMapper);
    }

    private SalesOrder order(long id, long userId, String status) {
        SalesOrder order = new SalesOrder();
        order.setId(id);
        order.setOrderNo("FL20260101000000000001");
        order.setUserId(userId);
        order.setStatus(status);
        order.setTotalAmount(new BigDecimal("456.00"));
        order.setCreatedAt(LocalDateTime.of(2026, 1, 1, 10, 0));
        if (!"PENDING_PAYMENT".equals(status) && !"CANCELLED".equals(status)) {
            order.setPaymentMethod("MOCK");
            order.setPaymentNo("MOCK20260101100000000AAAAAAAA");
            order.setPaidAt(LocalDateTime.of(2026, 1, 1, 10, 5));
        }
        return order;
    }

    @Test
    void prepareReturnsOwnedPendingOrder() {
        when(salesOrderMapper.selectById(7L)).thenReturn(order(7L, USER, "PENDING_PAYMENT"));

        PaymentService.PaymentView view = paymentService.prepare(USER, "7");

        assertEquals(7L, view.order().getId());
        assertEquals(OrderStatus.PENDING_PAYMENT, view.status());
        assertEquals(new BigDecimal("456.00"), view.order().getTotalAmount());
    }

    @Test
    void prepareOfOtherUsersOrderReturns404() {
        when(salesOrderMapper.selectById(7L)).thenReturn(order(7L, 2L, "PENDING_PAYMENT"));

        assertThrows(NotFoundException.class, () -> paymentService.prepare(USER, "7"));
    }

    @Test
    void payWritesStatusMethodVoucherAndPaidTimeInOneUpdate() {
        when(salesOrderMapper.selectById(7L)).thenReturn(order(7L, USER, "PENDING_PAYMENT"));
        when(salesOrderMapper.update(isNull(), any())).thenReturn(1);

        PaymentService.PayOutcome outcome = paymentService.pay(USER, "7");

        assertTrue(outcome.firstTime());
        assertTrue(outcome.paymentNo().startsWith("MOCK"), outcome.paymentNo());
        assertTrue(outcome.paymentNo().length() <= 40, outcome.paymentNo());
        assertTrue(outcome.paymentNo().substring(4).matches("[0-9]{17}[0-9A-F]{8}"), outcome.paymentNo());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Wrapper<SalesOrder>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(salesOrderMapper).update(isNull(), captor.capture());
        // 设置段的字段名在有无 Spring 上下文时分别为属性名与列名，断言只校验字段存在
        String set = captor.getValue().getSqlSet().toLowerCase();
        assertTrue(set.contains("status"), set);
        assertTrue(set.contains("payment"), "付款方式与凭证同条写入：" + set);
        assertTrue(set.contains("paid_at"), set);
        assertFalse(set.contains("total_amount") || set.contains("totalamount"),
                "金额只取订单总额，不由支付写入：" + set);
        String where = captor.getValue().getSqlSegment();
        assertTrue(where.contains("status"), where);
    }

    @Test
    void repeatSuccessKeepsFirstVoucherAndWritesNothing() {
        SalesOrder paid = order(7L, USER, "PAID");
        when(salesOrderMapper.selectById(7L)).thenReturn(paid);

        PaymentService.PayOutcome outcome = paymentService.pay(USER, "7");

        assertFalse(outcome.firstTime());
        assertEquals(paid.getPaymentNo(), outcome.paymentNo());
        verify(salesOrderMapper, never()).update(any(), any());
    }

    @Test
    void payRejectsCancelledOrder() {
        when(salesOrderMapper.selectById(7L)).thenReturn(order(7L, USER, "CANCELLED"));

        BusinessException error = assertThrows(BusinessException.class,
                () -> paymentService.pay(USER, "7"));
        assertTrue(error.getMessage().contains("不能发起支付"));
        verify(salesOrderMapper, never()).update(any(), any());
    }

    @Test
    void payRejectsShippedOrder() {
        when(salesOrderMapper.selectById(7L)).thenReturn(order(7L, USER, "SHIPPED"));

        assertThrows(BusinessException.class, () -> paymentService.pay(USER, "7"));
        verify(salesOrderMapper, never()).update(any(), any());
    }

    @Test
    void payOfOtherUsersOrderReturns404() {
        when(salesOrderMapper.selectById(7L)).thenReturn(order(7L, 2L, "PENDING_PAYMENT"));

        assertThrows(NotFoundException.class, () -> paymentService.pay(USER, "7"));
        verify(salesOrderMapper, never()).update(any(), any());
    }

    @Test
    void payWhenTransitionMissesAndOrderBecamePaidKeepsFirstVoucher() {
        SalesOrder paid = order(7L, USER, "PAID");
        when(salesOrderMapper.selectById(7L)).thenReturn(order(7L, USER, "PENDING_PAYMENT"), paid);
        when(salesOrderMapper.update(isNull(), any())).thenReturn(0);

        PaymentService.PayOutcome outcome = paymentService.pay(USER, "7");

        assertFalse(outcome.firstTime(), "并发支付已成交时不改写凭证");
        assertEquals(paid.getPaymentNo(), outcome.paymentNo());
    }

    @Test
    void payWhenTransitionMissesAndOrderCancelledRejects() {
        when(salesOrderMapper.selectById(7L))
                .thenReturn(order(7L, USER, "PENDING_PAYMENT"), order(7L, USER, "CANCELLED"));
        when(salesOrderMapper.update(isNull(), any())).thenReturn(0);

        BusinessException error = assertThrows(BusinessException.class,
                () -> paymentService.pay(USER, "7"));
        assertTrue(error.getMessage().contains("不能发起支付"));
    }

    @Test
    void failureActionWritesNothingAndKeepsPending() {
        SalesOrder pending = order(7L, USER, "PENDING_PAYMENT");
        when(salesOrderMapper.selectById(7L)).thenReturn(pending);

        paymentService.recordFailure(USER, "7");

        verify(salesOrderMapper, never()).update(any(), any());
        assertEquals("PENDING_PAYMENT", pending.getStatus());
        assertEquals(null, pending.getPaymentNo());
    }

    @Test
    void failureActionRejectsPaidOrder() {
        when(salesOrderMapper.selectById(7L)).thenReturn(order(7L, USER, "PAID"));

        assertThrows(BusinessException.class, () -> paymentService.recordFailure(USER, "7"));
        verify(salesOrderMapper, never()).update(any(), any());
    }

    @Test
    void failureActionOfOtherUsersOrderReturns404() {
        when(salesOrderMapper.selectById(7L)).thenReturn(order(7L, 2L, "PENDING_PAYMENT"));

        assertThrows(NotFoundException.class, () -> paymentService.recordFailure(USER, "7"));
    }

    @Test
    void voucherDiffersBetweenOrders() {
        when(salesOrderMapper.selectById(7L)).thenReturn(order(7L, USER, "PENDING_PAYMENT"));
        when(salesOrderMapper.selectById(8L)).thenReturn(order(8L, USER, "PENDING_PAYMENT"));
        when(salesOrderMapper.update(isNull(), any())).thenReturn(1);

        String first = paymentService.pay(USER, "7").paymentNo();
        String second = paymentService.pay(USER, "8").paymentNo();

        assertNotEquals(first, second);
    }

    @Test
    void invalidOrderIdRejectedBeforeAnyQuery() {
        assertThrows(BusinessException.class, () -> paymentService.pay(USER, "abc"));
        assertThrows(BusinessException.class, () -> paymentService.pay(USER, " "));
        verify(salesOrderMapper, never()).selectById(any());
    }
}
