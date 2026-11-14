package com.fabriclab.store.controller;

import com.fabriclab.store.service.SeriesService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * 服装类扩展页（T17）：面料档案、工艺说明、系列专题页。
 * 纯展示路由，只读在售数据；不参与登录、购物车与订单路径。
 */
@Controller
public class SeriesController {

    private final SeriesService seriesService;

    public SeriesController(SeriesService seriesService) {
        this.seriesService = seriesService;
    }

    @GetMapping("/fabrics")
    public String fabrics(Model model) {
        model.addAttribute("groups", seriesService.fabrics());
        return "fabrics";
    }

    @GetMapping("/crafts")
    public String crafts(Model model) {
        model.addAttribute("samples", seriesService.craftSamples(8));
        return "crafts";
    }

    @GetMapping("/series/{id}")
    public String series(@PathVariable String id, Model model) {
        model.addAttribute("series", seriesService.series(id));
        return "series";
    }
}
