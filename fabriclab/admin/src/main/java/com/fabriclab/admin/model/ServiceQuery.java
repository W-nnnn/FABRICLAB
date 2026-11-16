package com.fabriclab.admin.model;

/** 咨询列表查询条件：状态走白名单，关键字去空白，页码与页大小由服务层约束。 */
public record ServiceQuery(String kw, String status, int page, int size) {

    public static final int MAX_KW = 80;

    public static ServiceQuery of(String kw, String status, Integer page, Integer size) {
        String q = kw == null ? "" : kw.trim();
        if (q.length() > MAX_KW) {
            q = q.substring(0, MAX_KW);
        }
        String s = status == null || status.isBlank() ? "all" : status.trim().toLowerCase();
        int p = page == null || page < 1 ? 1 : page;
        int z = size == null ? 20 : size;
        return new ServiceQuery(q, s, p, z);
    }
}
