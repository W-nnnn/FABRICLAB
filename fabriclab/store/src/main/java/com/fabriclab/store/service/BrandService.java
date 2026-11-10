package com.fabriclab.store.service;

import org.springframework.stereotype.Service;

@Service
public class BrandService {

    public String brand() {
        return "FabricLab-Atelier";
    }

    public String tagline() {
        return "面料实验室 · 成衣工坊";
    }

    public String stage() {
        return "阶段 1 · 项目初始化";
    }
}
