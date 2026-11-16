package com.fabriclab.admin.controller;

import com.fabriclab.admin.exception.BusinessException;
import com.fabriclab.admin.exception.NotFoundException;
import com.fabriclab.admin.model.AdminUser;
import com.fabriclab.admin.model.PageSize;
import com.fabriclab.admin.model.ServicePage;
import com.fabriclab.admin.model.ServiceQuery;
import com.fabriclab.admin.service.AdminSessionService;
import com.fabriclab.admin.service.AdminSupportService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * 客服咨询管理（后台）：列表、详情、回复、关闭与重开。
 * /admin/** 由 AdminAuthInterceptor 统一检查 ADMIN 身份；全部写操作 POST + CSRF + PRG，
 * 失败提示保留已输入的回复草稿并就近标红，不丢内容。
 */
@Controller
@RequestMapping("/admin/service")
public class SupportController {

    private static final String LIST = "/admin/service";

    private final AdminSupportService supportService;
    private final AdminSessionService sessionService;
    private final MessageSource messages;

    public SupportController(AdminSupportService supportService, AdminSessionService sessionService,
                             MessageSource messages) {
        this.supportService = supportService;
        this.sessionService = sessionService;
        this.messages = messages;
    }

    private String t(String key) {
        return messages.getMessage(key, null, key, LocaleContextHolder.getLocale());
    }

    @GetMapping({"", "/"})
    public String list(@RequestParam(required = false) String kw,
                       @RequestParam(required = false) String status,
                       @RequestParam(required = false) Integer page,
                       HttpServletRequest request, HttpServletResponse response, Model model) {
        PageSize.remember(response, request);
        ServiceQuery q = ServiceQuery.of(kw, status, page, PageSize.resolve(request));
        ServicePage result = supportService.page(q);
        model.addAttribute("result", result);
        model.addAttribute("kw", q.kw());
        model.addAttribute("status", q.status());
        model.addAttribute("currentPage", result.page());
        model.addAttribute("pageSize", result.size());
        // 进入详情后返回列表时保留筛选、页码与页大小
        String query = request.getQueryString();
        model.addAttribute("listUrl", request.getRequestURI()
                + (query == null || query.isBlank() ? "" : "?" + query));
        return "support";
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable String id,
                         @RequestParam(required = false) String returnTo, Model model) {
        long threadId = parseId(id);
        model.addAttribute("view", supportService.view(threadId));
        model.addAttribute("returnTo", safeReturn(returnTo));
        return "support-detail";
    }

    @PostMapping("/{id}/reply")
    public String reply(@PathVariable String id,
                        @RequestParam(required = false) String body,
                        @RequestParam(required = false) String returnTo,
                        HttpSession session, RedirectAttributes ra) {
        long threadId = parseId(id);
        String back = "redirect:/admin/service/" + threadId + suffix(returnTo);
        AdminUser admin = sessionService.from(session);
        if (admin == null) {
            return "redirect:/admin/signon";
        }
        try {
            supportService.reply(threadId, admin, body);
            ra.addFlashAttribute("notice", t("admin.svc.ok.replied"));
            ra.addFlashAttribute("noticeKind", "ok");
        } catch (BusinessException | NotFoundException e) {
            ra.addFlashAttribute("notice", e.getMessage());
            ra.addFlashAttribute("noticeKind", "err");
            ra.addFlashAttribute("replyError", e.getMessage());
            ra.addFlashAttribute("replyDraft", body);
        }
        return back;
    }

    @PostMapping("/{id}/close")
    public String close(@PathVariable String id,
                        @RequestParam(required = false) String returnTo, RedirectAttributes ra) {
        long threadId = parseId(id);
        boolean changed = supportService.close(threadId);
        ra.addFlashAttribute("notice", changed ? t("admin.svc.ok.closed") : t("admin.svc.warn.alreadyClosed"));
        ra.addFlashAttribute("noticeKind", changed ? "ok" : "err");
        return "redirect:/admin/service/" + threadId + suffix(returnTo);
    }

    @PostMapping("/{id}/reopen")
    public String reopen(@PathVariable String id,
                         @RequestParam(required = false) String returnTo, RedirectAttributes ra) {
        long threadId = parseId(id);
        boolean changed = supportService.reopen(threadId);
        ra.addFlashAttribute("notice", changed ? t("admin.svc.ok.reopened") : t("admin.svc.warn.notClosed"));
        ra.addFlashAttribute("noticeKind", changed ? "ok" : "err");
        return "redirect:/admin/service/" + threadId + suffix(returnTo);
    }

    /** 返回列表时保留筛选条件；只接受本站咨询列表地址，防止开放重定向。 */
    private String safeReturn(String raw) {
        if (raw == null || raw.isBlank() || !raw.startsWith(LIST)
                || raw.contains("//") || raw.contains("\n") || raw.contains("\r")) {
            return LIST;
        }
        return raw;
    }

    private String suffix(String raw) {
        String back = safeReturn(raw);
        return LIST.equals(back) ? "" : "?returnTo=" + java.net.URLEncoder.encode(
                back, java.nio.charset.StandardCharsets.UTF_8);
    }

    private long parseId(String raw) {
        try {
            return Long.parseLong(raw.trim());
        } catch (RuntimeException e) {
            throw new NotFoundException("内容不存在", "咨询不存在或已被移除");
        }
    }
}
