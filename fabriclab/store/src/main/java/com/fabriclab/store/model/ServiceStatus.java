package com.fabriclab.store.model;

/** 咨询状态：待回复、已回复、已关闭；迁移只由服务端重查状态后执行。 */
public enum ServiceStatus {

    OPEN("svc.status.open"),
    REPLIED("svc.status.replied"),
    CLOSED("svc.status.closed");

    private final String labelKey;

    ServiceStatus(String labelKey) {
        this.labelKey = labelKey;
    }

    public String labelKey() {
        return labelKey;
    }

    public static ServiceStatus of(String raw) {
        for (ServiceStatus status : values()) {
            if (status.name().equals(raw)) {
                return status;
            }
        }
        throw new IllegalStateException("未知的咨询状态：" + raw);
    }
}
