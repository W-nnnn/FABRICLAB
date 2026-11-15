package com.fabriclab.admin.model;

/** 咨询分页与四项真实状态计数；分段器数字来自当次 COUNT，不含静态数字。 */
public record ServicePage(
        java.util.List<ServiceRow> rows, long total, int page, int pages, int size,
        long allCount, long openCount, long repliedCount, long closedCount) {
}
