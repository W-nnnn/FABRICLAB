package com.fabriclab.admin.controller;

import com.fabriclab.admin.exception.BusinessException;
import com.fabriclab.admin.exception.NotFoundException;
import com.fabriclab.admin.model.OrderPage;
import com.fabriclab.admin.model.OrderQuery;
import com.fabriclab.admin.model.OrderView;
import com.fabriclab.admin.model.ReceiverForm;
import com.fabriclab.admin.service.AdminOrderService;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * 订单管理（UC-A8、UC-A9、UC-A10）：全站查单、修改收货信息、发货与受限删除。
 * 列表 GET 整页刷新；三个写动作各自独立提交并走 PRG，提示只出现一次。
 */
@Controller
@RequestMapping("/admin/orders")
public class OrderController {

    private final AdminOrderService orderService;
    private final MessageSource messages;

    public OrderController(AdminOrderService orderService, MessageSource messages) {
        this.orderService = orderService;
        this.messages = messages;
    }

    private String t(String key) {
        return messages.getMessage(key, null, key, LocaleContextHolder.getLocale());
    }

    @GetMapping({"", "/"})
    public String list(@RequestParam(required = false) String kw,
                       @RequestParam(required = false) String status,
                       @RequestParam(required = false) Integer page,
                       @RequestParam(required = false) Integer size,
                       Model model) {
        OrderQuery q = OrderQuery.of(kw, status, page, size);
        OrderPage result = orderService.page(q);
        model.addAttribute("result", result);
        model.addAttribute("kw", q.kw());
        model.addAttribute("status", q.status());
        model.addAttribute("currentPage", result.page());
        model.addAttribute("pageSize", result.size());
        return "orders";
    }

    @GetMapping("/{orderNo}")
    public String detail(@PathVariable String orderNo, Model model) {
        OrderView view = orderService.view(orderNo);
        model.addAttribute("o", view);
        return "order-detail";
    }

    @PostMapping("/{orderNo}/receiver")
    public String updateReceiver(@PathVariable String orderNo,
                                 @RequestParam(required = false) String receiverName,
                                 @RequestParam(required = false) String receiverPhone,
                                 @RequestParam(required = false) String receiverAddress,
                                 RedirectAttributes ra) {
        try {
            orderService.updateReceiver(new ReceiverForm(orderNo, receiverName, receiverPhone,
                    receiverAddress));
            ra.addFlashAttribute("notice", t("admin.ok.order.receiverUpdated"));
        } catch (BusinessException | NotFoundException e) {
            ra.addFlashAttribute("notice", e.getMessage());
            ra.addFlashAttribute("noticeKind", "err");
        }
        return "redirect:/admin/orders/" + orderNo;
    }

    @PostMapping("/{orderNo}/ship")
    public String ship(@PathVariable String orderNo, RedirectAttributes ra) {
        try {
            orderService.ship(orderNo);
            ra.addFlashAttribute("notice", t("admin.ok.order.shipped"));
        } catch (BusinessException | NotFoundException e) {
            ra.addFlashAttribute("notice", e.getMessage());
            ra.addFlashAttribute("noticeKind", "err");
        }
        return "redirect:/admin/orders/" + orderNo;
    }

    @PostMapping("/{orderNo}/delete")
    public String delete(@PathVariable String orderNo, RedirectAttributes ra) {
        try {
            orderService.delete(orderNo);
            ra.addFlashAttribute("notice", t("admin.ok.order.deleted"));
            return "redirect:/admin/orders";
        } catch (BusinessException | NotFoundException e) {
            ra.addFlashAttribute("notice", e.getMessage());
            ra.addFlashAttribute("noticeKind", "err");
            return "redirect:/admin/orders/" + orderNo;
        }
    }
}
