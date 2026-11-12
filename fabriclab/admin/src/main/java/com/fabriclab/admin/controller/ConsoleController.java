package com.fabriclab.admin.controller;

import com.fabriclab.admin.service.ConsoleService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.Map;

/**
 * 后台控制台（FR-A-01）：仅呈现当前管理员身份、后台模块范围与访问规则。
 * 不展示订单量、销售额、访客数等未经真实统计的指标（NFR-11）。
 */
@Controller
public class ConsoleController {

    private final ConsoleService consoleService;

    public ConsoleController(ConsoleService consoleService) {
        this.consoleService = consoleService;
    }

    @GetMapping("/")
    public String root() {
        return "redirect:/admin/console";
    }

    @GetMapping("/admin/console")
    public String console(Model model) {
        model.addAttribute("modules", consoleService.modules());
        return "console";
    }

    @GetMapping("/health")
    @ResponseBody
    public Map<String, String> health() {
        return Map.of("status", "UP", "app", "fabriclab-admin", "stage", "T12");
    }
}
