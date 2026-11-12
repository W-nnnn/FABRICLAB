package com.fabriclab.store.model;

import com.fabriclab.store.exception.BusinessException;

/**
 * 本人订单查询参数（FR-O-03，第 14 章统一口径）。
 * 状态缺省或 ALL 表示全部，仅接受五种合法状态，其他值返回参数错误；
 * 订单号去首尾空白、最长 40 字符、按完整订单号精确匹配，空白等价于不限制；
 * 页码缺省 1、小于 1 的整数归一为 1，格式错误或溢出返回参数错误；
 * 页大小其他列表缺省 10，仅允许 1—50 整数。
 */
public record OrderQuery(OrderStatus status, String orderNo, int page, int size) {

    public static final int DEFAULT_PAGE = 1;
    public static final int DEFAULT_SIZE = 10;
    public static final int MIN_SIZE = 1;
    public static final int MAX_SIZE = 50;
    public static final int MAX_ORDER_NO_LENGTH = 40;
    public static final String ALL = "ALL";

    public static OrderQuery parse(String status, String orderNo, String page, String size) {
        return new OrderQuery(parseStatus(status), parseOrderNo(orderNo),
                parsePage(page), parseSize(size));
    }

    /** 状态筛选：全部（null）；非法值不能静默当作全部。 */
    private static OrderStatus parseStatus(String raw) {
        if (raw == null || raw.isBlank() || ALL.equals(raw.trim())) {
            return null;
        }
        for (OrderStatus status : OrderStatus.values()) {
            if (status.name().equals(raw.trim())) {
                return status;
            }
        }
        throw new BusinessException("订单状态只接受 ALL、PENDING_PAYMENT、PAID、SHIPPED、COMPLETED、CANCELLED");
    }

    private static String parseOrderNo(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.length() > MAX_ORDER_NO_LENGTH) {
            throw new BusinessException("订单号最长 " + MAX_ORDER_NO_LENGTH + " 个字符");
        }
        return trimmed;
    }

    private static int parsePage(String raw) {
        if (raw == null || raw.isBlank()) {
            return DEFAULT_PAGE;
        }
        long value = parseNumber(raw, "页码");
        if (value < DEFAULT_PAGE) {
            return DEFAULT_PAGE;
        }
        if (value > Integer.MAX_VALUE) {
            throw new BusinessException("页码超出范围");
        }
        return (int) value;
    }

    private static int parseSize(String raw) {
        if (raw == null || raw.isBlank()) {
            return DEFAULT_SIZE;
        }
        long value = parseNumber(raw, "页大小");
        if (value < MIN_SIZE || value > MAX_SIZE) {
            throw new BusinessException("页大小只允许 " + MIN_SIZE + "—" + MAX_SIZE + " 的整数");
        }
        return (int) value;
    }

    private static long parseNumber(String raw, String field) {
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException e) {
            throw new BusinessException(field + "必须是整数");
        }
    }

    /** 翻页与筛选链接用：全部状态不携带状态参数。 */
    public String statusParam() {
        return status == null ? null : status.name();
    }
}
