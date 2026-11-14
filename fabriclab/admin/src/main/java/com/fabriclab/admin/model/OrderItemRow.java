package com.fabriclab.admin.model;

/** 明细快照行：款号、名称、单价、数量、小计全部来自成交时快照，只读呈现。 */
public record OrderItemRow(
        String sku, String name, String unitPrice, int quantity, String lineAmount) {
}
