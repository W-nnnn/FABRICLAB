package com.fabriclab.admin.controller;

import com.fabriclab.admin.exception.BusinessException;
import com.fabriclab.admin.exception.NotFoundException;
import com.fabriclab.admin.model.AlbumImage;
import com.fabriclab.admin.model.PageSize;
import com.fabriclab.admin.model.ProductForm;
import com.fabriclab.admin.model.ProductPage;
import com.fabriclab.admin.model.ProductQuery;
import com.fabriclab.admin.service.AdminProductService;
import com.fabriclab.admin.service.CatalogService;
import com.fabriclab.admin.service.ProductAlbumService;
import com.fabriclab.admin.service.ProductImageStorage;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 款号维护（UC-A4）：列表筛选分页排序、新增编辑、上下架、批量与受限删除。 */
@Controller
@RequestMapping("/admin/products")
public class ProductController {

    private final AdminProductService productService;
    private final CatalogService catalogService;
    private final ProductAlbumService albumService;
    private final ProductImageStorage imageStorage;
    private final MessageSource messages;

    public ProductController(AdminProductService productService, CatalogService catalogService,
                             ProductAlbumService albumService, ProductImageStorage imageStorage,
                             MessageSource messages) {
        this.productService = productService;
        this.catalogService = catalogService;
        this.albumService = albumService;
        this.imageStorage = imageStorage;
        this.messages = messages;
    }

    private String t(String key) {
        return messages.getMessage(key, null, key, LocaleContextHolder.getLocale());
    }

    @GetMapping({"", "/"})
    public String list(@RequestParam(required = false) String kw,
                       @RequestParam(required = false) String filter,
                       @RequestParam(required = false) Integer page,
                       @RequestParam(required = false) String sort,
                       @RequestParam(required = false) String dir,
                       HttpServletRequest request,
                       jakarta.servlet.http.HttpServletResponse response,
                       Model model) {
        PageSize.remember(response, request);
        ProductQuery q = ProductQuery.of(kw, filter, page, PageSize.resolve(request), sort, dir);
        ProductPage result = productService.page(q);
        model.addAttribute("result", result);
        model.addAttribute("kw", q.kw());
        model.addAttribute("filter", q.filter());
        model.addAttribute("currentPage", result.page());
        model.addAttribute("pageSize", result.size());
        model.addAttribute("sort", q.sort());
        model.addAttribute("dir", q.dir());
        model.addAttribute("query", q);
        model.addAttribute("returnTo", here(request));
        return "products";
    }

    /** 当前列表地址：保存后据此回到原来的页码、筛选与排序，而不是跳回第一页。 */
    private String here(HttpServletRequest request) {
        String qs = request.getQueryString();
        return qs == null || qs.isBlank() ? request.getRequestURI() : request.getRequestURI() + "?" + qs;
    }

    @GetMapping("/new")
    public String newForm(@RequestParam(required = false) String returnTo, Model model) {
        model.addAttribute("form", productService.blankForm());
        model.addAttribute("editing", false);
        model.addAttribute("categories", catalogService.secondLevels());
        model.addAttribute("returnTo", safeReturn(returnTo));
        return "product-form";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable long id,
                           @RequestParam(required = false) String returnTo,
                           Model model) {
        var p = productService.require(id);
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm");
        model.addAttribute("createdAt", p.getCreatedAt() == null ? null : p.getCreatedAt().format(fmt));
        model.addAttribute("updatedAt", p.getUpdatedAt() == null ? null : p.getUpdatedAt().format(fmt));
        model.addAttribute("form", productService.form(id));
        model.addAttribute("editing", true);
        model.addAttribute("albumCount", albumService.count(id));
        model.addAttribute("coverOptions", coverOptions(id, p.getSku()));
        model.addAttribute("categories", catalogService.secondLevels());
        model.addAttribute("returnTo", safeReturn(returnTo));
        return "product-form";
    }

    /** 封面候选：优先用相册记录；没有记录时退回该款号目录里已有的图，保证老数据也能选。 */
    private List<String> coverOptions(long productId, String sku) {
        List<String> fromAlbum = albumService.images(productId).stream().map(AlbumImage::path).toList();
        return fromAlbum.isEmpty() ? imageStorage.list(sku) : fromAlbum;
    }

    /** 只接受站内商品列表地址，避免把参数当成开放跳转。 */
    private String safeReturn(String raw) {
        if (raw == null) return null;
        String v = raw.trim();
        if (v.isEmpty() || v.startsWith("//") || v.contains("\r") || v.contains("\n")) return null;
        return v.startsWith("/admin/products") ? v : null;
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
            @RequestParam(required = false) String returnTo,
            Model model, RedirectAttributes ra) {
        return create0(sku, categoryId, name, summary, price, stock, status, coverImage, sortOrder,
                fabric, weightNote, fitNote, craftNote, descriptionExtra, version, returnTo, model, ra);
    }

    /** 新增表单提交到 /new：校验失败时地址栏仍停在这张表单上，刷新不会变成列表。 */
    @PostMapping("/new")
    public String createFromForm(
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
            @RequestParam(required = false) String returnTo,
            Model model, RedirectAttributes ra) {
        return create0(sku, categoryId, name, summary, price, stock, status, coverImage, sortOrder,
                fabric, weightNote, fitNote, craftNote, descriptionExtra, version, returnTo, model, ra);
    }

    private String create0(String sku, Long categoryId, String name, String summary, String price,
                           Integer stock, String status, String coverImage, Integer sortOrder,
                           String fabric, String weightNote, String fitNote, String craftNote,
                           String descriptionExtra, Long version, String returnTo,
                           Model model, RedirectAttributes ra) {
        ProductForm form = bind(null, sku, categoryId, name, summary, price, stock, status,
                coverImage, sortOrder, fabric, weightNote, fitNote, craftNote,
                descriptionExtra, version);
        try {
            productService.create(form);
            ra.addFlashAttribute("notice", t("admin.ok.prod.created"));
            return "redirect:" + orDefault(returnTo, "/admin/products");
        } catch (BusinessException | NotFoundException e) {
            return rerender(model, form, false, e, returnTo);
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
            @RequestParam(required = false) String returnTo,
            Model model, RedirectAttributes ra) {
        return update0(id, sku, categoryId, name, summary, price, stock, status, coverImage, sortOrder,
                fabric, weightNote, fitNote, craftNote, descriptionExtra, version, returnTo, model, ra);
    }

    /** 编辑表单提交到 /{id}/edit：与 GET 同一地址，校验失败时刷新仍是这张表单。 */
    @PostMapping("/{id}/edit")
    public String updateFromForm(
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
            @RequestParam(required = false) String returnTo,
            Model model, RedirectAttributes ra) {
        return update0(id, sku, categoryId, name, summary, price, stock, status, coverImage, sortOrder,
                fabric, weightNote, fitNote, craftNote, descriptionExtra, version, returnTo, model, ra);
    }

    private String update0(long id, String sku, Long categoryId, String name, String summary,
                           String price, Integer stock, String status, String coverImage,
                           Integer sortOrder, String fabric, String weightNote, String fitNote,
                           String craftNote, String descriptionExtra, Long version, String returnTo,
                           Model model, RedirectAttributes ra) {
        ProductForm form = bind(id, sku, categoryId, name, summary, price, stock, status,
                coverImage, sortOrder, fabric, weightNote, fitNote, craftNote,
                descriptionExtra, version);
        try {
            productService.update(id, form);
            ra.addFlashAttribute("notice", t("admin.ok.prod.updated"));
            return "redirect:" + orDefault(returnTo, "/admin/products");
        } catch (BusinessException | NotFoundException e) {
            return rerender(model, form, true, e, returnTo);
        }
    }

    private String orDefault(String returnTo, String fallback) {
        String safe = safeReturn(returnTo);
        return safe == null ? fallback : safe;
    }

    private String rerender(Model model, ProductForm form, boolean editing,
                            RuntimeException e, String returnTo) {
        model.addAttribute("form", form);
        model.addAttribute("editing", editing);
        model.addAttribute("error", e.getMessage());
        model.addAttribute("errorField", e instanceof BusinessException be ? be.field() : null);
        model.addAttribute("albumCount", editing && form.id() != null ? albumService.count(form.id()) : 0);
        model.addAttribute("coverOptions", editing && form.id() != null
                ? coverOptions(form.id(), form.sku()) : List.of());
        model.addAttribute("categories", catalogService.secondLevels());
        model.addAttribute("returnTo", safeReturn(returnTo));
        return "product-form";
    }

    /** 表单里直接上传封面：先落盘拿到相对路径，再由前端回填预览，保存时仍是同一条商品更新。 */
    @PostMapping("/image")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> uploadImage(@RequestParam(required = false) String sku,
                                                           @RequestParam(required = false) MultipartFile file) {
        try {
            String path = imageStorage.store(sku, file);
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("path", path);
            body.put("url", "/" + path);
            return ResponseEntity.ok(body);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", t(uploadKey(ex))));
        }
    }

    private String uploadKey(IllegalArgumentException ex) {
        return switch (ex.getMessage() == null ? "" : ex.getMessage()) {
            case "size" -> "admin.err.upload.size";
            case "type" -> "admin.err.upload.type";
            case "sku" -> "admin.err.upload.sku";
            default -> "admin.err.upload.empty";
        };
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
                            @RequestParam(required = false) String returnTo,
                            RedirectAttributes ra) {
        try {
            productService.setStatus(id, target);
            ra.addFlashAttribute("notice", AdminProductService.ON_SALE.equalsIgnoreCase(target)
                    ? t("admin.ok.prod.published") : t("admin.ok.prod.unpublished"));
        } catch (BusinessException | NotFoundException e) {
            ra.addFlashAttribute("notice", e.getMessage());
            ra.addFlashAttribute("noticeKind", "err");
        }
        return "redirect:" + orDefault(returnTo, "/admin/products");
    }

    @PostMapping("/batch")
    public String batch(@RequestParam(required = false) List<Long> ids,
                        @RequestParam String target,
                        @RequestParam(required = false) String returnTo,
                        RedirectAttributes ra) {
        if (ids == null || ids.isEmpty()) {
            ra.addFlashAttribute("notice", t("admin.err.prod.notFound"));
            ra.addFlashAttribute("noticeKind", "err");
            return "redirect:" + orDefault(returnTo, "/admin/products");
        }
        int n = 0;
        for (Long id : ids) {
            try { productService.setStatus(id, target); n++; }
            catch (BusinessException | NotFoundException ignored) { }
        }
        ra.addFlashAttribute("notice", (AdminProductService.ON_SALE.equalsIgnoreCase(target)
                ? t("admin.ok.prod.published") : t("admin.ok.prod.unpublished")) + " · " + n);
        return "redirect:" + orDefault(returnTo, "/admin/products");
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable long id,
                         @RequestParam(required = false) String returnTo,
                         RedirectAttributes ra) {
        try {
            productService.delete(id);
            ra.addFlashAttribute("notice", t("admin.ok.prod.deleted"));
        } catch (BusinessException | NotFoundException e) {
            ra.addFlashAttribute("notice", e.getMessage());
            ra.addFlashAttribute("noticeKind", "err");
        }
        return "redirect:" + orDefault(returnTo, "/admin/products");
    }
}
