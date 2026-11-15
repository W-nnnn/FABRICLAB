package com.fabriclab.store.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fabriclab.store.entity.ServiceThread;
import com.fabriclab.store.exception.BusinessException;
import com.fabriclab.store.exception.NotFoundException;
import com.fabriclab.store.model.CurrentUser;
import com.fabriclab.store.model.ServiceCategory;
import com.fabriclab.store.model.ServiceForm;
import com.fabriclab.store.model.ServiceQuery;
import com.fabriclab.store.model.ServiceStatus;
import com.fabriclab.store.service.BrandService;
import com.fabriclab.store.service.CurrentUserService;
import com.fabriclab.store.service.SupportService;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

/**
 * 客服中心（用户端）：/service 为无需登录的常见问题与服务信息；
 * /service/threads/** 需要登录，由 AuthInterceptor 统一检查身份、角色与启用状态，
 * 数据归属在 SupportService 内复核。全部写操作走 PRG + 一次性提示 + CSRF。
 */
@Controller
@RequestMapping("/service")
public class SupportController {

    /** 常见问题条目：文案键与「按此问题发起咨询」的预选类别。 */
    public record Faq(String qKey, String aKey, String category) {}

    private static final List<Faq> FAQS = List.of(
            new Faq("svc.faq.q1", "svc.faq.a1", "SIZING"),
            new Faq("svc.faq.q2", "svc.faq.a2", "FABRIC"),
            new Faq("svc.faq.q3", "svc.faq.a3", "ORDER"),
            new Faq("svc.faq.q4", "svc.faq.a4", "ORDER"),
            new Faq("svc.faq.q5", "svc.faq.a5", "OTHER"));

    private final SupportService supportService;
    private final CurrentUserService currentUserService;
    private final BrandService brandService;

    public SupportController(SupportService supportService, CurrentUserService currentUserService,
                             BrandService brandService) {
        this.supportService = supportService;
        this.currentUserService = currentUserService;
        this.brandService = brandService;
    }

    /** 客服中心首页：常见问题按站点真实能力作答，不承诺退款、退换或物流追踪。 */
    @GetMapping({"", "/"})
    public String center(HttpSession session, Model model) {
        CurrentUser current = currentUserService.from(session);
        fillCommon(model);
        model.addAttribute("faqs", FAQS);
        model.addAttribute("openCount", current == null ? null : supportService.openCount(current.id()));
        return "service";
    }

    /** 我的咨询：状态筛选、分页与真实消息摘要，只返回本人会话。 */
    @GetMapping("/threads")
    public String threads(@RequestParam(required = false) String status,
                          @RequestParam(required = false) String page,
                          @RequestParam(required = false) String size,
                          HttpSession session, Model model) {
        CurrentUser current = require(session);
        ServiceQuery query = ServiceQuery.parse(status, page, size);
        Page<ServiceThread> result = supportService.list(current.id(), query);
        fillCommon(model);
        model.addAttribute("query", query);
        model.addAttribute("result", result);
        model.addAttribute("rows", supportService.rows(result.getRecords()));
        model.addAttribute("statuses", List.of(ServiceStatus.values()));
        model.addAttribute("openCount", supportService.openCount(current.id()));
        return "service-threads";
    }

    /** 发起咨询表单：从商品详情或订单页跳来时预填款号 / 订单号。 */
    @GetMapping("/threads/new")
    public String newThread(@RequestParam(required = false) String sku,
                            @RequestParam(required = false) String orderNo,
                            @RequestParam(required = false) String category,
                            HttpSession session, Model model) {
        require(session);
        fillCommon(model);
        model.addAttribute("form", ServiceForm.of(category, null, null, orderNo, sku));
        model.addAttribute("categories", List.of(ServiceCategory.values()));
        return "service-form";
    }

    /** 提交咨询：字段级错误回表单并保留输入；成功进会话详情。 */
    @PostMapping("/threads")
    public String create(@RequestParam(required = false) String category,
                         @RequestParam(required = false) String subject,
                         @RequestParam(required = false) String body,
                         @RequestParam(required = false) String orderNo,
                         @RequestParam(required = false) String sku,
                         HttpSession session, Model model, RedirectAttributes redirect) {
        CurrentUser current = require(session);
        ServiceForm form = ServiceForm.of(category, subject, body, orderNo, sku);
        try {
            ServiceThread thread = supportService.create(current, form);
            redirect.addFlashAttribute("notice", "咨询已提交，客服回复后会显示在对话里");
            return "redirect:/service/threads/" + thread.getId();
        } catch (BusinessException e) {
            fillCommon(model);
            model.addAttribute("form", form);
            model.addAttribute("categories", List.of(ServiceCategory.values()));
            model.addAttribute("error", e.getMessage());
            model.addAttribute("errorField", e.field());
            return "service-form";
        }
    }

    /** 会话详情：完整对话、追问、关闭与重开。 */
    @GetMapping("/threads/{id}")
    public String thread(@PathVariable String id, HttpSession session, Model model) {
        CurrentUser current = require(session);
        long threadId = parseId(id);
        fillCommon(model);
        model.addAttribute("view", supportService.view(current, threadId));
        return "service-thread";
    }

    @PostMapping("/threads/{id}/reply")
    public String reply(@PathVariable String id,
                        @RequestParam(required = false) String body,
                        HttpSession session, RedirectAttributes redirect) {
        CurrentUser current = require(session);
        long threadId = parseId(id);
        try {
            supportService.append(current, threadId, body);
            redirect.addFlashAttribute("notice", "追问已发送，状态回到待回复");
        } catch (BusinessException e) {
            redirect.addFlashAttribute("notice", e.getMessage());
            redirect.addFlashAttribute("noticeKind", "err");
        }
        return "redirect:/service/threads/" + threadId;
    }

    @PostMapping("/threads/{id}/close")
    public String close(@PathVariable String id, HttpSession session, RedirectAttributes redirect) {
        CurrentUser current = require(session);
        long threadId = parseId(id);
        boolean changed = supportService.close(current, threadId);
        redirect.addFlashAttribute("notice", changed ? "咨询已关闭" : "这条咨询已经是关闭状态");
        return "redirect:/service/threads/" + threadId;
    }

    @PostMapping("/threads/{id}/reopen")
    public String reopen(@PathVariable String id, HttpSession session, RedirectAttributes redirect) {
        CurrentUser current = require(session);
        long threadId = parseId(id);
        boolean changed = supportService.reopen(current, threadId);
        redirect.addFlashAttribute("notice", changed ? "咨询已重新打开，可以继续追问" : "只有已关闭的咨询可以重新打开");
        return "redirect:/service/threads/" + threadId;
    }

    private CurrentUser require(HttpSession session) {
        CurrentUser current = currentUserService.from(session);
        if (current == null) {
            throw new IllegalStateException("未登录访问受保护的客服入口");
        }
        return current;
    }

    private long parseId(String raw) {
        try {
            return Long.parseLong(raw.trim());
        } catch (RuntimeException e) {
            throw new NotFoundException("内容不存在", "咨询不存在或已被移除");
        }
    }

    private void fillCommon(Model model) {
        model.addAttribute("brand", brandService.brand());
        model.addAttribute("tagline", brandService.tagline());
    }
}
