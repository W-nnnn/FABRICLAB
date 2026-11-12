package com.fabriclab.store.model;

/** 商品列表排序四值：服务端白名单映射，禁止接收任意 SQL 排序字段。 */
public enum SortOption {
    DEFAULT, PRICE_ASC, PRICE_DESC, NEWEST
}
