package com.fabriclab.admin.controller;

import com.fabriclab.admin.model.ProductQuery;
import com.fabriclab.admin.service.AdminProductService;
import com.fabriclab.admin.service.ProductAlbumService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.HashMap;
import java.util.Map;

/**
 * 服装相册二级入口（UC-A6）：按款号列出真实相册张数，逐款进入 /admin/albums/{id} 维护页。
 * 款号维护里保留 /admin/products/{id}/album 跳转页，两条路径共用同一详情页且各自留在原模块。
 */
@Controller
public class AlbumIndexController {

    private final AdminProductService products;
    private final ProductAlbumService albums;

    public AlbumIndexController(AdminProductService products, ProductAlbumService albums) {
        this.products = products;
        this.albums = albums;
    }

    @GetMapping("/admin/albums")
    public String index(@RequestParam(required = false) String kw,
                        @RequestParam(required = false) Integer page, Model model) {
        ProductQuery query = ProductQuery.of(kw, "all", page, 50);
        var result = products.page(query);
        Map<Long, Integer> counts = new HashMap<>(albums.counts());
        for (var row : result.rows()) counts.putIfAbsent(row.id(), 0);
        model.addAttribute("result", result);
        model.addAttribute("counts", counts);
        model.addAttribute("kw", query.kw());
        model.addAttribute("query", query);
        return "albums";
    }
}
