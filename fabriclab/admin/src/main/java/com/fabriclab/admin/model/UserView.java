package com.fabriclab.admin.model;

import java.util.List;

/**
 * 账号详情：只读身份（用户名、角色）与可改资料（显示名、邮箱、电话）分开承载；
 * 订单概览来自 sales_order 的聚合与最近三条，用于核对改资料不影响历史订单快照。
 */
public record UserView(
        long id, String username, String role, String displayName, String email, String phone,
        String status, String statusLabel, String statusClass,
        String createdAt, String updatedAt, long orderCount, String ordersTotal,
        List<OrderBrief> recentOrders) {

    /** 最近订单摘要：订单号、下单时间、金额、状态；只读，不提供改单入口。 */
    public record OrderBrief(String orderNo, String date, String amount,
                             String statusLabel, String statusClass) {
    }
}
