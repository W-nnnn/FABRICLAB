package com.fabriclab.admin.controller;

import com.fabriclab.admin.exception.BusinessException;
import com.fabriclab.admin.exception.NotFoundException;
import com.fabriclab.admin.model.AlbumImage;
import com.fabriclab.admin.service.AdminProductService;
import com.fabriclab.admin.service.CatalogService;
import com.fabriclab.admin.service.ProductAlbumService;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.Map;

/** 服装相册维护（UC-A6）：三组图片的增改删与设为封面，来源许可逐张记录。 */
@Controller
@RequestMapping("/admin/products/{id}/album")
public class AlbumController {

    private final ProductAlbumService albumService;
    private final CatalogService catalogService;
    private final MessageSource messages;

    public AlbumController(ProductAlbumService albumService, CatalogService catalogService,
                           MessageSource messages) {
        this.albumService = albumService;
        this.catalogService = catalogService;
        this.messages = messages;
    }

    private String t(String key) {
        return messages.getMessage(key, null, key, LocaleContextHolder.getLocale());
    }

    @GetMapping({"", "/"})
    public String page(@PathVariable long id, Model model) {
        var product = albumService.require(id);
        Map<String, List<AlbumImage>> groups = albumService.grouped(id);
        model.addAttribute("product", product);
        model.addAttribute("groups", groups);
        model.addAttribute("total", albumService.count(id));
        model.addAttribute("kinds", ProductAlbumService.KINDS);
        model.addAttribute("categoryName", categoryName(product.getCategoryId()));
        model.addAttribute("statusLabel", t(AdminProductService.ON_SALE.equals(product.getStatus())
                ? "admin.prod.statusLive" : "admin.prod.statusDraft"));
        model.addAttribute("shape", AdminProductService.shape(product.getName()));
        model.addAttribute("tone", AdminProductService.tone(product.getName()));
        return "album";
    }

    private String categoryName(Long categoryId) {
        if (categoryId == null) return "—";
        return catalogService.secondLevels().stream()
                .filter(c -> c.getId().equals(categoryId))
                .map(c -> c.getName())
                .findFirst().orElse("—");
    }

    @PostMapping({"", "/"})
    public String add(@PathVariable long id,
                      @RequestParam(required = false) String kind,
                      @RequestParam(required = false) String path,
                      @RequestParam(required = false) String credit,
                      @RequestParam(required = false) String alt,
                      @RequestParam(required = false) Integer sortOrder,
                      RedirectAttributes ra) {
        try {
            albumService.add(id, kind, path, credit, alt, sortOrder);
            ra.addFlashAttribute("notice", t("admin.ok.album.added"));
        } catch (BusinessException | NotFoundException e) {
            ra.addFlashAttribute("notice", e.getMessage());
            ra.addFlashAttribute("noticeKind", "err");
        }
        return "redirect:/admin/products/" + id + "/album";
    }

    @PostMapping("/{imageId}")
    public String update(@PathVariable long id, @PathVariable long imageId,
                         @RequestParam(required = false) String kind,
                         @RequestParam(required = false) String path,
                         @RequestParam(required = false) String credit,
                         @RequestParam(required = false) String alt,
                         @RequestParam(required = false) Integer sortOrder,
                         RedirectAttributes ra) {
        try {
            albumService.update(id, imageId, kind, path, credit, alt, sortOrder);
            ra.addFlashAttribute("notice", t("admin.ok.album.updated"));
        } catch (BusinessException | NotFoundException e) {
            ra.addFlashAttribute("notice", e.getMessage());
            ra.addFlashAttribute("noticeKind", "err");
        }
        return "redirect:/admin/products/" + id + "/album";
    }

    @PostMapping("/{imageId}/delete")
    public String delete(@PathVariable long id, @PathVariable long imageId, RedirectAttributes ra) {
        try {
            albumService.delete(id, imageId);
            ra.addFlashAttribute("notice", t("admin.ok.album.deleted"));
        } catch (BusinessException | NotFoundException e) {
            ra.addFlashAttribute("notice", e.getMessage());
            ra.addFlashAttribute("noticeKind", "err");
        }
        return "redirect:/admin/products/" + id + "/album";
    }

    @PostMapping("/{imageId}/cover")
    public String cover(@PathVariable long id, @PathVariable long imageId, RedirectAttributes ra) {
        try {
            albumService.setCover(id, imageId);
            ra.addFlashAttribute("notice", t("admin.ok.album.cover"));
        } catch (BusinessException | NotFoundException e) {
            ra.addFlashAttribute("notice", e.getMessage());
            ra.addFlashAttribute("noticeKind", "err");
        }
        return "redirect:/admin/products/" + id + "/album";
    }
}
