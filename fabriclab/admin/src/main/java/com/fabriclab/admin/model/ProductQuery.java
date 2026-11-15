package com.fabriclab.admin.model;

/**
 * 列表查询条件：筛选值、排序字段与方向只在服务层白名单内，页码与页大小由服务层约束。
 * sort 取 updated（缺省）/ price / stock；dir 取 desc（缺省）/ asc。
 */
public record ProductQuery(String kw, String filter, int page, int size, String sort, String dir) {

    public static ProductQuery of(String kw, String filter, Integer page, Integer size) {
        return of(kw, filter, page, size, null, null);
    }

    public static ProductQuery of(String kw, String filter, Integer page, Integer size,
                                  String sort, String dir) {
        String q = kw == null ? "" : kw.trim();
        String f = filter == null || filter.isBlank() ? "all" : filter.trim();
        int p = page == null || page < 1 ? 1 : page;
        int s = size == null ? 20 : size;
        String so = sort == null || sort.isBlank() ? "updated" : sort.trim();
        String d = "asc".equalsIgnoreCase(dir == null ? "" : dir.trim()) ? "asc" : "desc";
        return new ProductQuery(q, f, p, s, so, d);
    }

    /** 同一列再点一次即换向；换列时回到该列的自然方向。 */
    public String toggleDir(String column) {
        if (column == null || !column.equals(sort)) {
            return "price".equals(column) || "stock".equals(column) ? "asc" : "desc";
        }
        return "asc".equals(dir) ? "desc" : "asc";
    }
}
