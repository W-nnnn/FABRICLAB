package com.fabriclab.store.service;

import org.springframework.stereotype.Component;

/**
 * 下单流程的故障注入位（AC-16 验收用）：在订单主表、明细快照与首项库存扣减之后被调用。
 * 生产实现不做任何事；真库验收以替身在此抛出异常，验证主表、明细、库存、商品版本与
 * 结算条目共同回滚。放在下单事务内调用，因此注入的异常同样触发整体回滚。
 */
@Component
public class CheckoutFaultPoint {

    public void afterStockDeducted(long orderId) {
        // 生产路径不做任何事
    }
}
