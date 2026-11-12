package com.fabriclab.admin.service;

import org.springframework.stereotype.Service;

/** 后台展示用的品牌信息，与用户端同一套视觉标识。 */
@Service
public class BrandService {

    public String brand() {
        return "FABRICLAB";
    }

    public String tagline() {
        return "未染";
    }

    public String consoleName() {
        return "管理台";
    }
}
