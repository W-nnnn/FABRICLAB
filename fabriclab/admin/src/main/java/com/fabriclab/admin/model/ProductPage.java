package com.fabriclab.admin.model;

import java.util.List;

/** 分页结果与筛选计数：四项计数都来自当次查询，供页头与分段器呈现。 */
public record ProductPage(
        List<ProductRow> rows, long total, int page, int pages, int size,
        long allCount, long liveCount, long draftCount, long soldCount) {
}
