package com.fabriclab.store.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 本人订单查询与取消（FR-O-03、FR-O-04）。
 * 列表与详情仅返回本人数据，归属不符统一 404 不泄露他人订单；排序按
 * created_at、id 倒序，支持五状态筛选、完整订单号精确匹配与分页。
 * 详情金额取明细快照，商品改名、改价、换封面或下架不改写历史订单（BR-15）。
 * 取消仅对本人待支付订单生效，同事务内转 CANCELLED 并按明细恢复库存与版本，
 * 重复取消不再增加库存。
 */
@Service
public class OrderService {

    /** 列表缩略图：下单时的商品名与封面快照，一笔订单最多展示两件。 */
    public record OrderThumb(String name, String image) {
    }

    /** 订单详情：订单快照 + 全部明细 + 状态枚举。 */
    public record OrderDetail(SalesOrder order, List<SalesOrderItem> items, OrderStatus status) {

        /** 明细总件数。 */
        public int totalQuantity() {
            return items.stream().mapToInt(item -> item.getQuantity() == null ? 0 : item.getQuantity()).sum();
        }
    }

    private final SalesOrderMapper salesOrderMapper;
    private final SalesOrderItemMapper salesOrderItemMapper;
    private final ProductMapper productMapper;
    private final StoreUserMapper storeUserMapper;
    private final CancelFaultPoint cancelFaultPoint;

    public OrderService(SalesOrderMapper salesOrderMapper, SalesOrderItemMapper salesOrderItemMapper,
                        ProductMapper productMapper, StoreUserMapper storeUserMapper,
                        CancelFaultPoint cancelFaultPoint) {
        this.salesOrderMapper = salesOrderMapper;
        this.salesOrderItemMapper = salesOrderItemMapper;
        this.productMapper = productMapper;
        this.storeUserMapper = storeUserMapper;
        this.cancelFaultPoint = cancelFaultPoint;
    }

    /** 本人订单分页：状态与订单号共同生效，查不到返回空结果而不是报错。 */
    public Page<SalesOrder> list(long userId, OrderQuery query) {
        LambdaQueryWrapper<SalesOrder> wrapper = new LambdaQueryWrapper<SalesOrder>()
                .eq(SalesOrder::getUserId, userId);
        if (query.status() != null) {
            wrapper.eq(SalesOrder::getStatus, query.status().name());
        }
        if (query.orderNo() != null) {
            wrapper.eq(SalesOrder::getOrderNo, query.orderNo());
        }
        wrapper.orderByDesc(SalesOrder::getCreatedAt).orderByDesc(SalesOrder::getId);
        return salesOrderMapper.selectPage(new Page<>(query.page(), query.size()), wrapper);
    }

    /** 列表缩略图按订单号分组，一次查出整页明细，快照为空时不占位。 */
    public Map<Long, List<OrderThumb>> thumbnails(List<SalesOrder> orders) {
        if (orders == null || orders.isEmpty()) {
            return Map.of();
        }
        Map<Long, List<OrderThumb>> result = new LinkedHashMap<>();
        orders.forEach(order -> result.put(order.getId(), new ArrayList<>()));
        List<SalesOrderItem> items = salesOrderItemMapper.selectList(
                new LambdaQueryWrapper<SalesOrderItem>()
                        .in(SalesOrderItem::getOrderId, result.keySet()));
        for (SalesOrderItem item : items) {
            List<OrderThumb> thumbs = result.get(item.getOrderId());
            if (thumbs == null || thumbs.size() >= 2 || item.getCoverImageSnapshot() == null
                    || item.getCoverImageSnapshot().isBlank()) {
                continue;
            }
            thumbs.add(new OrderThumb(item.getProductNameSnapshot(), item.getCoverImageSnapshot()));
        }
        return result;
    }

    /** 订单详情：非本人或不存在的订单统一 404。 */
    public OrderDetail detail(long userId, String rawOrderId) {
        long orderId = parseOrderId(rawOrderId);
        SalesOrder order = salesOrderMapper.selectById(orderId);
        if (order == null || order.getUserId() == null || order.getUserId() != userId) {
            throw new NotFoundException("内容不存在", "内容不存在或已被移除");
        }
        List<SalesOrderItem> items = salesOrderItemMapper.selectList(
                new LambdaQueryWrapper<SalesOrderItem>()
                        .eq(SalesOrderItem::getOrderId, orderId)
                        .orderByAsc(SalesOrderItem::getId));
        return new OrderDetail(order, items, OrderStatus.of(order.getStatus()));
    }

    /**
     * 取消待支付订单：状态与库存恢复同成同败。重复取消不重复恢复；
     * 已支付及后续状态不能取消；下架商品同样恢复数量并保持下架。
     */
    @Transactional(rollbackFor = Exception.class)
    public void cancel(long userId, String rawOrderId) {
        long orderId = parseOrderId(rawOrderId);
        lockAccount(userId);
        SalesOrder order = salesOrderMapper.selectById(orderId);
        if (order == null || order.getUserId() == null || order.getUserId() != userId) {
            throw new NotFoundException("内容不存在", "内容不存在或已被移除");
        }
        OrderStatus status = OrderStatus.of(order.getStatus());
        if (status == OrderStatus.CANCELLED) {
            throw new BusinessException("订单已取消，无需重复操作");
        }
        if (!status.cancellable()) {
            throw new BusinessException("当前状态不能取消订单");
        }
        // 状态迁移带原状态条件：与模拟支付争用同一待支付状态，只有一种迁移成功
        int rows = salesOrderMapper.update(null, new LambdaUpdateWrapper<SalesOrder>()
                .eq(SalesOrder::getId, orderId)
                .eq(SalesOrder::getStatus, OrderStatus.PENDING_PAYMENT.name())
                .set(SalesOrder::getStatus, OrderStatus.CANCELLED.name())
                .setSql("cancelled_at = NOW()"));
        if (rows != 1) {
            throw new BusinessException("订单状态已变化，请刷新后重试");
        }
        List<SalesOrderItem> items = salesOrderItemMapper.selectList(
                new LambdaQueryWrapper<SalesOrderItem>().eq(SalesOrderItem::getOrderId, orderId));
        for (SalesOrderItem item : items) {
            int quantity = item.getQuantity() == null ? 0 : item.getQuantity();
            productMapper.update(null, new UpdateWrapper<Product>()
                    .setSql("stock = stock + " + quantity)
                    .setSql("version = version + 1")
                    .eq("id", item.getProductId()));
            cancelFaultPoint.afterStockRestored(orderId);
        }
    }

    /** 同账号订单写互斥点：与购物车写、下单共用先锁用户行的约定。 */
    private void lockAccount(long userId) {
        List<StoreUser> locked = storeUserMapper.selectList(new LambdaQueryWrapper<StoreUser>()
                .eq(StoreUser::getId, userId)
                .last("FOR UPDATE"));
        if (locked.isEmpty()) {
            throw new NotFoundException("内容不存在", "内容不存在或已被移除");
        }
    }

    private long parseOrderId(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new BusinessException("缺少订单 ID");
        }
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException e) {
            throw new BusinessException("订单参数必须是整数 ID");
        }
    }
}
