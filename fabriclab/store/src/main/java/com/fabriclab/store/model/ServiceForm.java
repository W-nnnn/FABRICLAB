package com.fabriclab.store.model;

import com.fabriclab.store.exception.BusinessException;

/**
 * 发起咨询 / 追加追问的表单。服务端统一去首尾空白并校验长度；关联订单号与款号选填，
 * 订单号的本人归属与款号存在性由 SupportService 在写库前复核，不接受表单自报归属。
 */
public record ServiceForm(String category, String subject, String body, String orderNo, String sku) {

    public static final int MAX_SUBJECT = 60;
    public static final int MAX_BODY = 1000;
    public static final int MAX_ORDER_NO = 40;
    public static final int MAX_SKU = 40;
    public static final String ALL_STATUSES = "ALL";

    public static ServiceForm of(String category, String subject, String body, String orderNo, String sku) {
        return new ServiceForm(trim(category), trim(subject), trim(body), trim(orderNo), trim(sku));
    }

    /** 新建咨询：类别必选、主题与正文必填，长度与格式逐项校验。 */
    public ServiceForm checked() {
        String cat = category == null ? "" : category;
        boolean validCategory = false;
        for (ServiceCategory value : ServiceCategory.values()) {
            if (value.name().equals(cat)) {
                validCategory = true;
                break;
            }
        }
        if (!validCategory) {
            throw new BusinessException("category", "请选择咨询类别");
        }
        if (subject == null || subject.isEmpty()) {
            throw new BusinessException("subject", "请填写咨询主题");
        }
        if (subject.length() > MAX_SUBJECT) {
            throw new BusinessException("subject", "咨询主题最长 " + MAX_SUBJECT + " 个字符");
        }
        if (body == null || body.isEmpty()) {
            throw new BusinessException("body", "请填写咨询内容");
        }
        if (body.length() > MAX_BODY) {
            throw new BusinessException("body", "咨询内容最长 " + MAX_BODY + " 个字符");
        }
        if (orderNo != null && orderNo.length() > MAX_ORDER_NO) {
            throw new BusinessException("orderNo", "订单号最长 " + MAX_ORDER_NO + " 个字符");
        }
        if (sku != null && sku.length() > MAX_SKU) {
            throw new BusinessException("sku", "款号最长 " + MAX_SKU + " 个字符");
        }
        if (sku != null && !sku.matches("[A-Za-z0-9_-]+")) {
            throw new BusinessException("sku", "款号只能包含字母、数字、连字符或下划线");
        }
        return this;
    }

    /** 追问只需正文；主题、类别与关联信息沿用会话原值。 */
    public String checkedBody() {
        if (body == null || body.isEmpty()) {
            throw new BusinessException("body", "请填写追问内容");
        }
        if (body.length() > MAX_BODY) {
            throw new BusinessException("body", "追问内容最长 " + MAX_BODY + " 个字符");
        }
        return body;
    }

    private static String trim(String raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.trim();
        return value.isEmpty() ? null : value;
    }
}
