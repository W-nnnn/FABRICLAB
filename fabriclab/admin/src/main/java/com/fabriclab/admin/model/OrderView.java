package com.fabriclab.admin.model;

import java.util.List;

/**
 * 订单详情：明细、金额、收货与支付信息全部来自成交快照，只读呈现。
 * can* 三个标记由服务层按状态计算：改收货限待支付 / 待发货，发货仅待发货，
 * 删除仅已取消且无支付凭证。
 */
public record OrderView(
        String orderNo, String status, String statusLabel, String statusClass,
        String totalAmount, String buyer,
        String createdAt, List<OrderItemRow> items,
        List<TimelineStep> timeline,
        String receiverName, String receiverPhone, String receiverAddress,
        String paymentMethodLabel, String paymentNo, String paidAt,
        String cancelledAt, String shippedAt, String completedAt,
        boolean canEditReceiver, boolean canShip, boolean canDelete) {

    /** 流转记录节点：done 为已发生，date 为发生时间；未发生节点空心呈现。 */
    public record TimelineStep(String label, String date, boolean done) {
    }
}
