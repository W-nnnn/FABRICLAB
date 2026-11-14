package com.fabriclab.admin.model;

/** 账号列表查询条件：状态筛选只在服务层白名单内，页码与页大小由服务层约束。 */
public record UserQuery(String kw, String status, int page, int size) {

    public static UserQuery of(String kw, String status, Integer page, Integer size) {
        String q = kw == null ? "" : kw.trim();
        String s = status == null || status.isBlank() ? "all" : status.trim();
        int p = page == null || page < 1 ? 1 : page;
        int z = size == null ? 20 : size;
        return new UserQuery(q, s, p, z);
    }
}
