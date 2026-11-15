package com.fabriclab.admin.model;

import com.fabriclab.admin.entity.ServiceThread;

import java.util.List;

/** 咨询详情：会话、会员联系资料与按时间升序的完整对话。 */
public record ServiceView(
        ServiceThread thread, String statusKey, String categoryKey,
        String username, String displayName, String email, String phone,
        List<Message> messages, String createdText, String activityText, String repliedText) {

    /** 一条消息：发送方由服务端判定，正文由模板转义输出。 */
    public record Message(String body, boolean fromCustomer, String authorLabel, String time) {}
}
