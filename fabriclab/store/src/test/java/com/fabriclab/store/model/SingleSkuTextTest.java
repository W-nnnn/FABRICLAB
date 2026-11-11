package com.fabriclab.store.model;

import com.fabriclab.store.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 单 SKU 口径：尺码配色并入名称、面料档案纯文本。 */
class SingleSkuTextTest {

    @Test
    void variantNameJoinsSizeAndColor() {
        assertEquals("亚麻通勤衬衫", SingleSkuText.variantName("亚麻通勤衬衫", null, null));
        assertEquals("亚麻通勤衬衫（M）", SingleSkuText.variantName("亚麻通勤衬衫", " M ", null));
        assertEquals("亚麻通勤衬衫（M / 雾蓝）", SingleSkuText.variantName("亚麻通勤衬衫", "M", "雾蓝"));
    }

    @Test
    void variantNameTrimsAndLimitsLength() {
        String base = "长".repeat(120);
        assertEquals(base, SingleSkuText.variantName(" " + base + " ", null, null));
        assertThrows(BusinessException.class, () -> SingleSkuText.variantName("长".repeat(120), "M", null));
    }

    @Test
    void fabricProfileKeepsOnlyProvidedLines() {
        String profile = SingleSkuText.fabricProfile("全棉斜纹", "180g/m²", null, "水洗做旧");
        assertEquals("面料：全棉斜纹\n克重：180g/m²\n工艺：水洗做旧", profile);
        assertTrue(!profile.contains("版型"));
        assertEquals("", SingleSkuText.fabricProfile(null, "", " ", null));
    }

    @Test
    void summaryTrimsOrNull() {
        assertEquals("四季可穿", SingleSkuText.summary(" 四季可穿 "));
        assertNull(SingleSkuText.summary("   "));
        assertThrows(BusinessException.class, () -> SingleSkuText.summary("长".repeat(501)));
    }
}
