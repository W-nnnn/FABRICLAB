package com.fabriclab.admin.controller;

import com.fabriclab.admin.service.AdminOrderService;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * 顶栏搜索：按范围分流，而不是把任何关键词都丢进商品列表。
 * 订单号（FL + 数字）直接进订单详情；否则按所选范围进对应列表。
 */
@Controller
public class AdminSearchController {

    private final AdminOrderService orderService;

    public AdminSearchController(AdminOrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping("/admin/search")
    public String search(@RequestParam(required = false) String kw,
                         @RequestParam(required = false) String scope) {
        String q = kw == null ? "" : kw.trim();
        if (q.isEmpty()) {
            return switch (scope == null ? "" : scope) {
                case "order" -> "redirect:/admin/orders";
                case "service" -> "redirect:/admin/service";
                default -> "redirect:/admin/products";
            };
        }
        if ("service".equals(scope)) {
            return "redirect:/admin/service?kw=" + enc(q);
        }
        if ("order".equals(scope)) {
            if (q.matches("FL[0-9]{8,}") && orderService.exists(q)) {
                return "redirect:/admin/orders/" + q;
            }
            return "redirect:/admin/orders?kw=" + enc(q);
        }
        return "redirect:/admin/products?kw=" + enc(q);
    }

    private String enc(String v) {
        return URLEncoder.encode(v, StandardCharsets.UTF_8);
    }
}
