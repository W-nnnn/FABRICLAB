package com.fabriclab.admin.model;

import java.math.BigDecimal;

/** 列表行：视图所需字段全部来自查询，不含任何估算值。 */
public record ProductRow(
        long id, String sku, String name, String fabricLine, String categoryName,
        BigDecimal price, int stock, String status, String statusLabel, String statusClass,
        String updatedAt, String shape, String tone) {
}
