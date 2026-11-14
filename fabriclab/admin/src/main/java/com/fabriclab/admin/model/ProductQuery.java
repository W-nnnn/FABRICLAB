package com.fabriclab.admin.model;

/** 列表查询条件：筛选值只在服务层白名单内，页码与页大小由服务层约束。 */
public record ProductQuery(String kw, String filter, int page, int size) {
    public static ProductQuery of(String kw, String filter, Integer page, Integer size) {
        String q = kw == null ? "" : kw.trim();
        String f = filter == null || filter.isBlank() ? "all" : filter.trim();
        int p = page == null || page < 1 ? 1 : page;
        int s = size == null ? 20 : size;
        return new ProductQuery(q, f, p, s);
    }
}
