package com.fabriclab.store.controller;

import com.fabriclab.store.service.BrandService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.Map;

@Controller
public class MainController {

    private final BrandService brandService;

    public MainController(BrandService brandService) {
        this.brandService = brandService;
    }

    @GetMapping({"/", "/index", "/main"})
    public String index(Model model) {
        model.addAttribute("brand", brandService.brand());
        model.addAttribute("tagline", brandService.tagline());
        model.addAttribute("stage", brandService.stage());
        return "index";
    }

    @GetMapping("/health")
    @ResponseBody
    public Map<String, String> health() {
        return Map.of("status", "UP", "app", "fabriclab-store", "stage", "T3");
    }
}
