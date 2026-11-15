package com.fabriclab.store.model;

import com.fabriclab.store.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** 「我的咨询」查询参数：状态白名单、页码归一与页大小边界。 */
class ServiceQueryTest {

    @Test
    void defaultsAreAllFirstPageTenRows() {
        ServiceQuery q = ServiceQuery.parse(null, null, null);
        assertNull(q.status());
        assertEquals(1, q.page());
        assertEquals(10, q.size());
        assertNull(q.statusParam());
    }

    @Test
    void statusAndParamsAreParsed() {
        ServiceQuery q = ServiceQuery.parse("REPLIED", "3", "20");
        assertEquals(ServiceStatus.REPLIED, q.status());
        assertEquals("REPLIED", q.statusParam());
        assertEquals(3, q.page());
        assertEquals(20, q.size());
    }

    @Test
    void illegalStatusIsRejectedInsteadOfSilentlyAll() {
        assertThrows(BusinessException.class, () -> ServiceQuery.parse("REFUND", null, null));
    }

    @Test
    void pageLessThanOneIsNormalisedAndSizeIsBounded() {
        assertEquals(1, ServiceQuery.parse(null, "0", null).page());
        assertEquals(1, ServiceQuery.parse(null, "-7", null).page());
        assertThrows(BusinessException.class, () -> ServiceQuery.parse(null, "1", "0"));
        assertThrows(BusinessException.class, () -> ServiceQuery.parse(null, "1", "51"));
        assertThrows(BusinessException.class, () -> ServiceQuery.parse(null, "abc", "10"));
        assertThrows(BusinessException.class, () -> ServiceQuery.parse(null, "1", "ten"));
    }
}
