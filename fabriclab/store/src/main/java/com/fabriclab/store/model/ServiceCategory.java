package com.fabriclab.store.model;

/** 咨询类别：尺码版型、订单与支付、面料洗护、其他；均为售前选购咨询。 */
public enum ServiceCategory {

    SIZING("svc.cat.sizing"),
    ORDER("svc.cat.order"),
    FABRIC("svc.cat.fabric"),
    OTHER("svc.cat.other");

    private final String labelKey;

    ServiceCategory(String labelKey) {
        this.labelKey = labelKey;
    }

    public String labelKey() {
        return labelKey;
    }

    public static ServiceCategory of(String raw) {
        for (ServiceCategory category : values()) {
            if (category.name().equals(raw)) {
                return category;
            }
        }
        throw new IllegalStateException("未知的咨询类别：" + raw);
    }
}
