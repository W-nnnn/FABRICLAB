package com.fabriclab.admin.model;

/** 账号列表行：字段全部来自查询，订单数与累计金额按 user_id 聚合得到，不做估算。 */
public record UserRow(
        long id, String username, String displayName, String email, String phone,
        String status, String statusLabel, String statusClass, String createdAt,
        long orderCount, String ordersTotal) {
}
