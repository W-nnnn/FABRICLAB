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

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 客服中心（用户端）：只有 /service 一个页面，FAQ、我的咨询、发起与对话都在这里完成。
 * 旧 /service/threads/** 路由只做兼容跳转，不再有独立页面。登录检查由 AuthInterceptor 统一处理，
 * 数据归属在 SupportService 内复核；写操作仍走 PRG + 一次性提示 + CSRF。
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

    /**
     * 客服中心单页：游客只读 FAQ；会员在同一页切换咨询列表、发起表单与对话。
     * ?ask=1 打开发起表单，?thread=id 指定会话，不传时默认选最近一条。
     */
    @GetMapping({"", "/"})
    public String center(@RequestParam(required = false) String status,
                         @RequestParam(required = false) String page,
                         @RequestParam(required = false) String size,
                         @RequestParam(required = false) String thread,
                         @RequestParam(required = false) String ask,
                         @RequestParam(required = false) String sku,
                         @RequestParam(required = false) String orderNo,
                         @RequestParam(required = false) String category,
                         HttpSession session, Model model) {
        fillCommon(model);
        model.addAttribute("faqs", FAQS);
        CurrentUser current = currentUserService.from(session);
        if (current == null) {
            model.addAttribute("openCount", null);
            return "service";
        }
        ServiceQuery query = ServiceQuery.parse(status, page, size);
        Page<ServiceThread> result = supportService.list(current.id(), query);
        boolean asking = "1".equals(ask) || "true".equalsIgnoreCase(ask);
        String initialCategory = category == null || category.isBlank()
                ? ServiceCategory.SIZING.name() : category;
        fillWorkspace(current, model, query, result, asking, thread,
                ServiceForm.of(initialCategory, null, null, orderNo, sku));
        return "service";
    }

    /** 兼容旧「我的咨询」链接，统一回单页。 */
    @GetMapping("/threads")
    public String threads(@RequestParam(required = false) String status,
                          @RequestParam(required = false) String page,
                          @RequestParam(required = false) String size,
                          HttpSession session) {
        require(session);
        return "redirect:/service" + querySuffix("status", status, "page", page, "size", size);
    }

    /** 兼容商品详情等旧「发起咨询」链接，带入预填参数。 */
    @GetMapping("/threads/new")
    public String newThread(@RequestParam(required = false) String sku,
                            @RequestParam(required = false) String orderNo,
                            @RequestParam(required = false) String category,
                            HttpSession session) {
        require(session);
        return "redirect:/service" + querySuffix("ask", "1", "category", category,
                "orderNo", orderNo, "sku", sku);
    }

    /** 提交咨询：字段级错误回单页表单并保留输入；成功定位到该会话。 */
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
            return "redirect:/service?thread=" + thread.getId();
        } catch (BusinessException e) {
            fillCommon(model);
            fillWorkspace(current, model, ServiceQuery.parse(null, null, null),
                    supportService.list(current.id(), ServiceQuery.parse(null, null, null)),
                    true, null, form);
            model.addAttribute("error", e.getMessage());
            model.addAttribute("errorField", e.field());
            return "service";
        }
    }

    /** 兼容旧详情链接；先复核本人归属，避免跳转暴露他人咨询是否存在。 */
    @GetMapping("/threads/{id}")
    public String thread(@PathVariable String id, HttpSession session) {
        CurrentUser current = require(session);
        long threadId = parseId(id);
        supportService.view(current, threadId);
        return "redirect:/service?thread=" + threadId;
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
        return "redirect:/service?thread=" + threadId;
    }

    @PostMapping("/threads/{id}/close")
    public String close(@PathVariable String id, HttpSession session, RedirectAttributes redirect) {
        CurrentUser current = require(session);
        long threadId = parseId(id);
        boolean changed = supportService.close(current, threadId);
        redirect.addFlashAttribute("notice", changed ? "咨询已关闭" : "这条咨询已经是关闭状态");
        return "redirect:/service?thread=" + threadId;
    }

    @PostMapping("/threads/{id}/reopen")
    public String reopen(@PathVariable String id, HttpSession session, RedirectAttributes redirect) {
        CurrentUser current = require(session);
        long threadId = parseId(id);
        boolean changed = supportService.reopen(current, threadId);
        redirect.addFlashAttribute("notice", changed ? "咨询已重新打开，可以继续追问" : "只有已关闭的咨询可以重新打开");
        return "redirect:/service?thread=" + threadId;
    }

    /** 会员工作区：列表、发起表单与对话共用一个模型。 */
    private void fillWorkspace(CurrentUser current, Model model, ServiceQuery query,
                               Page<ServiceThread> result, boolean asking, String threadRaw,
                               ServiceForm form) {
        model.addAttribute("query", query);
        model.addAttribute("result", result);
        model.addAttribute("rows", supportService.rows(result.getRecords()));
        model.addAttribute("statuses", List.of(ServiceStatus.values()));
        model.addAttribute("openCount", supportService.openCount(current.id()));
        model.addAttribute("asking", asking);
        model.addAttribute("form", form);
        model.addAttribute("categories", List.of(ServiceCategory.values()));

        Long selected = null;
        if (threadRaw != null && !threadRaw.isBlank()) {
            selected = parseId(threadRaw);
        } else if (!result.getRecords().isEmpty()) {
            selected = result.getRecords().get(0).getId();
        }
        model.addAttribute("currentThreadId", selected);
        if (!asking && selected != null) {
            model.addAttribute("view", supportService.view(current, selected));
        }
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

    private String querySuffix(String... pairs) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < pairs.length; i += 2) {
            String value = pairs[i + 1];
            if (value == null || value.isBlank()) continue;
            sb.append(sb.length() == 0 ? "?" : "&").append(pairs[i]).append('=')
                    .append(URLEncoder.encode(value, StandardCharsets.UTF_8));
        }
        return sb.toString();
    }

    private void fillCommon(Model model) {
        model.addAttribute("brand", brandService.brand());
        model.addAttribute("tagline", brandService.tagline());
    }
}
