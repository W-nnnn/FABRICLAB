package com.fabriclab.admin.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/** 订单明细快照引用：已有明细的商品不得删除（UC-A4）。 */
@TableName("sales_order_item")
public class SalesOrderItem {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long productId;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }
}
