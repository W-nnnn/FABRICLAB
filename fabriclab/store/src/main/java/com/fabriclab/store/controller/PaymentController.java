package com.fabriclab.store.controller;

import com.fabriclab.store.exception.BusinessException;
import com.fabriclab.store.model.CurrentUser;
import com.fabriclab.store.service.BrandService;
import com.fabriclab.store.service.CurrentUserService;
import com.fabriclab.store.service.PaymentService;
import com.fabriclab.store.service.PaymentService.PayOutcome;
import com.fabriclab.store.service.PaymentService.PaymentView;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * 教学模拟支付（FR-P-01）：支付页只展示订单号与订单总额，金额不来自表单；
 * 成功与失败是两个明确动作，操作后一律回订单详情呈现数据库真实状态，
 * 不用凭空生成的「成功」弹窗。非待支付订单不提供支付页；
 * 非本人订单统一 404。游客经访问检查先登录。
 */
@Controller
public class PaymentController {

    private final BrandService brandService;
    private final PaymentService paymentService;
    private final CurrentUserService currentUserService;

    public PaymentController(BrandService brandService, PaymentService paymentService,
                             CurrentUserService currentUserService) {
        this.brandService = brandService;
        this.paymentService = paymentService;
        this.currentUserService = currentUserService;
    }

    /** 支付页：仅待支付订单可进入，其余状态回详情说明原因。 */
    @GetMapping("/orders/{orderId}/payment")
    public String page(@PathVariable String orderId, HttpSession session, Model model,
                       RedirectAttributes redirect) {
        CurrentUser current = currentUserService.from(session);
        if (current == null) {
            return "redirect:/signon";
        }
        PaymentView view = paymentService.prepare(current.id(), orderId);
        if (!view.status().payable()) {
            redirect.addFlashAttribute("orderError", "只有待支付订单可以发起支付");
            return "redirect:/orders/" + orderId.trim();
        }
        model.addAttribute("brand", brandService.brand());
        model.addAttribute("tagline", brandService.tagline());
        model.addAttribute("view", view);
        return "payment";
    }

    /** 模拟支付成功：成功与重复成功分别提示，二者都回详情展示库内真实状态。 */
    @PostMapping("/orders/{orderId}/payment")
    public String pay(@PathVariable String orderId, HttpSession session, RedirectAttributes redirect) {
        CurrentUser current = currentUserService.from(session);
        if (current == null) {
            return "redirect:/signon";
        }
        try {
            PayOutcome outcome = paymentService.pay(current.id(), orderId);
            redirect.addFlashAttribute("orderFlash", outcome.firstTime()
                    ? "支付成功，支付凭证 " + outcome.paymentNo()
                    : "该订单已支付，保留首次支付凭证与时间");
        } catch (BusinessException e) {
            redirect.addFlashAttribute("orderError", e.getMessage());
        }
        return "redirect:/orders/" + orderId.trim();
    }

    /** 模拟支付失败：订单仍待支付，可重试或取消。 */
    @PostMapping("/orders/{orderId}/payment/fail")
    public String fail(@PathVariable String orderId, HttpSession session, RedirectAttributes redirect) {
        CurrentUser current = currentUserService.from(session);
        if (current == null) {
            return "redirect:/signon";
        }
        try {
            paymentService.recordFailure(current.id(), orderId);
            redirect.addFlashAttribute("orderError", "支付未完成，订单仍为待支付，可重新发起或取消订单");
        } catch (BusinessException e) {
            redirect.addFlashAttribute("orderError", e.getMessage());
        }
        return "redirect:/orders/" + orderId.trim();
    }
}
