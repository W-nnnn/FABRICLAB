package com.fabriclab.store.model;

import com.fabriclab.store.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** 咨询表单：类别白名单、主题与正文长度、选填关联信息与字段定位。 */
class ServiceFormTest {

    @Test
    void validFormIsTrimmedAndKeepsOptionalReferences() {
        ServiceForm form = ServiceForm.of("SIZING", "  尺码问题  ", "  肩宽 44  ",
                "  DEMO-202611-AL-01  ", "  FL-CM-01  ").checked();
        assertEquals("SIZING", form.category());
        assertEquals("尺码问题", form.subject());
        assertEquals("肩宽 44", form.body());
        assertEquals("DEMO-202611-AL-01", form.orderNo());
        assertEquals("FL-CM-01", form.sku());
    }

    @Test
    void blankOptionalsBecomeNull() {
        ServiceForm form = ServiceForm.of("OTHER", "试穿", "周末有空", "   ", " ").checked();
        assertNull(form.orderNo());
        assertNull(form.sku());
    }

    @Test
    void invalidCategoryAndSubjectAreRejectedWithField() {
        BusinessException category = assertThrows(BusinessException.class, () ->
                ServiceForm.of("REFUND", "主题", "正文", null, null).checked());
        assertEquals("category", category.field());

        BusinessException subject = assertThrows(BusinessException.class, () ->
                ServiceForm.of("SIZING", "   ", "正文", null, null).checked());
        assertEquals("subject", subject.field());

        BusinessException tooLong = assertThrows(BusinessException.class, () ->
                ServiceForm.of("SIZING", "字".repeat(61), "正文", null, null).checked());
        assertEquals("subject", tooLong.field());
    }

    @Test
    void bodyLengthAndOptionalFieldFormatsAreChecked() {
        BusinessException emptyBody = assertThrows(BusinessException.class, () ->
                ServiceForm.of("FABRIC", "洗护", "  ", null, null).checked());
        assertEquals("body", emptyBody.field());

        BusinessException longBody = assertThrows(BusinessException.class, () ->
                ServiceForm.of("FABRIC", "洗护", "字".repeat(1001), null, null).checked());
        assertEquals("body", longBody.field());

        BusinessException badSku = assertThrows(BusinessException.class, () ->
                ServiceForm.of("FABRIC", "洗护", "正文", null, "FL CM/01").checked());
        assertEquals("sku", badSku.field());

        BusinessException longOrder = assertThrows(BusinessException.class, () ->
                ServiceForm.of("ORDER", "订单", "正文", "A".repeat(41), null).checked());
        assertEquals("orderNo", longOrder.field());
    }

    @Test
    void followUpOnlyNeedsBody() {
        assertEquals("追问内容", ServiceForm.of(null, null, " 追问内容 ", null, null).checkedBody());
        assertEquals("body", assertThrows(BusinessException.class, () ->
                ServiceForm.of(null, null, "  ", null, null).checkedBody()).field());
    }
}
