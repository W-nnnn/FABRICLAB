package com.fabriclab.store.model;

import com.fabriclab.store.exception.BusinessException;

/**
 * 购买数量：缺省 1，仅接受 1—99 整数。
 * 详情页数量输入与购物车阶段的后端校验共用本模型，不只靠前端加减按钮。
 */
public final class Quantity {

    public static final int MIN = 1;
    public static final int MAX = 99;

    private Quantity() {
    }

    /** 未传或空白视为默认 1；非整数、越界返回参数错误。 */
    public static int parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return MIN;
        }
        int value;
        try {
            value = Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            throw new BusinessException("数量必须是 " + MIN + "—99 的整数");
        }
        if (value < MIN || value > MAX) {
            throw new BusinessException("数量必须在 " + MIN + "—99 之间");
        }
        return value;
    }
}
