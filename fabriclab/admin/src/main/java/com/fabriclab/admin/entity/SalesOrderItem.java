package com.fabriclab.admin.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;

/** 订单明细快照（与用户端同库同表）：T16 详情页只读呈现，删除订单时随单清理。 */
@TableName("sales_order_item")
public class SalesOrderItem {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long orderId;
    private Long productId;
    private String skuSnapshot;
    private String productNameSnapshot;
    private String coverImageSnapshot;
    private BigDecimal unitPrice;
    private Integer quantity;
    private BigDecimal lineAmount;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }
    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }
    public String getSkuSnapshot() { return skuSnapshot; }
    public void setSkuSnapshot(String skuSnapshot) { this.skuSnapshot = skuSnapshot; }
    public String getProductNameSnapshot() { return productNameSnapshot; }
    public void setProductNameSnapshot(String productNameSnapshot) { this.productNameSnapshot = productNameSnapshot; }
    public String getCoverImageSnapshot() { return coverImageSnapshot; }
    public void setCoverImageSnapshot(String coverImageSnapshot) { this.coverImageSnapshot = coverImageSnapshot; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public void setUnitPrice(BigDecimal unitPrice) { this.unitPrice = unitPrice; }
    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }
    public BigDecimal getLineAmount() { return lineAmount; }
    public void setLineAmount(BigDecimal lineAmount) { this.lineAmount = lineAmount; }
}
