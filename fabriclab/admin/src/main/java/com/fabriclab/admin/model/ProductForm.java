package com.fabriclab.admin.model;

/**
 * 商品表单白名单。DDL 没有面料 / 版型 / 工艺独立字段，
 * 四个输入由 DescriptionText 无损编解码进 description；
 * stock 仅在新建时接收，编辑页只读；version 用于并发条件更新。
 */
public record ProductForm(
        Long id, String sku, Long categoryId, String name, String summary,
        String price, Integer stock, String status, String coverImage, Integer sortOrder,
        String fabric, String weightNote, String fitNote, String craftNote,
        String descriptionExtra, Long version) {
}
