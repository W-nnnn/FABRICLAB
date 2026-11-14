package com.fabriclab.admin.controller;

import com.fabriclab.admin.exception.BusinessException;
import com.fabriclab.admin.exception.NotFoundException;
import com.fabriclab.admin.model.CategoryForm;
import com.fabriclab.admin.service.CatalogService;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** 分类维护（UC-A3）：GET 整页刷新，写操作 POST + CSRF + PRG，失败重渲并保留输入。 */
@Controller
@RequestMapping("/admin/categories")
public class CategoryController {

    private final CatalogService catalogService;
    private final MessageSource messages;

    public CategoryController(CatalogService catalogService, MessageSource messages) {
        this.catalogService = catalogService;
        this.messages = messages;
    }

    private String t(String key) {
        return messages.getMessage(key, null, key, LocaleContextHolder.getLocale());
    }

    @GetMapping({"", "/"})
    public String list(Model model) {
        model.addAttribute("nodes", catalogService.tree());
        return "categories";
    }

    @GetMapping("/new")
    public String newForm(Model model) {
        model.addAttribute("form", new CategoryForm(null, "", null, "", 0));
        model.addAttribute("editing", false);
        populateParents(model);
        return "category-form";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable long id, Model model) {
        var c = catalogService.require(id);
        model.addAttribute("form", new CategoryForm(c.getId(), c.getCode(), c.getParentId(),
                c.getName(), c.getSortOrder()));
        model.addAttribute("editing", true);
        populateParents(model);
        return "category-form";
    }

    private void populateParents(Model model) {
        model.addAttribute("parents", catalogService.tree());
    }

    private CategoryForm bind(String code, Long parentId, String name, Integer sortOrder,
                              Long id, String existingCode, Long existingParentId) {
        return new CategoryForm(id, code, parentId, name, sortOrder);
    }

    @PostMapping({"", "/"})
    public String create(@RequestParam(required = false) String code,
                         @RequestParam(required = false) Long parentId,
                         @RequestParam(required = false) String name,
                         @RequestParam(required = false) Integer sortOrder,
                         Model model, RedirectAttributes ra) {
        CategoryForm form = bind(code, parentId, name, sortOrder, null, null, null);
        try {
            catalogService.create(form);
            ra.addFlashAttribute("notice", t("admin.ok.cat.created"));
            return "redirect:/admin/categories";
        } catch (BusinessException | NotFoundException e) {
            model.addAttribute("error", e.getMessage());
            model.addAttribute("form", form);
            model.addAttribute("editing", false);
            populateParents(model);
            return "category-form";
        }
    }

    @PostMapping("/{id}")
    public String update(@PathVariable long id,
                         @RequestParam(required = false) String code,
                         @RequestParam(required = false) Long parentId,
                         @RequestParam(required = false) String name,
                         @RequestParam(required = false) Integer sortOrder,
                         Model model, RedirectAttributes ra) {
        CategoryForm form = bind(code, parentId, name, sortOrder, id, null, null);
        try {
            catalogService.update(id, form);
            ra.addFlashAttribute("notice", t("admin.ok.cat.updated"));
            return "redirect:/admin/categories";
        } catch (BusinessException | NotFoundException e) {
            model.addAttribute("error", e.getMessage());
            model.addAttribute("form", form);
            model.addAttribute("editing", true);
            populateParents(model);
            return "category-form";
        }
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable long id, RedirectAttributes ra) {
        try {
            catalogService.delete(id);
            ra.addFlashAttribute("notice", t("admin.ok.cat.deleted"));
        } catch (BusinessException | NotFoundException e) {
            ra.addFlashAttribute("notice", e.getMessage());
            ra.addFlashAttribute("noticeKind", "err");
        }
        return "redirect:/admin/categories";
    }
}
