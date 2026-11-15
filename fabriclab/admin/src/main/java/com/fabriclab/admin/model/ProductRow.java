package com.fabriclab.admin.model;

import java.math.BigDecimal;

/** 列表行：视图所需字段全部来自查询，不含任何估算值。coverImage 为真实封面相对路径。 */
public record ProductRow(
        long id, String sku, String name, String fabricLine, String categoryName,
        BigDecimal price, int stock, String status, String statusLabel, String statusClass,
        String updatedAt, String coverImage, String shape, String tone) {
}
