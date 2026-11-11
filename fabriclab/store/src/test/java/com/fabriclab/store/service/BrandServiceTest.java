package com.fabriclab.store.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BrandServiceTest {

    private final BrandService service = new BrandService();

    @Test
    void brandIsClothingLabel() {
        assertEquals("FabricLab-Atelier", service.brand());
    }

    @Test
    void stageMarksDataLayer() {
        assertEquals("阶段 2 · 数据层与服装模型", service.stage());
    }
}
