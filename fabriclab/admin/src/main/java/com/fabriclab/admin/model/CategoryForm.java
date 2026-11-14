package com.fabriclab.admin.model;

/** 分类表单白名单：只接收编码、上级、名称、排序；编码与上级创建后不可变。 */
public record CategoryForm(Long id, String code, Long parentId, String name, Integer sortOrder) {
}
