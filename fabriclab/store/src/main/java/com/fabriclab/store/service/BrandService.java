package com.fabriclab.store.service;

import org.springframework.stereotype.Service;

@Service
public class BrandService {

    public String brand() {
        return "FABRICLAB";
    }

    public String tagline() {
        return "未染";
    }

    public String stage() {
        return "阶段 2 · 商品模块";
    }
}
