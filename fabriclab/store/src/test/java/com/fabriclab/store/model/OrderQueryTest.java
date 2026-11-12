package com.fabriclab.store.model;

import com.fabriclab.store.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 订单查询参数与收货三项校验（FR-O-01、FR-O-03，第 14 章统一口径）：
 * 状态仅接受五种合法值与 ALL，订单号精确匹配且最长 40 字符，
 * 页码归一、页大小 1—50，收货三项去空白后必填并校验长度。
 */
class OrderQueryTest {

    @Test
    void statusDefaultsToAll() {
        assertNull(OrderQuery.parse(null, null, null, null).status());
        assertNull(OrderQuery.parse("", "", "", "").status());
        assertNull(OrderQuery.parse("ALL", null, null, null).status());
        assertEquals(OrderStatus.PENDING_PAYMENT, OrderQuery.parse("PENDING_PAYMENT", null, null, null).status());
        assertEquals(OrderStatus.CANCELLED, OrderQuery.parse("CANCELLED", null, null, null).status());
    }

    @Test
    void invalidStatusIsRejectedNotTreatedAsAll() {
        assertThrows(BusinessException.class, () -> OrderQuery.parse("PAIDING", null, null, null));
        assertThrows(BusinessException.class, () -> OrderQuery.parse("pending_payment", null, null, null));
        assertThrows(BusinessException.class, () -> OrderQuery.parse("ALL_PAID", null, null, null));
    }

    @Test
    void orderNoIsTrimmedAndBlankMeansNoFilter() {
        assertNull(OrderQuery.parse(null, "   ", null, null).orderNo());
        assertNull(OrderQuery.parse(null, null, null, null).orderNo());
        assertEquals("FL20260101000000001", OrderQuery.parse(null, "  FL20260101000000001  ", null, null).orderNo());
    }

    @Test
    void orderNoOverFortyCharactersIsRejected() {
        String tooLong = "F".repeat(41);
        assertThrows(BusinessException.class, () -> OrderQuery.parse(null, tooLong, null, null));
        assertEquals(40, OrderQuery.parse(null, "F".repeat(40), null, null).orderNo().length());
    }

    @Test
    void pageNormalizesBelowOneAndRejectsNonNumeric() {
        assertEquals(1, OrderQuery.parse(null, null, null, null).page());
        assertEquals(1, OrderQuery.parse(null, null, "0", null).page());
        assertEquals(1, OrderQuery.parse(null, null, "-5", null).page());
        assertEquals(7, OrderQuery.parse(null, null, "7", null).page());
        assertThrows(BusinessException.class, () -> OrderQuery.parse(null, null, "abc", null));
        assertThrows(BusinessException.class, () -> OrderQuery.parse(null, null, "99999999999", null));
    }

    @Test
    void sizeDefaultsToTenAndStaysWithinOneToFifty() {
        assertEquals(10, OrderQuery.parse(null, null, null, null).size());
        assertEquals(1, OrderQuery.parse(null, null, null, "1").size());
        assertEquals(50, OrderQuery.parse(null, null, null, "50").size());
        assertThrows(BusinessException.class, () -> OrderQuery.parse(null, null, null, "0"));
        assertThrows(BusinessException.class, () -> OrderQuery.parse(null, null, null, "51"));
        assertThrows(BusinessException.class, () -> OrderQuery.parse(null, null, null, "2.5"));
    }

    @Test
    void receiverTrimsAndRequiresAllThree() {
        ReceiverInfo info = ReceiverInfo.parse("  林未  ", " 13800001234 ", "  上海市静安区某路 1 号 ");
        assertEquals("林未", info.name());
        assertEquals("13800001234", info.phone());
        assertEquals("上海市静安区某路 1 号", info.address());
        assertThrows(BusinessException.class, () -> ReceiverInfo.parse("   ", "13800001234", "地址"));
        assertThrows(BusinessException.class, () -> ReceiverInfo.parse("林未", null, "地址"));
        assertThrows(BusinessException.class, () -> ReceiverInfo.parse("林未", "138", "   "));
    }

    @Test
    void receiverLengthLimitsFollowSpec() {
        assertEquals(50, ReceiverInfo.parse("名".repeat(50), "1", "址").name().length());
        assertThrows(BusinessException.class, () -> ReceiverInfo.parse("名".repeat(51), "1", "址"));
        assertThrows(BusinessException.class, () -> ReceiverInfo.parse("名", "1".repeat(31), "址"));
        assertThrows(BusinessException.class, () -> ReceiverInfo.parse("名", "1", "址".repeat(256)));
        // 联系电话不固定 11 位：含区号与分机也按文本接受
        assertEquals("+86-021-0000 转 12", ReceiverInfo.parse("名", " +86-021-0000 转 12 ", "址").phone());
    }

    @Test
    void statusParamOmitsAllFilter() {
        assertNull(OrderQuery.parse("ALL", null, null, null).statusParam());
        assertEquals("PAID", OrderQuery.parse("PAID", null, null, null).statusParam());
    }
}
