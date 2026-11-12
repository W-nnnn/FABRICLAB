package com.fabriclab.store.controller;

import com.fabriclab.store.exception.BusinessException;
import com.fabriclab.store.model.CheckoutConfirmation;
import com.fabriclab.store.model.CurrentUser;
import com.fabriclab.store.model.ReceiverInfo;
import com.fabriclab.store.service.BrandService;
import com.fabriclab.store.service.CheckoutService;
import com.fabriclab.store.service.CheckoutService.CheckoutView;
import com.fabriclab.store.service.CurrentUserService;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * 结算确认与整车下单（FR-O-01、FR-O-02）：确认只存会话，金额全部由后端计算，
 * 隐藏的金额字段不参与定价。提交失败保留合法收货输入并指明原因；
 * 下单成功后清除确认并跳转本人待支付订单详情。游客经访问检查先登录。
 */
@Controller
public class CheckoutController {

    /** 会话属性键：服务端临时结算确认，随会话失效而失效。 */
    public static final String KEY_CONFIRMATION = "storeCheckoutConfirmation";

    private final BrandService brandService;
    private final CheckoutService checkoutService;
    private final CurrentUserService currentUserService;

    public CheckoutController(BrandService brandService, CheckoutService checkoutService,
                              CurrentUserService currentUserService) {
        this.brandService = brandService;
        this.checkoutService = checkoutService;
        this.currentUserService = currentUserService;
    }

    /** 打开确认：生成服务端确认并展示全车；空车或有失效条目时回购物车提示。 */
    @GetMapping("/checkout")
    public String checkout(Model model, HttpSession session, RedirectAttributes redirect) {
        CurrentUser current = currentUserService.from(session);
        if (current == null) {
            return "redirect:/signon";
        }
        try {
            CheckoutView view = checkoutService.prepare(current.id());
            session.setAttribute(KEY_CONFIRMATION, view.confirmation());
            fill(model, view);
            return "checkout";
        } catch (BusinessException e) {
            redirect.addFlashAttribute("cartError", e.getMessage());
            return "redirect:/cart";
        }
    }

    /** 提交整车订单：同成同败；失败回确认页保留收货输入，确认仍在会话内可再次提交。 */
    @PostMapping("/checkout/submit")
    public String submit(@RequestParam(required = false) String receiverName,
                         @RequestParam(required = false) String receiverPhone,
                         @RequestParam(required = false) String receiverAddress,
                         HttpSession session, Model model, RedirectAttributes redirect) {
        CurrentUser current = currentUserService.from(session);
        if (current == null) {
            return "redirect:/signon";
        }
        Object stored = session.getAttribute(KEY_CONFIRMATION);
        CheckoutConfirmation confirmation = stored instanceof CheckoutConfirmation value ? value : null;
        if (confirmation == null) {
            redirect.addFlashAttribute("cartError", CheckoutService.EXPIRED_MESSAGE);
            return "redirect:/cart";
        }
        ReceiverInfo receiver;
        try {
            receiver = ReceiverInfo.parse(receiverName, receiverPhone, receiverAddress);
        } catch (BusinessException e) {
            return backToConfirm(current.id(), receiverName, receiverPhone,
                    receiverAddress, e.getMessage(), session, model, redirect);
        }
        long orderId;
        try {
            orderId = checkoutService.placeOrder(current.id(), confirmation, receiver);
        } catch (BusinessException e) {
            return backToConfirm(current.id(), receiverName, receiverPhone,
                    receiverAddress, e.getMessage(), session, model, redirect);
        }
        session.removeAttribute(KEY_CONFIRMATION);
        redirect.addFlashAttribute("orderFlash", "订单已提交，请完成模拟支付");
        return "redirect:/orders/" + orderId;
    }

    /** 失败回确认页：保留合法收货输入，并按当前车刷新确认，避免用过期金额再次成交。 */
    private String backToConfirm(long userId, String name,
                                 String phone, String address, String message,
                                 HttpSession session, Model model, RedirectAttributes redirect) {
        try {
            CheckoutView view = checkoutService.prepare(userId);
            session.setAttribute(KEY_CONFIRMATION, view.confirmation());
            fill(model, view);
        } catch (BusinessException e) {
            // 购物车已不可结算时回购物车提示，并保留本次失败原因
            redirect.addFlashAttribute("cartError", message);
            return "redirect:/cart";
        }
        model.addAttribute("checkoutError", message);
        model.addAttribute("receiverName", name);
        model.addAttribute("receiverPhone", phone);
        model.addAttribute("receiverAddress", address);
        return "checkout";
    }

    private void fill(Model model, CheckoutView view) {
        model.addAttribute("brand", brandService.brand());
        model.addAttribute("tagline", brandService.tagline());
        model.addAttribute("view", view.cart());
    }
}
