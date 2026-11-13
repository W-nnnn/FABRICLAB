package com.fabriclab.store.controller;

import com.fabriclab.store.model.BrowseQuery;
import com.fabriclab.store.service.BrandService;
import com.fabriclab.store.service.BrowseService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 商品列表页：GET 整页刷新，不使用 AJAX。
 * 分类导航与分页为普通链接，查询表单保留分类、类型、关键字、排序与页大小。
 */
@Controller
public class BrowseController {

    private final BrandService brandService;
    private final BrowseService browseService;

    public BrowseController(BrandService brandService, BrowseService browseService) {
        this.brandService = brandService;
        this.browseService = browseService;
    }

    @GetMapping("/category")
    public String category(@RequestParam(required = false) String category,
                           @RequestParam(required = false) String productType,
                           @RequestParam(required = false) String sort,
                           @RequestParam(required = false) String keyword,
                           @RequestParam(required = false) String page,
                           @RequestParam(required = false) String size,
                           Model model) {
        BrowseQuery query = BrowseQuery.parse(category, productType, sort, keyword, page, size);
        model.addAttribute("brand", brandService.brand());
        model.addAttribute("tagline", brandService.tagline());
        model.addAttribute("query", query);
        model.addAttribute("result", browseService.browse(query));
        model.addAttribute("lines", browseService.categoryTree());
        model.addAttribute("onSaleTotal", browseService.onSaleTotal());
        return "category";
    }
}
