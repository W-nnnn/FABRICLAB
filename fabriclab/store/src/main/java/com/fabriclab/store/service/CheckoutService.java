package com.fabriclab.store.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fabriclab.store.entity.CartItem;
import com.fabriclab.store.entity.Product;
import com.fabriclab.store.entity.SalesOrder;
import com.fabriclab.store.entity.SalesOrderItem;
import com.fabriclab.store.entity.StoreUser;
import com.fabriclab.store.exception.BusinessException;
import com.fabriclab.store.exception.NotFoundException;
import com.fabriclab.store.mapper.CartItemMapper;
import com.fabriclab.store.mapper.ProductMapper;
import com.fabriclab.store.mapper.SalesOrderItemMapper;
import com.fabriclab.store.mapper.SalesOrderMapper;
import com.fabriclab.store.mapper.StoreUserMapper;
import com.fabriclab.store.model.CheckoutConfirmation;
import com.fabriclab.store.model.OrderStatus;
import com.fabriclab.store.model.ReceiverInfo;
import com.fabriclab.store.service.CartService.CartView;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 结算确认与整车下单（FR-O-01、FR-O-02，BR-06—BR-09、BR-12、BR-13）。
 * 确认页金额全部由后端按数据库当前车与当前价计算，打开确认不锁价、不预占库存；
 * 提交时重新读取本人全车，逐项核对确认快照、商品状态、数量、库存与价格，
 * 任一不符即要求重新确认且不写任何数据。下单在同一事务内完成订单主表、
 * 明细快照、扣库存与清理本次结算条目，任一步失败全部回滚、原车保留。
 */
@Service
public class CheckoutService {

    /** 结算范围：整车结算，不提供勾选或直接购买分支。 */
    public static final String STATUS_PENDING_PAYMENT = OrderStatus.PENDING_PAYMENT.name();

    /** 确认失效的统一提示：车、价格或库存已变，必须重新进入结算确认。 */
    public static final String EXPIRED_MESSAGE = "结算确认已失效，请返回购物车重新进入结算";

    private static final DateTimeFormatter ORDER_NO_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final SecureRandom RANDOM = new SecureRandom();

    /** 确认页数据：当前全车视图 + 本次服务端确认。 */
    public record CheckoutView(CartView cart, CheckoutConfirmation confirmation) {
    }

    private final CartService cartService;
    private final CartItemMapper cartItemMapper;
    private final ProductMapper productMapper;
    private final SalesOrderMapper salesOrderMapper;
    private final SalesOrderItemMapper salesOrderItemMapper;
    private final StoreUserMapper storeUserMapper;
    private final CheckoutFaultPoint faultPoint;

    public CheckoutService(CartService cartService, CartItemMapper cartItemMapper,
                           ProductMapper productMapper, SalesOrderMapper salesOrderMapper,
                           SalesOrderItemMapper salesOrderItemMapper, StoreUserMapper storeUserMapper,
                           CheckoutFaultPoint faultPoint) {
        this.cartService = cartService;
        this.cartItemMapper = cartItemMapper;
        this.productMapper = productMapper;
        this.salesOrderMapper = salesOrderMapper;
        this.salesOrderItemMapper = salesOrderItemMapper;
        this.storeUserMapper = storeUserMapper;
        this.faultPoint = faultPoint;
    }

    /**
     * 打开结算确认：按本人数据库车与当前价生成确认快照，空车或存在失效条目时拒绝进入。
     * 只读操作，不锁价、不预占库存、不写库。
     */
    public CheckoutView prepare(long userId) {
        CartView cart = cartService.view(userId);
        if (cart.empty()) {
            throw new BusinessException("购物车是空的，不能进入结算");
        }
        if (cart.hasInvalid()) {
            throw new BusinessException("购物车包含不可结算条目，请先移除或修正后再结算");
        }
        List<CheckoutConfirmation.Line> lines = cart.lines().stream()
                .map(line -> new CheckoutConfirmation.Line(line.itemId(), line.productId(),
                        line.quantity(), line.version(), line.unitPrice()))
                .toList();
        return new CheckoutView(cart, new CheckoutConfirmation(userId, lines, cart.payableTotal()));
    }

    /**
     * 整车下单：同事务完成订单、明细快照、扣库存与清理本次结算条目。
     * 并发提交同一车时后到请求读到空车或确认失效而拒绝，不重复成交。
     */
    @Transactional(rollbackFor = Exception.class)
    public long placeOrder(long userId, CheckoutConfirmation confirmation, ReceiverInfo receiver) {
        if (confirmation == null || confirmation.userId() != userId) {
            throw new BusinessException(EXPIRED_MESSAGE);
        }
        lockAccount(userId);
        List<CartItem> current = cartItemMapper.selectList(new LambdaQueryWrapper<CartItem>()
                .eq(CartItem::getUserId, userId)
                .orderByAsc(CartItem::getId)
                .last("FOR UPDATE"));
        if (current.isEmpty()) {
            throw new BusinessException(EXPIRED_MESSAGE);
        }
        Map<Long, CartItem> byId = new HashMap<>();
        current.forEach(item -> byId.put(item.getId(), item));
        if (current.size() != confirmation.lines().size()) {
            throw new BusinessException("购物车已变化，请重新进入结算确认");
        }

        // 逐项核对确认快照与当前车，并读取商品当前价格、状态与库存
        List<SalesOrderItem> drafts = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO.setScale(2);
        for (CheckoutConfirmation.Line line : confirmation.lines()) {
            CartItem item = byId.get(line.itemId());
            if (item == null || item.getProductId() != line.productId()
                    || item.getQuantity() == null || item.getQuantity() != line.quantity()
                    || item.getVersion() == null || item.getVersion() != line.version()) {
                throw new BusinessException("购物车已变化，请重新进入结算确认");
            }
            Product product = productMapper.selectById(line.productId());
            if (product == null || !ProductService.STATUS_ON_SALE.equals(product.getStatus())) {
                throw new BusinessException("购物车中有商品已下架，请重新确认");
            }
            int stock = product.getStock() == null ? 0 : product.getStock();
            if (stock < line.quantity()) {
                throw new BusinessException(stock == 0
                        ? "购物车中有商品已售罄，请重新确认"
                        : "购物车中有商品库存不足，当前仅剩 " + stock + " 件");
            }
            if (product.getPrice() == null || product.getPrice().compareTo(line.unitPrice()) != 0) {
                throw new BusinessException("购物车中有商品价格已变化，请重新确认");
            }
            BigDecimal lineAmount = line.unitPrice().multiply(BigDecimal.valueOf(line.quantity()));
            total = total.add(lineAmount);
            drafts.add(snapshot(product, line.quantity(), line.unitPrice(), lineAmount));
        }

        SalesOrder order = new SalesOrder();
        order.setOrderNo(nextOrderNo());
        order.setUserId(userId);
        order.setStatus(STATUS_PENDING_PAYMENT);
        order.setTotalAmount(total);
        order.setReceiverName(receiver.name());
        order.setReceiverPhone(receiver.phone());
        order.setReceiverAddress(receiver.address());
        salesOrderMapper.insert(order);
        for (SalesOrderItem draft : drafts) {
            draft.setOrderId(order.getId());
            salesOrderItemMapper.insert(draft);
        }

        // 扣减可销售库存并维护商品版本：条件更新保证库存不为负，未命中即整体回滚
        for (CheckoutConfirmation.Line line : confirmation.lines()) {
            int rows = productMapper.update(null, new UpdateWrapper<Product>()
                    .setSql("stock = stock - " + line.quantity())
                    .setSql("version = version + 1")
                    .eq("id", line.productId())
                    .ge("stock", line.quantity()));
            if (rows != 1) {
                throw new BusinessException("商品库存已变化，请重新确认");
            }
        }

        faultPoint.afterStockDeducted(order.getId());

        int deleted = cartItemMapper.delete(new LambdaQueryWrapper<CartItem>()
                .eq(CartItem::getUserId, userId)
                .in(CartItem::getId, confirmation.lines().stream()
                        .map(CheckoutConfirmation.Line::itemId).toList()));
        if (deleted != confirmation.lines().size()) {
            throw new BusinessException("购物车已变化，请重新进入结算确认");
        }
        return order.getId();
    }

    /** 明细快照：款号、名称、封面、单价、数量与行金额均取成交时值。 */
    private SalesOrderItem snapshot(Product product, int quantity, BigDecimal unitPrice, BigDecimal lineAmount) {
        SalesOrderItem item = new SalesOrderItem();
        item.setProductId(product.getId());
        item.setSkuSnapshot(product.getSku());
        item.setProductNameSnapshot(product.getName());
        item.setCoverImageSnapshot(product.getCoverImage());
        item.setUnitPrice(unitPrice);
        item.setQuantity(quantity);
        item.setLineAmount(lineAmount);
        return item;
    }

    /** 订单号由服务端生成且唯一（BR-13），不以页面编号或最大编号加一。 */
    private String nextOrderNo() {
        return "FL" + LocalDateTime.now().format(ORDER_NO_TIME)
                + String.format("%06d", RANDOM.nextInt(1_000_000));
    }

    /** 同账号写互斥点：与购物车写操作共用先锁用户行的约定。 */
    private void lockAccount(long userId) {
        List<StoreUser> locked = storeUserMapper.selectList(new LambdaQueryWrapper<StoreUser>()
                .eq(StoreUser::getId, userId)
                .last("FOR UPDATE"));
        if (locked.isEmpty()) {
            throw new NotFoundException("内容不存在", "内容不存在或已被移除");
        }
    }
}
