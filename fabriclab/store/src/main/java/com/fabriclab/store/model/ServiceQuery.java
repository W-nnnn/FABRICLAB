package com.fabriclab.store.model;

import com.fabriclab.store.exception.BusinessException;

/**
 * 「我的咨询」查询参数（沿用第 14 章统一口径）：状态缺省或 ALL 表示全部，
 * 页码缺省 1、小于 1 归一为 1，页大小缺省 10、只允许 1—50 整数；
 * 格式错误或非法状态按参数错误 400 处理，不静默当作全部。
 */
public record ServiceQuery(ServiceStatus status, int page, int size) {

    public static final int DEFAULT_PAGE = 1;
    public static final int DEFAULT_SIZE = 10;
    public static final int MIN_SIZE = 1;
    public static final int MAX_SIZE = 50;
    public static final String ALL = "ALL";

    public static ServiceQuery parse(String status, String page, String size) {
        return new ServiceQuery(parseStatus(status), parsePage(page), parseSize(size));
    }

    private static ServiceStatus parseStatus(String raw) {
        if (raw == null || raw.isBlank() || ALL.equals(raw.trim())) {
            return null;
        }
        for (ServiceStatus status : ServiceStatus.values()) {
            if (status.name().equals(raw.trim())) {
                return status;
            }
        }
        throw new BusinessException("咨询状态只接受 ALL、OPEN、REPLIED、CLOSED");
    }

    private static int parsePage(String raw) {
        if (raw == null || raw.isBlank()) {
            return DEFAULT_PAGE;
        }
        long value = number(raw, "页码");
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
        long value = number(raw, "页大小");
        if (value < MIN_SIZE || value > MAX_SIZE) {
            throw new BusinessException("页大小只允许 " + MIN_SIZE + "—" + MAX_SIZE + " 的整数");
        }
        return (int) value;
    }

    private static long number(String raw, String field) {
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException e) {
            throw new BusinessException(field + "必须是整数");
        }
    }

    public String statusParam() {
        return status == null ? null : status.name();
    }
}
