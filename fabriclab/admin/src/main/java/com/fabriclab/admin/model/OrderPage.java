package com.fabriclab.admin.model;

import java.util.List;

/** 订单分页结果与五状态计数：计数全部来自当次查询，供页头与分段器呈现。 */
public record OrderPage(
        List<OrderRow> rows, long total, int page, int pages, int size,
        long allCount, long pendingCount, long paidCount,
        long shippedCount, long completedCount, long cancelledCount) {
}
