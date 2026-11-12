package com.fabriclab.store.model;

import com.fabriclab.store.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** 购买数量：缺省 1，仅接受 1—99 整数；0、负数、小数、100、非数字均拒绝。 */
class QuantityTest {

    @Test
    void blankOrMissingDefaultsToOne() {
        assertEquals(1, Quantity.parse(null));
        assertEquals(1, Quantity.parse(""));
        assertEquals(1, Quantity.parse("   "));
    }

    @Test
    void acceptsIntegersInsideRangeAndTrims() {
        assertEquals(1, Quantity.parse("1"));
        assertEquals(5, Quantity.parse(" 5 "));
        assertEquals(99, Quantity.parse("99"));
    }

    @Test
    void rejectsZeroNegativeAndOverMax() {
        assertThrows(BusinessException.class, () -> Quantity.parse("0"));
        assertThrows(BusinessException.class, () -> Quantity.parse("-2"));
        assertThrows(BusinessException.class, () -> Quantity.parse("100"));
    }

    @Test
    void rejectsDecimalsAndNonNumeric() {
        assertThrows(BusinessException.class, () -> Quantity.parse("1.5"));
        assertThrows(BusinessException.class, () -> Quantity.parse("abc"));
    }
}
