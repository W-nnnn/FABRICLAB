package com.fabriclab.admin.model;

/** 咨询列表行：编号、会员、主题、状态与最后一条消息摘要，全部来自当次查询。 */
public record ServiceRow(
        long id, String threadNo, String username, String displayName,
        String categoryKey, String statusKey, String status, String subject,
        String lastSnippet, int messageCount, String activityText, String createdText,
        String relatedOrderNo, String relatedSku) {
}
