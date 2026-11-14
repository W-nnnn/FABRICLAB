package com.fabriclab.admin.model;

import java.util.ArrayList;
import java.util.List;

/** 分类树节点：一级携带二级 children；款数为当次查询的真实计数。 */
public class CategoryNode {
    private final Long id;
    private final String code;
    private final String name;
    private final int sortOrder;
    private final long productCount;
    private final boolean topLevel;
    private final List<CategoryNode> children = new ArrayList<>();

    public CategoryNode(Long id, String code, String name, int sortOrder,
                        long productCount, boolean topLevel) {
        this.id = id; this.code = code; this.name = name;
        this.sortOrder = sortOrder; this.productCount = productCount;
        this.topLevel = topLevel;
    }
    public Long getId() { return id; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public int getSortOrder() { return sortOrder; }
    public long getProductCount() { return productCount; }
    public boolean isTopLevel() { return topLevel; }
    public List<CategoryNode> getChildren() { return children; }
}
