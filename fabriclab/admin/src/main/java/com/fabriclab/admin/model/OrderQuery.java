package com.fabriclab.admin.model;

/** 订单列表查询条件：状态只在白名单内，订单号要求完整精确匹配，页大小缺省 10。 */
public record OrderQuery(String kw, String status, int page, int size) {

    public static OrderQuery of(String kw, String status, Integer page, Integer size) {
        String q = kw == null ? "" : kw.trim();
        String s = status == null || status.isBlank() ? "all" : status.trim();
        int p = page == null || page < 1 ? 1 : page;
        int z = size == null ? 10 : size;
        return new OrderQuery(q, s, p, z);
    }
}
