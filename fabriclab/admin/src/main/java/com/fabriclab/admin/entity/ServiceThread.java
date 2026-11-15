package com.fabriclab.admin.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/** 选购咨询会话（扩展表）：状态 OPEN / REPLIED / CLOSED，消息另表存放。 */
@TableName("service_thread")
public class ServiceThread {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String threadNo;
    private Long userId;
    private String category;
    private String subject;
    private String status;
    private String relatedOrderNo;
    private String relatedSku;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime lastActivityAt;
    private LocalDateTime firstRepliedAt;
    private LocalDateTime closedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getThreadNo() { return threadNo; }
    public void setThreadNo(String threadNo) { this.threadNo = threadNo; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getRelatedOrderNo() { return relatedOrderNo; }
    public void setRelatedOrderNo(String relatedOrderNo) { this.relatedOrderNo = relatedOrderNo; }
    public String getRelatedSku() { return relatedSku; }
    public void setRelatedSku(String relatedSku) { this.relatedSku = relatedSku; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public LocalDateTime getLastActivityAt() { return lastActivityAt; }
    public void setLastActivityAt(LocalDateTime lastActivityAt) { this.lastActivityAt = lastActivityAt; }
    public LocalDateTime getFirstRepliedAt() { return firstRepliedAt; }
    public void setFirstRepliedAt(LocalDateTime firstRepliedAt) { this.firstRepliedAt = firstRepliedAt; }
    public LocalDateTime getClosedAt() { return closedAt; }
    public void setClosedAt(LocalDateTime closedAt) { this.closedAt = closedAt; }
}
