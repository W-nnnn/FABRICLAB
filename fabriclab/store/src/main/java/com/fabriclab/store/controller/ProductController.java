package com.fabriclab.store.controller;

import com.fabriclab.store.model.CareGuide;
import com.fabriclab.store.model.FitGuide;
import com.fabriclab.store.service.BrandService;
import com.fabriclab.store.service.DetailService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 商品详情页（FR-G-03）：GET /product?id= 整页刷新。
 * 数量输入 1—99 由 Quantity 模型约束；加入购物车在购物车模块（后续任务）接入后端校验。
 */
@Controller
public class ProductController {

    /** 详情页搭配推荐条数。 */
    private static final int RELATED_LIMIT = 4;

    private final BrandService brandService;
    private final DetailService detailService;

    public ProductController(BrandService brandService, DetailService detailService) {
        this.brandService = brandService;
        this.detailService = detailService;
    }

    @GetMapping("/product")
    public String detail(@RequestParam(required = false) String id, Model model) {
        model.addAttribute("brand", brandService.brand());
        model.addAttribute("tagline", brandService.tagline());
        DetailService.ProductDetailView view = detailService.detail(id);
        model.addAttribute("view", view);
        model.addAttribute("fit", FitGuide.of(view.product()));
        model.addAttribute("fitShape", FitGuide.fitOf(view.product()));
        model.addAttribute("careKey", CareGuide.keyOf(view.product()));
        model.addAttribute("related", detailService.related(view.product(), RELATED_LIMIT));
        return "product";
    }
}
