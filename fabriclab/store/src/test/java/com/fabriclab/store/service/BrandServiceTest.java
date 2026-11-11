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
    void stageMarksInitialization() {
        assertEquals("阶段 1 · 项目初始化", service.stage());
    }
}
