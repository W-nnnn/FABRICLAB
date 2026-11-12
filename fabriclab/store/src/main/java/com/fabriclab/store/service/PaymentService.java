package com.fabriclab.store.service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fabriclab.store.entity.SalesOrder;
import com.fabriclab.store.exception.BusinessException;
import com.fabriclab.store.exception.NotFoundException;
import com.fabriclab.store.mapper.SalesOrderMapper;
import com.fabriclab.store.model.OrderStatus;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 教学模拟支付（FR-P-01）：显式成功与显式失败两个动作，不随机伪装外部支付结果。
 * 成功以「id + 原状态为待支付」的条件更新一次性写入 PAID、MOCK、服务端生成且唯一的凭证
 * 与首次成功时间，因此重复成功不会改写凭证与时间；该条件更新是支付与取消唯一的互斥点，
 * 二者并发只有一种状态迁移成功。
 * 金额只取订单总额，请求参数不参与定价；支付不再扣库存，商品后续改价或下架也不阻止
 * 有效待支付订单付款。失败动作只做归属与状态校验，不写库、不生成凭证、不改变库存，
 * 订单保持待支付可重试或取消。
 */
@Service
public class PaymentService {

    /** 支付方式固定写入数据库约束允许的唯一值。 */
    public static final String METHOD = "MOCK";

    /** 支付页数据：订单与当前状态；金额取 total_amount，不读任何请求参数。 */
    public record PaymentView(SalesOrder order, OrderStatus status) {
    }

    /** 支付结果：是否首次成功与生效凭证；重复成功返回库内首次凭证。 */
    public record PayOutcome(boolean firstTime, String paymentNo) {
    }

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");
    private static final SecureRandom RANDOM = new SecureRandom();

    private final SalesOrderMapper salesOrderMapper;

    public PaymentService(SalesOrderMapper salesOrderMapper) {
        this.salesOrderMapper = salesOrderMapper;
    }

    /** 支付页：非本人或不存在的订单统一 404，不泄露存在性。 */
    public PaymentView prepare(long userId, String rawOrderId) {
        SalesOrder order = requireOwned(userId, parseOrderId(rawOrderId));
        return new PaymentView(order, OrderStatus.of(order.getStatus()));
    }

    /**
     * 模拟支付成功：仅待支付订单生效。已支付订单重复提交返回库内首次凭证与时间，不改写；
     * 已取消或其他状态拒绝。并发成功时条件更新只有一条命中，未命中的请求回读后按重复成功处理。
     */
    public PayOutcome pay(long userId, String rawOrderId) {
        long orderId = parseOrderId(rawOrderId);
        SalesOrder order = requireOwned(userId, orderId);
        OrderStatus status = OrderStatus.of(order.getStatus());
        if (status == OrderStatus.PAID) {
            return new PayOutcome(false, order.getPaymentNo());
        }
        if (!status.payable()) {
            throw new BusinessException("当前状态不能模拟支付");
        }
        String paymentNo = nextPaymentNo();
        // 状态、方式、凭证与首次成功时间在同一条更新中写入，满足付款信息成组约束
        int rows = salesOrderMapper.update(null, new LambdaUpdateWrapper<SalesOrder>()
                .eq(SalesOrder::getId, orderId)
                .eq(SalesOrder::getStatus, OrderStatus.PENDING_PAYMENT.name())
                .set(SalesOrder::getStatus, OrderStatus.PAID.name())
                .set(SalesOrder::getPaymentMethod, METHOD)
                .set(SalesOrder::getPaymentNo, paymentNo)
                .setSql("paid_at = NOW()"));
        if (rows == 1) {
            return new PayOutcome(true, paymentNo);
        }
        // 未命中说明状态已被其他请求改变（并发支付或取消）：回读后按真实状态处理
        SalesOrder latest = requireOwned(userId, orderId);
        OrderStatus latestStatus = OrderStatus.of(latest.getStatus());
        if (latestStatus == OrderStatus.PAID) {
            return new PayOutcome(false, latest.getPaymentNo());
        }
        if (!latestStatus.payable()) {
            throw new BusinessException("当前状态不能模拟支付");
        }
        throw new BusinessException("订单状态已变化，请刷新后重试");
    }

    /**
     * 模拟支付失败：订单仍为待支付，不生成凭证、不改库存、不留任何写入痕迹，
     * 允许再次发起或取消。非待支付状态拒绝，避免把已成交订单当作失败重试。
     */
    public void recordFailure(long userId, String rawOrderId) {
        SalesOrder order = requireOwned(userId, parseOrderId(rawOrderId));
        if (!OrderStatus.of(order.getStatus()).payable()) {
            throw new BusinessException("当前状态不能模拟支付");
        }
    }

    /** 凭证由服务端生成：MOCK + 毫秒时间戳 + 8 位随机十六进制，唯一性由数据库唯一索引兜底。 */
    private String nextPaymentNo() {
        byte[] bytes = new byte[4];
        RANDOM.nextBytes(bytes);
        StringBuilder suffix = new StringBuilder(8);
        for (byte b : bytes) {
            suffix.append(String.format("%02X", b));
        }
        return METHOD + LocalDateTime.now().format(STAMP) + suffix;
    }

    private SalesOrder requireOwned(long userId, long orderId) {
        SalesOrder order = salesOrderMapper.selectById(orderId);
        if (order == null || order.getUserId() == null || order.getUserId() != userId) {
            throw new NotFoundException("内容不存在", "内容不存在或已被移除");
        }
        return order;
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
