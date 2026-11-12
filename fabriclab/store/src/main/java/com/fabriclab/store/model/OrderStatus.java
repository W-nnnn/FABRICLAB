package com.fabriclab.store.model;

/**
 * 订单五状态与界面名称（FR-O-03）：待支付、待发货、已发货、已完成、已取消。
 * 不使用「配送中」「待收货」等伪装物流追踪的名称；状态迁移只由后端重查状态后执行，
 * 不依赖按钮隐藏。
 */
public enum OrderStatus {

    PENDING_PAYMENT("待支付"),
    PAID("待发货"),
    SHIPPED("已发货"),
    COMPLETED("已完成"),
    CANCELLED("已取消");

    private final String label;

    OrderStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /** 待支付订单可模拟支付与取消；其余状态只提供合法操作。 */
    public boolean cancellable() {
        return this == PENDING_PAYMENT;
    }

    /** 可发起模拟支付的状态只有待支付；已支付订单的重复提交按首次凭证处理，不在此列。 */
    public boolean payable() {
        return this == PENDING_PAYMENT;
    }

    /** 数据库状态值转枚举；未知值按技术异常处理，不静默降级为某状态。 */
    public static OrderStatus of(String raw) {
        for (OrderStatus status : values()) {
            if (status.name().equals(raw)) {
                return status;
            }
        }
        throw new IllegalStateException("未知的订单状态：" + raw);
    }
}
