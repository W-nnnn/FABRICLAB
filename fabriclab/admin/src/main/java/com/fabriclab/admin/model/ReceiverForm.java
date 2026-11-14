package com.fabriclab.admin.model;

/** 收货信息修改表单：只有待支付、待发货订单接受修改；订单号本身不可变。 */
public record ReceiverForm(String orderNo, String receiverName,
                           String receiverPhone, String receiverAddress) {
}
