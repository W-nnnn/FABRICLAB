package com.fabriclab.store.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fabriclab.store.entity.SalesOrder;
import com.fabriclab.store.exception.BusinessException;
import com.fabriclab.store.model.CurrentUser;
import com.fabriclab.store.model.OrderQuery;
import com.fabriclab.store.model.OrderStatus;
import com.fabriclab.store.service.BrandService;
import com.fabriclab.store.service.CurrentUserService;
import com.fabriclab.store.service.OrderService;
import com.fabriclab.store.service.OrderService.OrderDetail;
import com.fabriclab.store.service.OrderService.OrderThumb;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.Map;

/**
 * 我的订单（FR-O-03、FR-O-04）：列表与详情只呈现本人数据，越权统一 404；
 * 五状态筛选、完整订单号精确匹配与分页按第 14 章统一参数规则处理，
 * 非法参数返回 400 参数错误页。取消仅对待支付订单有效，重复取消给出已处理提示。
 */
@Controller
public class OrderController {

    /** 列表行：状态数据库值在服务端转界面名称，模板不做类引用。 */
    public record OrderRow(SalesOrder order, String statusLabel, List<OrderThumb> thumbs) {

        static OrderRow of(SalesOrder order, Map<Long, List<OrderThumb>> thumbs) {
            List<OrderThumb> own = thumbs == null ? List.of()
                    : thumbs.getOrDefault(order.getId(), List.of());
            return new OrderRow(order, OrderStatus.of(order.getStatus()).label(), own);
        }
    }

    private final BrandService brandService;
    private final OrderService orderService;
    private final CurrentUserService currentUserService;

    public OrderController(BrandService brandService, OrderService orderService,
                           CurrentUserService currentUserService) {
        this.brandService = brandService;
        this.orderService = orderService;
        this.currentUserService = currentUserService;
    }

    /** 本人订单列表：筛选、订单号与页码共同生效；超过末页显示空结果并保留条件。 */
    @GetMapping("/orders")
    public String list(@RequestParam(required = false) String status,
                       @RequestParam(required = false) String orderNo,
                       @RequestParam(required = false) String page,
                       @RequestParam(required = false) String size,
                       HttpSession session, Model model) {
        CurrentUser current = currentUserService.from(session);
        if (current == null) {
            return "redirect:/signon";
        }
        OrderQuery query = OrderQuery.parse(status, orderNo, page, size);
        Page<SalesOrder> result = orderService.list(current.id(), query);
        fillCommon(model);
        model.addAttribute("query", query);
        model.addAttribute("result", result);
        Map<Long, List<OrderThumb>> thumbs = orderService.thumbnails(result.getRecords());
        model.addAttribute("rows", result.getRecords().stream()
                .map(order -> OrderRow.of(order, thumbs)).toList());
        model.addAttribute("statuses", List.of(OrderStatus.values()));
        return "orders";
    }

    /** 本人订单详情：非本人订单 404，不泄露存在性。 */
    @GetMapping("/orders/{orderId}")
    public String detail(@PathVariable String orderId, HttpSession session, Model model) {
        CurrentUser current = currentUserService.from(session);
        if (current == null) {
            return "redirect:/signon";
        }
        OrderDetail detail = orderService.detail(current.id(), orderId);
        fillCommon(model);
        model.addAttribute("detail", detail);
        return "order";
    }

    /** 取消待支付订单：成功回详情；已取消或状态不允许时回详情提示原因。 */
    @PostMapping("/orders/{orderId}/cancel")
    public String cancel(@PathVariable String orderId, HttpSession session, RedirectAttributes redirect) {
        CurrentUser current = currentUserService.from(session);
        if (current == null) {
            return "redirect:/signon";
        }
        try {
            orderService.cancel(current.id(), orderId);
        } catch (BusinessException e) {
            redirect.addFlashAttribute("orderError", e.getMessage());
        }
        return "redirect:/orders/" + orderId.trim();
    }

    private void fillCommon(Model model) {
        model.addAttribute("brand", brandService.brand());
        model.addAttribute("tagline", brandService.tagline());
    }
}
