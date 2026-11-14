package com.fabriclab.admin.model;

import java.util.List;

/** 账号分页结果与三项计数：计数全部来自当次查询，供页头与分段器呈现。 */
public record UserPage(
        List<UserRow> rows, long total, int page, int pages, int size,
        long allCount, long enabledCount, long disabledCount) {
}
