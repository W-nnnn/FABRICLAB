package com.fabriclab.store.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fabriclab.store.entity.CartItem;
import com.fabriclab.store.entity.Product;
import com.fabriclab.store.entity.StoreUser;
import com.fabriclab.store.exception.BusinessException;
import com.fabriclab.store.exception.NotFoundException;
import com.fabriclab.store.mapper.CartItemMapper;
import com.fabriclab.store.mapper.ProductMapper;
import com.fabriclab.store.mapper.StoreUserMapper;
import com.fabriclab.store.model.Quantity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 持久购物车（FR-C-01、FR-C-02）：存库不存 Session，退出与重启不清车。
 * 同账号写操作（加入、改量、移除）先锁用户行互斥，再处理该车；条目版本
 * 不符的提交被拒绝，旧版本不覆盖当前车。车不预占库存、不保存可信价格，
 * 展示时关联商品当前名称、图片、价格与库存并重算小计；下架或数量超库存
 * 的条目保留并标注失效原因，不计入可结算合计。
 */
@Service
public class CartService {

    /** 单车商品种类上限（BR-04）：第 21 种加入被拒绝。 */
    public static final int MAX_KINDS = 20;

    /** 购物车行：条目信息 + 商品当前口径；unavailable 标注失效与原因。 */
    public record CartLine(long itemId, long productId, int quantity, long version,
                           String name, String sku, String coverImage, BigDecimal unitPrice,
                           Integer stock, boolean unavailable, String reason) {

        /** 行小计 = 当前单价 × 数量，两位小数。 */
        public BigDecimal subtotal() {
            return unitPrice.multiply(BigDecimal.valueOf(quantity));
        }
    }

    /** 整车视图：行列表、可结算合计（仅有效条目）、数量总和（含失效条目）。 */
    public record CartView(List<CartLine> lines, BigDecimal payableTotal,
                           int totalQuantity, boolean hasInvalid, boolean empty) {
    }

    private final CartItemMapper cartItemMapper;
    private final ProductMapper productMapper;
    private final StoreUserMapper storeUserMapper;

    public CartService(CartItemMapper cartItemMapper, ProductMapper productMapper,
                       StoreUserMapper storeUserMapper) {
        this.cartItemMapper = cartItemMapper;
        this.productMapper = productMapper;
        this.storeUserMapper = storeUserMapper;
    }

    /** 加入购物车：同款累加为一条记录并返回合并后数量；失败抛业务异常且不写库。 */
    @Transactional(rollbackFor = Exception.class)
    public int addToCart(long userId, String rawProductId, String rawQuantity) {
        long productId = parseProductId(rawProductId);
        int quantity = Quantity.parse(rawQuantity);
        lockUser(userId);
        Product product = productMapper.selectById(productId);
        if (product == null || !ProductService.STATUS_ON_SALE.equals(product.getStatus())) {
            throw new BusinessException("商品不存在或已下架，未加入购物车");
        }
        CartItem existing = cartItemMapper.selectOne(new LambdaQueryWrapper<CartItem>()
                .eq(CartItem::getUserId, userId)
                .eq(CartItem::getProductId, productId));
        if (existing == null) {
            Long kinds = cartItemMapper.selectCount(new LambdaQueryWrapper<CartItem>()
                    .eq(CartItem::getUserId, userId));
            if (kinds != null && kinds >= MAX_KINDS) {
                throw new BusinessException("购物车最多保存 " + MAX_KINDS + " 种商品，未加入购物车");
            }
            requireStock(product, quantity);
            CartItem item = new CartItem();
            item.setUserId(userId);
            item.setProductId(productId);
            item.setQuantity(quantity);
            item.setVersion(0L);
            cartItemMapper.insert(item);
            return quantity;
        }
        int merged = existing.getQuantity() + quantity;
        if (merged > Quantity.MAX) {
            throw new BusinessException("单款数量最多 " + Quantity.MAX + " 件");
        }
        requireStock(product, merged);
        existing.setQuantity(merged);
        existing.setVersion(existing.getVersion() + 1);
        cartItemMapper.updateById(existing);
        return merged;
    }

    /** 绝对修改数量：归属与条目版本校验通过后按新数量覆盖，维护版本。 */
    @Transactional(rollbackFor = Exception.class)
    public void updateQuantity(long userId, String rawItemId, String rawVersion, String rawQuantity) {
        long itemId = parseItemId(rawItemId);
        long expectedVersion = parseVersion(rawVersion);
        int quantity = Quantity.parse(rawQuantity);
        lockUser(userId);
        CartItem item = ownedItem(userId, itemId);
        if (item.getVersion() == null || item.getVersion() != expectedVersion) {
            throw new BusinessException("购物车已在其他页面更新，请刷新后重试");
        }
        Product product = productMapper.selectById(item.getProductId());
        if (product == null || !ProductService.STATUS_ON_SALE.equals(product.getStatus())) {
            throw new BusinessException("该商品已下架或不存在，不能修改数量，可移除该条目");
        }
        requireStock(product, quantity);
        item.setQuantity(quantity);
        item.setVersion(item.getVersion() + 1);
        cartItemMapper.updateById(item);
    }

    /** 移除条目：归属与条目版本校验通过后删除；下架、售罄条目同样可移除。 */
    @Transactional(rollbackFor = Exception.class)
    public void remove(long userId, String rawItemId, String rawVersion) {
        long itemId = parseItemId(rawItemId);
        long expectedVersion = parseVersion(rawVersion);
        lockUser(userId);
        CartItem item = ownedItem(userId, itemId);
        if (item.getVersion() == null || item.getVersion() != expectedVersion) {
            throw new BusinessException("购物车已在其他页面更新，请刷新后重试");
        }
        cartItemMapper.deleteById(itemId);
    }

    /** 本人购物车视图：关联商品当前口径重算小计，失效条目保留并标注原因。 */
    public CartView view(long userId) {
        List<CartItem> items = cartItemMapper.selectList(new LambdaQueryWrapper<CartItem>()
                .eq(CartItem::getUserId, userId)
                .orderByAsc(CartItem::getId));
        if (items.isEmpty()) {
            return new CartView(List.of(), BigDecimal.ZERO.setScale(2), 0, false, true);
        }
        List<CartLine> lines = new ArrayList<>();
        BigDecimal payable = BigDecimal.ZERO.setScale(2);
        int totalQuantity = 0;
        boolean hasInvalid = false;
        for (CartItem item : items) {
            Product product = productMapper.selectById(item.getProductId());
            String name;
            String sku;
            String cover;
            BigDecimal price;
            Integer stock;
            boolean unavailable;
            String reason;
            if (product == null) {
                name = "商品已不存在";
                sku = null;
                cover = null;
                price = BigDecimal.ZERO.setScale(2);
                stock = 0;
                unavailable = true;
                reason = "商品已不存在";
            } else {
                name = product.getName();
                sku = product.getSku();
                cover = product.getCoverImage();
                price = product.getPrice();
                stock = product.getStock() == null ? 0 : product.getStock();
                if (!ProductService.STATUS_ON_SALE.equals(product.getStatus())) {
                    unavailable = true;
                    reason = "商品已下架";
                } else if (item.getQuantity() > stock) {
                    unavailable = true;
                    reason = stock == 0 ? "商品售罄" : "库存不足，仅剩 " + stock + " 件";
                } else {
                    unavailable = false;
                    reason = null;
                }
            }
            totalQuantity += item.getQuantity();
            hasInvalid = hasInvalid || unavailable;
            if (!unavailable) {
                payable = payable.add(price.multiply(BigDecimal.valueOf(item.getQuantity())));
            }
            lines.add(new CartLine(item.getId(), item.getProductId(), item.getQuantity(),
                    item.getVersion(), name, sku, cover, price, stock, unavailable, reason));
        }
        return new CartView(List.copyOf(lines), payable, totalQuantity, hasInvalid, false);
    }

    /** 导航徽标：本人全部条目数量总和，含失效条目；空车为 0。 */
    public int badge(long userId) {
        List<CartItem> items = cartItemMapper.selectList(new LambdaQueryWrapper<CartItem>()
                .eq(CartItem::getUserId, userId));
        return items.stream().mapToInt(item -> item.getQuantity() == null ? 0 : item.getQuantity()).sum();
    }

    /** 同账号购物车写互斥点：先锁用户行，再处理该车（含下单共用此约定）。 */
    private void lockUser(long userId) {
        List<StoreUser> locked = storeUserMapper.selectList(new LambdaQueryWrapper<StoreUser>()
                .eq(StoreUser::getId, userId)
                .last("FOR UPDATE"));
        if (locked.isEmpty()) {
            throw new NotFoundException("内容不存在", "内容不存在或已被移除");
        }
    }

    /** 条目归属校验：非本人条目与不存在的条目统一 404，不泄露他人数据。 */
    private CartItem ownedItem(long userId, long itemId) {
        CartItem item = cartItemMapper.selectById(itemId);
        if (item == null || item.getUserId() == null || !item.getUserId().equals(userId)) {
            throw new NotFoundException("内容不存在", "内容不存在或已被移除");
        }
        return item;
    }

    private void requireStock(Product product, int wanted) {
        int stock = product.getStock() == null ? 0 : product.getStock();
        if (stock == 0) {
            throw new BusinessException("该商品已售罄，未加入购物车");
        }
        if (wanted > stock) {
            throw new BusinessException("库存不足，当前可购 " + stock + " 件");
        }
    }

    private long parseProductId(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new BusinessException("缺少商品 ID");
        }
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException e) {
            throw new BusinessException("商品参数必须是整数 ID");
        }
    }

    private long parseItemId(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new BusinessException("缺少条目 ID");
        }
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException e) {
            throw new BusinessException("条目参数必须是整数 ID");
        }
    }

    private long parseVersion(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new BusinessException("缺少条目版本");
        }
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException e) {
            throw new BusinessException("条目版本必须是整数");
        }
    }
}
