package com.fabriclab.store.service;

import com.fabriclab.store.entity.CartItem;
import com.fabriclab.store.entity.Product;
import com.fabriclab.store.entity.SalesOrder;
import com.fabriclab.store.entity.SalesOrderItem;
import com.fabriclab.store.entity.StoreUser;
import com.fabriclab.store.exception.BusinessException;
import com.fabriclab.store.mapper.CartItemMapper;
import com.fabriclab.store.mapper.ProductMapper;
import com.fabriclab.store.mapper.SalesOrderItemMapper;
import com.fabriclab.store.mapper.SalesOrderMapper;
import com.fabriclab.store.mapper.StoreUserMapper;
import com.fabriclab.store.model.CheckoutConfirmation;
import com.fabriclab.store.model.ReceiverInfo;
import com.fabriclab.store.service.CartService.CartLine;
import com.fabriclab.store.service.CartService.CartView;
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
 * 结算与整车下单规则（FR-O-01、FR-O-02、BR-06—BR-09）：确认只读不锁价、
 * 确认快照逐项核对、价格与库存与状态变化即拒绝、同成同败的写库序列。
 */
class CheckoutServiceTest {

    private static final long USER = 1L;
    private static final ReceiverInfo RECEIVER = ReceiverInfo.parse("林未", "13800001234", "上海市静安区某路 1 号");

    private CartService cartService;
    private CartItemMapper cartItemMapper;
    private ProductMapper productMapper;
    private SalesOrderMapper salesOrderMapper;
    private SalesOrderItemMapper salesOrderItemMapper;
    private StoreUserMapper storeUserMapper;
    private CheckoutService checkoutService;

    @BeforeEach
    void setUp() {
        cartService = mock(CartService.class);
        cartItemMapper = mock(CartItemMapper.class);
        productMapper = mock(ProductMapper.class);
        salesOrderMapper = mock(SalesOrderMapper.class);
        salesOrderItemMapper = mock(SalesOrderItemMapper.class);
        storeUserMapper = mock(StoreUserMapper.class);
        checkoutService = new CheckoutService(cartService, cartItemMapper, productMapper,
                salesOrderMapper, salesOrderItemMapper, storeUserMapper, new CheckoutFaultPoint());
        when(storeUserMapper.selectList(any())).thenReturn(List.of(new StoreUser()));
        when(salesOrderMapper.insert(any(SalesOrder.class))).thenAnswer(invocation -> {
            invocation.getArgument(0, SalesOrder.class).setId(99L);
            return 1;
        });
    }

    private CartLine line(long itemId, long productId, int quantity, long version, String price) {
        return new CartLine(itemId, productId, quantity, version, "亚麻衬衫", "FL-000-01",
                "images/demo.jpg", new BigDecimal(price), 10, false, null);
    }

    private CartItem cartItem(long id, long productId, int quantity, long version) {
        CartItem item = new CartItem();
        item.setId(id);
        item.setUserId(USER);
        item.setProductId(productId);
        item.setQuantity(quantity);
        item.setVersion(version);
        return item;
    }

    private Product product(long id, String status, int stock, String price) {
        Product product = new Product();
        product.setId(id);
        product.setName("亚麻衬衫");
        product.setSku("FL-000-01");
        product.setCoverImage("images/demo.jpg");
        product.setStatus(status);
        product.setStock(stock);
        product.setPrice(new BigDecimal(price));
        return product;
    }

    private CheckoutConfirmation confirmation(CheckoutConfirmation.Line... lines) {
        BigDecimal total = BigDecimal.ZERO.setScale(2);
        for (CheckoutConfirmation.Line line : lines) {
            total = total.add(line.unitPrice().multiply(BigDecimal.valueOf(line.quantity())));
        }
        return new CheckoutConfirmation(USER, List.of(lines), total);
    }

    @Test
    void prepareRejectsEmptyCart() {
        when(cartService.view(USER)).thenReturn(new CartView(List.of(), BigDecimal.ZERO.setScale(2), 0, false, true));
        assertThrows(BusinessException.class, () -> checkoutService.prepare(USER));
    }

    @Test
    void prepareRejectsCartWithInvalidLine() {
        CartLine invalid = new CartLine(11L, 21L, 2, 0, "亚麻衬衫", "FL-000-01",
                "images/demo.jpg", new BigDecimal("199.00"), 0, true, "商品售罄");
        when(cartService.view(USER)).thenReturn(new CartView(List.of(invalid), BigDecimal.ZERO.setScale(2), 2, true, false));
        BusinessException error = assertThrows(BusinessException.class, () -> checkoutService.prepare(USER));
        assertTrue(error.getMessage().contains("不可结算条目"));
    }

    @Test
    void prepareBuildsConfirmationFromCurrentCart() {
        when(cartService.view(USER)).thenReturn(new CartView(
                List.of(line(11, 21, 2, 3, "199.00"), line(12, 22, 1, 0, "58.00")),
                new BigDecimal("456.00"), 3, false, false));

        CheckoutService.CheckoutView view = checkoutService.prepare(USER);

        assertEquals(2, view.confirmation().lines().size());
        assertEquals(USER, view.confirmation().userId());
        assertEquals(new BigDecimal("456.00"), view.confirmation().totalAmount());
        assertEquals(3, view.confirmation().lines().get(0).version());
        // 确认页只读：不写库、不预占库存
        verify(salesOrderMapper, never()).insert(any(SalesOrder.class));
    }

    @Test
    void placeOrderRejectsMissingConfirmation() {
        assertThrows(BusinessException.class,
                () -> checkoutService.placeOrder(USER, null, RECEIVER));
    }

    @Test
    void placeOrderRejectsConfirmationOfAnotherUser() {
        CheckoutConfirmation other = new CheckoutConfirmation(2L,
                List.of(new CheckoutConfirmation.Line(11, 21, 1, 0, new BigDecimal("199.00"))),
                new BigDecimal("199.00"));
        assertThrows(BusinessException.class, () -> checkoutService.placeOrder(USER, other, RECEIVER));
        verify(salesOrderMapper, never()).insert(any(SalesOrder.class));
    }

    @Test
    void placeOrderRejectsEmptyCart() {
        when(cartItemMapper.selectList(any())).thenReturn(List.of());
        CheckoutConfirmation conf = confirmation(new CheckoutConfirmation.Line(11, 21, 1, 0, new BigDecimal("199.00")));
        assertThrows(BusinessException.class, () -> checkoutService.placeOrder(USER, conf, RECEIVER));
    }

    @Test
    void placeOrderRejectsCartChangedByAnotherSession() {
        // 另一会话新增了一款商品：条目数不一致即拒绝，且不删除任何条目
        when(cartItemMapper.selectList(any())).thenReturn(List.of(
                cartItem(11, 21, 2, 0), cartItem(12, 22, 1, 0)));
        CheckoutConfirmation conf = confirmation(new CheckoutConfirmation.Line(11, 21, 2, 0, new BigDecimal("199.00")));

        BusinessException error = assertThrows(BusinessException.class,
                () -> checkoutService.placeOrder(USER, conf, RECEIVER));
        assertTrue(error.getMessage().contains("重新进入结算确认"));
        verify(cartItemMapper, never()).delete(any());
        verify(salesOrderMapper, never()).insert(any(SalesOrder.class));
    }

    @Test
    void placeOrderRejectsStaleItemVersion() {
        when(cartItemMapper.selectList(any())).thenReturn(List.of(cartItem(11, 21, 2, 1)));
        CheckoutConfirmation conf = confirmation(new CheckoutConfirmation.Line(11, 21, 2, 0, new BigDecimal("199.00")));

        assertThrows(BusinessException.class, () -> checkoutService.placeOrder(USER, conf, RECEIVER));
        verify(salesOrderMapper, never()).insert(any(SalesOrder.class));
    }

    @Test
    void placeOrderRejectsOffSaleProduct() {
        when(cartItemMapper.selectList(any())).thenReturn(List.of(cartItem(11, 21, 2, 0)));
        when(productMapper.selectById(21L)).thenReturn(product(21, ProductService.STATUS_DRAFT, 10, "199.00"));
        CheckoutConfirmation conf = confirmation(new CheckoutConfirmation.Line(11, 21, 2, 0, new BigDecimal("199.00")));

        BusinessException error = assertThrows(BusinessException.class,
                () -> checkoutService.placeOrder(USER, conf, RECEIVER));
        assertTrue(error.getMessage().contains("已下架"));
        verify(salesOrderMapper, never()).insert(any(SalesOrder.class));
    }

    @Test
    void placeOrderRejectsInsufficientStock() {
        when(cartItemMapper.selectList(any())).thenReturn(List.of(cartItem(11, 21, 2, 0)));
        when(productMapper.selectById(21L)).thenReturn(product(21, ProductService.STATUS_ON_SALE, 1, "199.00"));
        CheckoutConfirmation conf = confirmation(new CheckoutConfirmation.Line(11, 21, 2, 0, new BigDecimal("199.00")));

        BusinessException error = assertThrows(BusinessException.class,
                () -> checkoutService.placeOrder(USER, conf, RECEIVER));
        assertTrue(error.getMessage().contains("库存不足"));
        verify(salesOrderMapper, never()).insert(any(SalesOrder.class));
    }

    @Test
    void placeOrderRejectsChangedPrice() {
        when(cartItemMapper.selectList(any())).thenReturn(List.of(cartItem(11, 21, 2, 0)));
        when(productMapper.selectById(21L)).thenReturn(product(21, ProductService.STATUS_ON_SALE, 10, "219.00"));
        CheckoutConfirmation conf = confirmation(new CheckoutConfirmation.Line(11, 21, 2, 0, new BigDecimal("199.00")));

        BusinessException error = assertThrows(BusinessException.class,
                () -> checkoutService.placeOrder(USER, conf, RECEIVER));
        assertTrue(error.getMessage().contains("价格已变化"));
        verify(salesOrderMapper, never()).insert(any(SalesOrder.class));
    }

    @Test
    void placeOrderWritesOrderSnapshotDeductsStockAndClearsCart() {
        when(cartItemMapper.selectList(any())).thenReturn(List.of(
                cartItem(11, 21, 2, 0), cartItem(12, 22, 1, 0)));
        when(productMapper.selectById(21L)).thenReturn(product(21, ProductService.STATUS_ON_SALE, 10, "199.00"));
        when(productMapper.selectById(22L)).thenReturn(product(22, ProductService.STATUS_ON_SALE, 5, "58.00"));
        when(productMapper.update(isNull(), any())).thenReturn(1);
        when(cartItemMapper.delete(any())).thenReturn(2);
        CheckoutConfirmation conf = confirmation(
                new CheckoutConfirmation.Line(11, 21, 2, 0, new BigDecimal("199.00")),
                new CheckoutConfirmation.Line(12, 22, 1, 0, new BigDecimal("58.00")));

        long orderId = checkoutService.placeOrder(USER, conf, RECEIVER);

        assertEquals(99L, orderId);
        ArgumentCaptor<SalesOrder> orderCaptor = ArgumentCaptor.forClass(SalesOrder.class);
        verify(salesOrderMapper).insert(orderCaptor.capture());
        SalesOrder order = orderCaptor.getValue();
        assertEquals(CheckoutService.STATUS_PENDING_PAYMENT, order.getStatus());
        assertEquals(new BigDecimal("456.00"), order.getTotalAmount());
        assertEquals("林未", order.getReceiverName());
        assertEquals("上海市静安区某路 1 号", order.getReceiverAddress());
        assertTrue(order.getOrderNo().startsWith("FL"));
        assertTrue(order.getOrderNo().length() <= 40);

        ArgumentCaptor<SalesOrderItem> itemCaptor = ArgumentCaptor.forClass(SalesOrderItem.class);
        verify(salesOrderItemMapper, times(2)).insert(itemCaptor.capture());
        assertEquals(99L, itemCaptor.getAllValues().get(0).getOrderId());
        assertEquals("FL-000-01", itemCaptor.getAllValues().get(0).getSkuSnapshot());
        assertEquals(new BigDecimal("398.00"), itemCaptor.getAllValues().get(0).getLineAmount());
        assertEquals(2, itemCaptor.getAllValues().get(0).getQuantity());

        verify(productMapper, times(2)).update(isNull(), any());
        verify(cartItemMapper).delete(any());
    }

    @Test
    void placeOrderFailsWhenStockConditionMisses() {
        when(cartItemMapper.selectList(any())).thenReturn(List.of(cartItem(11, 21, 2, 0)));
        when(productMapper.selectById(21L)).thenReturn(product(21, ProductService.STATUS_ON_SALE, 10, "199.00"));
        when(productMapper.update(isNull(), any())).thenReturn(0);
        CheckoutConfirmation conf = confirmation(new CheckoutConfirmation.Line(11, 21, 2, 0, new BigDecimal("199.00")));

        assertThrows(BusinessException.class, () -> checkoutService.placeOrder(USER, conf, RECEIVER));
        verify(cartItemMapper, never()).delete(any());
    }

    @Test
    void placeOrderFailsWhenCartCleanupCountMismatches() {
        when(cartItemMapper.selectList(any())).thenReturn(List.of(cartItem(11, 21, 2, 0)));
        when(productMapper.selectById(21L)).thenReturn(product(21, ProductService.STATUS_ON_SALE, 10, "199.00"));
        when(productMapper.update(isNull(), any())).thenReturn(1);
        when(cartItemMapper.delete(any())).thenReturn(0);
        CheckoutConfirmation conf = confirmation(new CheckoutConfirmation.Line(11, 21, 2, 0, new BigDecimal("199.00")));

        BusinessException error = assertThrows(BusinessException.class,
                () -> checkoutService.placeOrder(USER, conf, RECEIVER));
        assertTrue(error.getMessage().contains("购物车已变化"));
    }
}
