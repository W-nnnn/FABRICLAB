package com.fabriclab.store.controller;

import com.fabriclab.store.service.BrandService;
import com.fabriclab.store.service.BrowseService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.Map;

@Controller
public class MainController {

    private final BrandService brandService;
    private final BrowseService browseService;

    public MainController(BrandService brandService, BrowseService browseService) {
        this.brandService = brandService;
        this.browseService = browseService;
    }

    /** 游客首页：品牌横幅、系列线导航、新品上架与精选推荐两栏，无需登录可浏览。 */
    @GetMapping({"/", "/index", "/main"})
    public String index(Model model) {
        model.addAttribute("brand", brandService.brand());
        model.addAttribute("tagline", brandService.tagline());
        model.addAttribute("home", browseService.homeView());
        return "index";
    }

    @GetMapping("/health")
    @ResponseBody
    public Map<String, String> health() {
        return Map.of("status", "UP", "app", "fabriclab-store", "stage", "T5");
    }
}
