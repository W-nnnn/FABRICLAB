package com.fabriclab.admin.controller;

import com.fabriclab.admin.exception.BusinessException;
import com.fabriclab.admin.exception.NotFoundException;
import com.fabriclab.admin.model.UserForm;
import com.fabriclab.admin.model.UserPage;
import com.fabriclab.admin.model.UserQuery;
import com.fabriclab.admin.model.UserView;
import com.fabriclab.admin.service.AdminUserService;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * 顾客账号（UC-A7）：列表筛选分页、资料修改、口令重置、启停。
 * 三个动作各自独立提交，不混在保存里；全部走 PRG，提示只出现一次。
 */
@Controller
@RequestMapping("/admin/users")
public class UserController {

    private final AdminUserService userService;
    private final MessageSource messages;

    public UserController(AdminUserService userService, MessageSource messages) {
        this.userService = userService;
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
        UserQuery q = UserQuery.of(kw, status, page, size);
        UserPage result = userService.page(q);
        model.addAttribute("result", result);
        model.addAttribute("kw", q.kw());
        model.addAttribute("status", q.status());
        model.addAttribute("currentPage", result.page());
        model.addAttribute("pageSize", result.size());
        model.addAttribute("initialPassword", AdminUserService.INITIAL_PASSWORD);
        return "users";
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable long id, Model model) {
        UserView view = userService.view(id);
        model.addAttribute("u", view);
        model.addAttribute("form", userService.form(id));
        model.addAttribute("initialPassword", AdminUserService.INITIAL_PASSWORD);
        return "user-detail";
    }

    @PostMapping("/{id}")
    public String save(@PathVariable long id,
                       @RequestParam(required = false) String username,
                       @RequestParam(required = false) String role,
                       @RequestParam(required = false) String displayName,
                       @RequestParam(required = false) String email,
                       @RequestParam(required = false) String phone,
                       Model model, RedirectAttributes ra) {
        try {
            userService.update(id, new UserForm(id, username, role, displayName, email, phone));
            ra.addFlashAttribute("notice", t("admin.ok.user.updated"));
            return "redirect:/admin/users/" + id;
        } catch (BusinessException | NotFoundException e) {
            UserView view = userService.view(id);
            model.addAttribute("u", view);
            model.addAttribute("form", new UserForm(id, view.username(), view.role(),
                    displayName, email, phone));
            model.addAttribute("initialPassword", AdminUserService.INITIAL_PASSWORD);
            model.addAttribute("error", e.getMessage());
            return "user-detail";
        }
    }

    @PostMapping("/{id}/password")
    public String resetPassword(@PathVariable long id, RedirectAttributes ra) {
        try {
            userService.resetPassword(id);
            ra.addFlashAttribute("notice", t("admin.ok.user.reset"));
            ra.addFlashAttribute("noticeKind", "ok");
        } catch (BusinessException | NotFoundException e) {
            ra.addFlashAttribute("notice", e.getMessage());
            ra.addFlashAttribute("noticeKind", "err");
        }
        return "redirect:/admin/users/" + id;
    }

    @PostMapping("/{id}/status")
    public String setStatus(@PathVariable long id, @RequestParam(required = false) String target,
                            RedirectAttributes ra) {
        try {
            boolean changed = userService.setStatus(id, target);
            if (changed) {
                ra.addFlashAttribute("notice", AdminUserService.DISABLED.equalsIgnoreCase(
                        target == null ? "" : target.trim())
                        ? t("admin.ok.user.disabled") : t("admin.ok.user.enabled"));
            } else {
                ra.addFlashAttribute("notice",
                        t("admin.err.user.statusSame") + " · " + userService.currentStatusLabel(id));
                ra.addFlashAttribute("noticeKind", "err");
            }
        } catch (BusinessException | NotFoundException e) {
            ra.addFlashAttribute("notice", e.getMessage());
            ra.addFlashAttribute("noticeKind", "err");
        }
        return "redirect:/admin/users/" + id;
    }
}
