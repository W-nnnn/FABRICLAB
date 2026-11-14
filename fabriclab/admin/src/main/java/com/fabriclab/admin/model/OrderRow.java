package com.fabriclab.admin.model;

/** 订单列表行：金额与状态来自订单快照，发货入口只对待发货订单渲染。 */
public record OrderRow(
        String orderNo, String receiverName, String amount,
        String status, String statusLabel, String statusClass,
        String createdAt, boolean canShip) {
}
