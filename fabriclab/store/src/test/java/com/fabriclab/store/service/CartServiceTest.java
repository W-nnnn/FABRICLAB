package com.fabriclab.store.service;

import com.fabriclab.store.entity.CartItem;
import com.fabriclab.store.entity.Product;
import com.fabriclab.store.entity.StoreUser;
import com.fabriclab.store.exception.BusinessException;
import com.fabriclab.store.exception.NotFoundException;
import com.fabriclab.store.mapper.CartItemMapper;
import com.fabriclab.store.mapper.ProductMapper;
import com.fabriclab.store.mapper.StoreUserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 购物车规则单元测试（FR-C-01、FR-C-02、BR-04）：同款累加、1—99、
 * 最多 20 种、库存与上架校验、条目版本与归属校验、失效判定与合计。
 */
class CartServiceTest {

    private static final long USER = 1L;

    private CartItemMapper cartItemMapper;
    private ProductMapper productMapper;
    private StoreUserMapper storeUserMapper;
    private CartService cartService;

    @BeforeEach
    void setUp() {
        cartItemMapper = mock(CartItemMapper.class);
        productMapper = mock(ProductMapper.class);
        storeUserMapper = mock(StoreUserMapper.class);
        cartService = new CartService(cartItemMapper, productMapper, storeUserMapper);
        when(storeUserMapper.selectList(any())).thenReturn(List.of(new StoreUser()));
    }

    private Product product(long id, String status, int stock, String price) {
        Product product = new Product();
        product.setId(id);
        product.setName("亚麻衬衫");
        product.setSku("FL-000-01");
        product.setStatus(status);
        product.setStock(stock);
        product.setPrice(new BigDecimal(price));
        return product;
    }

    private CartItem item(long id, long userId, long productId, int quantity, long version) {
        CartItem item = new CartItem();
        item.setId(id);
        item.setUserId(userId);
        item.setProductId(productId);
        item.setQuantity(quantity);
        item.setVersion(version);
        return item;
    }

    @Test
    void addToCartInsertsNewItemWithVersionZero() {
        when(productMapper.selectById(10L)).thenReturn(product(10, ProductService.STATUS_ON_SALE, 5, "199.00"));
        when(cartItemMapper.selectOne(any())).thenReturn(null);
        when(cartItemMapper.selectCount(any())).thenReturn(0L);

        int merged = cartService.addToCart(USER, "10", "2");

        assertEquals(2, merged);
        ArgumentCaptor<CartItem> captor = ArgumentCaptor.forClass(CartItem.class);
        verify(cartItemMapper).insert(captor.capture());
        assertEquals(2, captor.getValue().getQuantity());
        assertEquals(0L, captor.getValue().getVersion());
    }

    @Test
    void addToCartMergesSameProductIntoSingleRecord() {
        when(productMapper.selectById(10L)).thenReturn(product(10, ProductService.STATUS_ON_SALE, 10, "199.00"));
        when(cartItemMapper.selectOne(any())).thenReturn(item(5, USER, 10, 3, 2L));

        int merged = cartService.addToCart(USER, "10", "2");

        assertEquals(5, merged);
        verify(cartItemMapper, never()).insert(any(CartItem.class));
        ArgumentCaptor<CartItem> captor = ArgumentCaptor.forClass(CartItem.class);
        verify(cartItemMapper).updateById(captor.capture());
        assertEquals(5, captor.getValue().getQuantity());
        assertEquals(3L, captor.getValue().getVersion(), "合并数量必须维护条目版本");
    }

    @Test
    void addToCartRejectsMergedQuantityOver99WithoutWriting() {
        when(productMapper.selectById(10L)).thenReturn(product(10, ProductService.STATUS_ON_SALE, 200, "199.00"));
        when(cartItemMapper.selectOne(any())).thenReturn(item(5, USER, 10, 99, 1L));

        BusinessException e = assertThrows(BusinessException.class,
                () -> cartService.addToCart(USER, "10", "1"));
        assertTrue(e.getMessage().contains("99"));
        verify(cartItemMapper, never()).updateById(any(CartItem.class));
    }

    @Test
    void addToCartRejectsTwentyFirstKind() {
        when(productMapper.selectById(10L)).thenReturn(product(10, ProductService.STATUS_ON_SALE, 10, "199.00"));
        when(cartItemMapper.selectOne(any())).thenReturn(null);
        when(cartItemMapper.selectCount(any())).thenReturn(20L);

        BusinessException e = assertThrows(BusinessException.class,
                () -> cartService.addToCart(USER, "10", "1"));
        assertTrue(e.getMessage().contains("20"));
        verify(cartItemMapper, never()).insert(any(CartItem.class));
    }

    @Test
    void addToCartRejectsOffSaleAndSoldOutAndInsufficientStock() {
        when(productMapper.selectById(10L)).thenReturn(product(10, "OFF_SALE", 10, "199.00"));
        assertThrows(BusinessException.class, () -> cartService.addToCart(USER, "10", "1"));

        when(productMapper.selectById(10L)).thenReturn(product(10, ProductService.STATUS_ON_SALE, 0, "199.00"));
        assertThrows(BusinessException.class, () -> cartService.addToCart(USER, "10", "1"));

        when(productMapper.selectById(10L)).thenReturn(product(10, ProductService.STATUS_ON_SALE, 3, "199.00"));
        BusinessException e = assertThrows(BusinessException.class, () -> cartService.addToCart(USER, "10", "4"));
        assertTrue(e.getMessage().contains("3"));
        verify(cartItemMapper, never()).insert(any(CartItem.class));
    }

    @Test
    void addToCartRejectsInvalidQuantityAndMissingProduct() {
        assertThrows(BusinessException.class, () -> cartService.addToCart(USER, "10", "0"));
        assertThrows(BusinessException.class, () -> cartService.addToCart(USER, "10", "-1"));
        assertThrows(BusinessException.class, () -> cartService.addToCart(USER, "10", "1.5"));
        assertThrows(BusinessException.class, () -> cartService.addToCart(USER, "10", "100"));
        when(productMapper.selectById(10L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> cartService.addToCart(USER, "10", "1"));
        verify(cartItemMapper, never()).insert(any(CartItem.class));
    }

    @Test
    void updateQuantityRewritesQuantityAndBumpsVersion() {
        when(cartItemMapper.selectById(5L)).thenReturn(item(5, USER, 10, 3, 2L));
        when(productMapper.selectById(10L)).thenReturn(product(10, ProductService.STATUS_ON_SALE, 10, "199.00"));

        cartService.updateQuantity(USER, "5", "2", "7");

        ArgumentCaptor<CartItem> captor = ArgumentCaptor.forClass(CartItem.class);
        verify(cartItemMapper).updateById(captor.capture());
        assertEquals(7, captor.getValue().getQuantity());
        assertEquals(3L, captor.getValue().getVersion());
    }

    @Test
    void updateQuantityRejectsStaleVersionWithoutOverwriting() {
        when(cartItemMapper.selectById(5L)).thenReturn(item(5, USER, 10, 3, 4L));

        BusinessException e = assertThrows(BusinessException.class,
                () -> cartService.updateQuantity(USER, "5", "2", "7"));
        assertTrue(e.getMessage().contains("刷新"));
        verify(cartItemMapper, never()).updateById(any(CartItem.class));
    }

    @Test
    void updateQuantityRejectsForeignAndMissingItemAsNotFound() {
        when(cartItemMapper.selectById(6L)).thenReturn(item(6, 2L, 10, 1, 0L));
        assertThrows(NotFoundException.class, () -> cartService.updateQuantity(USER, "6", "0", "2"));
        when(cartItemMapper.selectById(9L)).thenReturn(null);
        assertThrows(NotFoundException.class, () -> cartService.updateQuantity(USER, "9", "0", "2"));
    }

    @Test
    void updateQuantityRejectsInvalidQuantityAndOffSaleProduct() {
        when(cartItemMapper.selectById(5L)).thenReturn(item(5, USER, 10, 3, 2L));
        assertThrows(BusinessException.class, () -> cartService.updateQuantity(USER, "5", "2", "0"));
        assertThrows(BusinessException.class, () -> cartService.updateQuantity(USER, "5", "2", "100"));
        when(productMapper.selectById(10L)).thenReturn(product(10, "OFF_SALE", 10, "199.00"));
        assertThrows(BusinessException.class, () -> cartService.updateQuantity(USER, "5", "2", "2"));
        verify(cartItemMapper, never()).updateById(any(CartItem.class));
    }

    @Test
    void removeDeletesOwnedItemWithMatchingVersion() {
        when(cartItemMapper.selectById(5L)).thenReturn(item(5, USER, 10, 3, 2L));

        cartService.remove(USER, "5", "2");

        verify(cartItemMapper).deleteById(5L);
    }

    @Test
    void removeRejectsStaleVersionAndForeignItem() {
        when(cartItemMapper.selectById(5L)).thenReturn(item(5, USER, 10, 3, 2L));
        assertThrows(BusinessException.class, () -> cartService.remove(USER, "5", "1"));
        when(cartItemMapper.selectById(6L)).thenReturn(item(6, 2L, 10, 1, 0L));
        assertThrows(NotFoundException.class, () -> cartService.remove(USER, "6", "0"));
        verify(cartItemMapper, never()).deleteById(any(Long.class));
    }

    @Test
    void viewComputesSubtotalsTotalsAndInvalidReasons() {
        when(cartItemMapper.selectList(any())).thenReturn(List.of(
                item(1, USER, 10, 2, 0L),
                item(2, USER, 11, 5, 0L),
                item(3, USER, 12, 4, 0L)));
        when(productMapper.selectById(10L)).thenReturn(product(10, ProductService.STATUS_ON_SALE, 10, "100.00"));
        when(productMapper.selectById(11L)).thenReturn(product(11, "OFF_SALE", 10, "200.00"));
        when(productMapper.selectById(12L)).thenReturn(product(12, ProductService.STATUS_ON_SALE, 2, "50.00"));

        CartService.CartView view = cartService.view(USER);

        assertEquals(3, view.lines().size());
        assertTrue(view.hasInvalid());
        assertEquals(11, view.totalQuantity(), "数量总和含失效条目");
        // 仅有效条目计价：2 × 100.00；下架行不计，库存不足行不计
        assertEquals(0, new BigDecimal("200.00").compareTo(view.payableTotal()));
        assertEquals("商品已下架", view.lines().get(1).reason());
        assertEquals("库存不足，仅剩 2 件", view.lines().get(2).reason());
        assertEquals(0, new BigDecimal("200.00").compareTo(view.lines().get(0).subtotal()));
    }

    @Test
    void viewMarksSoldOutAndMissingProduct() {
        when(cartItemMapper.selectList(any())).thenReturn(List.of(
                item(1, USER, 10, 1, 0L),
                item(2, USER, 11, 1, 0L)));
        when(productMapper.selectById(10L)).thenReturn(product(10, ProductService.STATUS_ON_SALE, 0, "100.00"));
        when(productMapper.selectById(11L)).thenReturn(null);

        CartService.CartView view = cartService.view(USER);

        assertEquals("商品售罄", view.lines().get(0).reason());
        assertEquals("商品已不存在", view.lines().get(1).reason());
        assertNull(view.lines().get(1).sku());
        assertTrue(view.hasInvalid());
    }

    @Test
    void viewOfEmptyCartIsZeroAndEmpty() {
        when(cartItemMapper.selectList(any())).thenReturn(List.of());

        CartService.CartView view = cartService.view(USER);

        assertTrue(view.empty());
        assertEquals(0, view.totalQuantity());
        assertFalse(view.hasInvalid());
        assertEquals(0, BigDecimal.ZERO.compareTo(view.payableTotal()));
    }

    @Test
    void badgeSumsAllQuantitiesIncludingInvalid() {
        when(cartItemMapper.selectList(any())).thenReturn(List.of(
                item(1, USER, 10, 2, 0L),
                item(2, USER, 11, 5, 0L)));

        assertEquals(7, cartService.badge(USER));
        when(cartItemMapper.selectList(any())).thenReturn(List.of());
        assertEquals(0, cartService.badge(USER));
    }

    @Test
    void writesLockUserRowFirstForSameAccountMutualExclusion() {
        when(productMapper.selectById(10L)).thenReturn(product(10, ProductService.STATUS_ON_SALE, 5, "199.00"));
        when(cartItemMapper.selectOne(any())).thenReturn(null);
        when(cartItemMapper.selectCount(any())).thenReturn(0L);

        cartService.addToCart(USER, "10", "1");

        // 同账号写操作互斥点：先锁用户行（SELECT ... FOR UPDATE），再处理该车
        verify(storeUserMapper).selectList(any());
    }
}
