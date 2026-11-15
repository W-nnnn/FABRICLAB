package com.fabriclab.admin.model;

/** 咨询状态与类别的文案键；未知值不静默降级，避免页面显示技术串。 */
public final class ServiceKeys {

    private ServiceKeys() {
    }

    public static String statusKey(String status) {
        return switch (status == null ? "" : status) {
            case "OPEN" -> "admin.svc.status.open";
            case "REPLIED" -> "admin.svc.status.replied";
            case "CLOSED" -> "admin.svc.status.closed";
            default -> throw new IllegalStateException("未知的咨询状态：" + status);
        };
    }

    public static String categoryKey(String category) {
        return switch (category == null ? "" : category) {
            case "SIZING" -> "admin.svc.cat.sizing";
            case "ORDER" -> "admin.svc.cat.order";
            case "FABRIC" -> "admin.svc.cat.fabric";
            case "OTHER" -> "admin.svc.cat.other";
            default -> throw new IllegalStateException("未知的咨询类别：" + category);
        };
    }
}
