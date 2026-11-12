package com.fabriclab.store.service;

import org.springframework.stereotype.Component;

/**
 * 取消订单流程的故障注入位（AC-23 验收用）：每恢复一条明细的库存与商品版本之后被调用。
 * 生产实现不做任何事；真库验收以替身在此抛出异常，验证订单状态仍为待支付、全部库存与
 * 商品版本回到取消前。调用点在取消事务内，注入的异常同样触发整体回滚。
 */
@Component
public class CancelFaultPoint {

    public void afterStockRestored(long orderId) {
        // 生产路径不做任何事
    }
}
