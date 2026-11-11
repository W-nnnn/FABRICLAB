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
    void stageMarksCatalogModule() {
        assertEquals("阶段 2 · 商品模块", service.stage());
    }
}
