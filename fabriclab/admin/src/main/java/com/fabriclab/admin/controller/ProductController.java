package com.fabriclab.admin.controller;

import com.fabriclab.admin.exception.BusinessException;
import com.fabriclab.admin.exception.NotFoundException;
import com.fabriclab.admin.model.ProductForm;
import com.fabriclab.admin.model.ProductPage;
import com.fabriclab.admin.model.ProductQuery;
import com.fabriclab.admin.service.AdminProductService;
import com.fabriclab.admin.service.CatalogService;
import com.fabriclab.admin.service.ProductAlbumService;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.format.DateTimeFormatter;
import java.util.List;

/** 款号维护（UC-A4）：列表筛选分页、新增编辑、上下架、批量与受限删除。 */
@Controller
@RequestMapping("/admin/products")
public class ProductController {

    private final AdminProductService productService;
    private final CatalogService catalogService;
    private final ProductAlbumService albumService;
    private final MessageSource messages;

    public ProductController(AdminProductService productService, CatalogService catalogService,
                             ProductAlbumService albumService, MessageSource messages) {
        this.productService = productService;
        this.catalogService = catalogService;
        this.albumService = albumService;
        this.messages = messages;
    }

    private String t(String key) {
        return messages.getMessage(key, null, key, LocaleContextHolder.getLocale());
    }

    @GetMapping({"", "/"})
    public String list(@RequestParam(required = false) String kw,
                       @RequestParam(required = false) String filter,
                       @RequestParam(required = false) Integer page,
                       @RequestParam(required = false) Integer size,
                       Model model) {
        ProductQuery q = ProductQuery.of(kw, filter, page, size);
        ProductPage result = productService.page(q);
        model.addAttribute("result", result);
        model.addAttribute("kw", q.kw());
        model.addAttribute("filter", q.filter());
        model.addAttribute("currentPage", result.page());
        model.addAttribute("pageSize", result.size());
        return "products";
    }

    @GetMapping("/new")
    public String newForm(Model model) {
        model.addAttribute("form", productService.blankForm());
        model.addAttribute("editing", false);
        model.addAttribute("categories", catalogService.secondLevels());
        return "product-form";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable long id, Model model) {
        var p = productService.require(id);
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm");
        model.addAttribute("createdAt", p.getCreatedAt() == null ? null : p.getCreatedAt().format(fmt));
        model.addAttribute("updatedAt", p.getUpdatedAt() == null ? null : p.getUpdatedAt().format(fmt));
        model.addAttribute("form", productService.form(id));
        model.addAttribute("editing", true);
        model.addAttribute("albumCount", albumService.count(id));
        model.addAttribute("categories", catalogService.secondLevels());
        return "product-form";
    }

    private ProductForm bind(Long id, String sku, Long categoryId, String name, String summary,
                             String price, Integer stock, String status, String coverImage,
                             Integer sortOrder, String fabric, String weightNote, String fitNote,
                             String craftNote, String descriptionExtra, Long version) {
        return new ProductForm(id, sku, categoryId, name, summary, price, stock, status,
                coverImage, sortOrder, fabric, weightNote, fitNote, craftNote,
                descriptionExtra, version);
    }

    @PostMapping({"", "/"})
    public String create(
            @RequestParam(required = false) String sku,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String summary,
            @RequestParam(required = false) String price,
            @RequestParam(required = false) Integer stock,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String coverImage,
            @RequestParam(required = false) Integer sortOrder,
            @RequestParam(required = false) String fabric,
            @RequestParam(required = false) String weightNote,
            @RequestParam(required = false) String fitNote,
            @RequestParam(required = false) String craftNote,
            @RequestParam(required = false) String descriptionExtra,
            @RequestParam(required = false) Long version,
            Model model, RedirectAttributes ra) {
        ProductForm form = bind(null, sku, categoryId, name, summary, price, stock, status,
                coverImage, sortOrder, fabric, weightNote, fitNote, craftNote,
                descriptionExtra, version);
        try {
            productService.create(form);
            ra.addFlashAttribute("notice", t("admin.ok.prod.created"));
            return "redirect:/admin/products";
        } catch (BusinessException | NotFoundException e) {
            return rerender(model, form, false, e.getMessage());
        }
    }

    @PostMapping("/{id}")
    public String update(
            @PathVariable long id,
            @RequestParam(required = false) String sku,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String summary,
            @RequestParam(required = false) String price,
            @RequestParam(required = false) Integer stock,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String coverImage,
            @RequestParam(required = false) Integer sortOrder,
            @RequestParam(required = false) String fabric,
            @RequestParam(required = false) String weightNote,
            @RequestParam(required = false) String fitNote,
            @RequestParam(required = false) String craftNote,
            @RequestParam(required = false) String descriptionExtra,
            @RequestParam(required = false) Long version,
            Model model, RedirectAttributes ra) {
        ProductForm form = bind(id, sku, categoryId, name, summary, price, stock, status,
                coverImage, sortOrder, fabric, weightNote, fitNote, craftNote,
                descriptionExtra, version);
        try {
            productService.update(id, form);
            ra.addFlashAttribute("notice", t("admin.ok.prod.updated"));
            return "redirect:/admin/products";
        } catch (BusinessException | NotFoundException e) {
            return rerender(model, form, true, e.getMessage());
        }
    }

    private String rerender(Model model, ProductForm form, boolean editing, String error) {
        model.addAttribute("form", form);
        model.addAttribute("editing", editing);
        model.addAttribute("error", error);
        model.addAttribute("albumCount", editing && form.id() != null ? albumService.count(form.id()) : 0);
        model.addAttribute("categories", catalogService.secondLevels());
        return "product-form";
    }

    /** 增量补货：编辑页与列表行菜单共用，正整数增量，成功后回到来源页。 */
    @PostMapping("/{id}/restock")
    public String restock(@PathVariable long id,
                          @RequestParam(required = false) String delta,
                          @RequestHeader(value = "Referer", required = false) String referer,
                          RedirectAttributes ra) {
        try {
            int stock = productService.restock(id, delta(delta));
            ra.addFlashAttribute("notice", t("admin.ok.stock.restocked") + " · " + stock);
        } catch (BusinessException | NotFoundException e) {
            ra.addFlashAttribute("notice", e.getMessage());
            ra.addFlashAttribute("noticeKind", "err");
        }
        return "redirect:" + back(referer);
    }

    private Integer delta(String raw) {
        if (raw == null || raw.isBlank()) throw new BusinessException(t("admin.err.stock.deltaInvalid"));
        try {
            return Integer.valueOf(raw.trim());
        } catch (NumberFormatException e) {
            throw new BusinessException(t("admin.err.stock.deltaInvalid"));
        }
    }

    /** 只接受站内商品路径，避免把 Referer 当成开放跳转。 */
    private String back(String referer) {
        if (referer != null) {
            int start = referer.indexOf("/admin/products");
            if (start >= 0) {
                String path = referer.substring(start);
                int q = path.indexOf('?');
                if (q >= 0) path = path.substring(0, q);
                if (path.equals("/admin/products") || path.startsWith("/admin/products/")) {
                    return path;
                }
            }
        }
        return "/admin/products";
    }

    @PostMapping("/{id}/status")
    public String setStatus(@PathVariable long id, @RequestParam String target,
                            RedirectAttributes ra) {
        try {
            productService.setStatus(id, target);
            ra.addFlashAttribute("notice", AdminProductService.ON_SALE.equalsIgnoreCase(target)
                    ? t("admin.ok.prod.published") : t("admin.ok.prod.unpublished"));
        } catch (BusinessException | NotFoundException e) {
            ra.addFlashAttribute("notice", e.getMessage());
            ra.addFlashAttribute("noticeKind", "err");
        }
        return "redirect:/admin/products";
    }

    @PostMapping("/batch")
    public String batch(@RequestParam(required = false) List<Long> ids,
                        @RequestParam String target, RedirectAttributes ra) {
        if (ids == null || ids.isEmpty()) {
            ra.addFlashAttribute("notice", t("admin.err.prod.notFound"));
            ra.addFlashAttribute("noticeKind", "err");
            return "redirect:/admin/products";
        }
        int n = 0;
        for (Long id : ids) {
            try { productService.setStatus(id, target); n++; }
            catch (BusinessException | NotFoundException ignored) { }
        }
        ra.addFlashAttribute("notice", (AdminProductService.ON_SALE.equalsIgnoreCase(target)
                ? t("admin.ok.prod.published") : t("admin.ok.prod.unpublished")) + " · " + n);
        return "redirect:/admin/products";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable long id, RedirectAttributes ra) {
        try {
            productService.delete(id);
            ra.addFlashAttribute("notice", t("admin.ok.prod.deleted"));
        } catch (BusinessException | NotFoundException e) {
            ra.addFlashAttribute("notice", e.getMessage());
            ra.addFlashAttribute("noticeKind", "err");
        }
        return "redirect:/admin/products";
    }
}
