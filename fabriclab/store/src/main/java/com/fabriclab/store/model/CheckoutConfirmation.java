package com.fabriclab.store.model;

import java.math.BigDecimal;
import java.util.List;

/**
 * 服务端临时确认（FR-O-01）：打开结算确认页时按本人数据库车生成，
 * 保存当前用户、条目 ID、商品 ID、数量、条目版本与单价，仅存会话。
 * 确认页不锁价、不预占库存；提交时逐项核对，任一项变化即要求重新确认，
 * 不静默按新金额成交。会话失效后确认随之失效，不能沿用旧确认下单。
 */
public record CheckoutConfirmation(long userId, List<Line> lines, BigDecimal totalAmount) {

    /** 确认行：条目口径快照。 */
    public record Line(long itemId, long productId, int quantity, long version, BigDecimal unitPrice) {
    }
}
