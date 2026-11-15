package com.fabriclab.admin.controller;

import com.fabriclab.admin.entity.Product;
import com.fabriclab.admin.exception.BusinessException;
import com.fabriclab.admin.exception.NotFoundException;
import com.fabriclab.admin.model.AlbumImage;
import com.fabriclab.admin.service.AdminProductService;
import com.fabriclab.admin.service.CatalogService;
import com.fabriclab.admin.service.ProductAlbumService;
import com.fabriclab.admin.service.ProductImageStorage;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.Map;

/**
 * 服装相册维护（UC-A6）：三组图片的上传、增改删与设为封面，来源许可逐张记录。
 * 两条路径共用同一详情页：/admin/albums/{id} 归属「服装相册」，/admin/products/{id}/album
 * 是款号维护里保留的跳转页，归属「款号维护」。表单提交与返回按钮始终留在来时的模块。
 */
@Controller
@RequestMapping({"/admin/products/{id}/album", "/admin/albums/{id}"})
public class AlbumController {

    private static final String ALBUMS_PATH = "/admin/albums/";

    private final ProductAlbumService albumService;
    private final CatalogService catalogService;
    private final ProductImageStorage imageStorage;
    private final MessageSource messages;

    public AlbumController(ProductAlbumService albumService, CatalogService catalogService,
                           ProductImageStorage imageStorage, MessageSource messages) {
        this.albumService = albumService;
        this.catalogService = catalogService;
        this.imageStorage = imageStorage;
        this.messages = messages;
    }

    private String t(String key) {
        return messages.getMessage(key, null, key, LocaleContextHolder.getLocale());
    }

    private boolean fromAlbums(HttpServletRequest request) {
        return request.getRequestURI().contains(ALBUMS_PATH);
    }

    private String albumBase(long id, HttpServletRequest request) {
        return fromAlbums(request) ? ALBUMS_PATH + id : "/admin/products/" + id + "/album";
    }

    @GetMapping({"", "/"})
    public String page(@PathVariable long id, HttpServletRequest request, Model model) {
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

        boolean albums = fromAlbums(request);
        model.addAttribute("albumScope", albums ? "albums" : "products");
        model.addAttribute("albumBase", albumBase(id, request));
        model.addAttribute("backHref", albums ? "/admin/albums" : "/admin/products/" + id + "/edit");
        model.addAttribute("backLabel", t(albums ? "admin.album.backAlbums" : "admin.album.back"));
        model.addAttribute("crumbSection", t(albums ? "admin.nav.albums" : "admin.nav.products"));
        model.addAttribute("crumbCurrent", albums ? product.getSku() : t("admin.album.title"));
        model.addAttribute("crumbHref", albums ? "/admin/albums" : "/admin/products");
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
    public String add(@PathVariable long id, HttpServletRequest request,
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
        return "redirect:" + albumBase(id, request);
    }

    /** 上传一张：文件名与目录由服务端按款号生成，用户不再手打路径。 */
    @PostMapping("/upload")
    public String upload(@PathVariable long id, HttpServletRequest request,
                         @RequestParam(required = false) String kind,
                         @RequestParam(required = false) String credit,
                         @RequestParam(required = false) String alt,
                         @RequestParam(required = false) Integer sortOrder,
                         @RequestParam(required = false) MultipartFile file,
                         RedirectAttributes ra) {
        try {
            Product product = albumService.require(id);
            String path = imageStorage.store(product.getSku(), file);
            albumService.add(id, kind, path, credit, alt, sortOrder);
            ra.addFlashAttribute("notice", t("admin.ok.album.uploaded"));
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("notice", t(uploadKey(e)));
            ra.addFlashAttribute("noticeKind", "err");
        } catch (BusinessException | NotFoundException e) {
            ra.addFlashAttribute("notice", e.getMessage());
            ra.addFlashAttribute("noticeKind", "err");
        }
        return "redirect:" + albumBase(id, request);
    }

    private String uploadKey(IllegalArgumentException ex) {
        return switch (ex.getMessage() == null ? "" : ex.getMessage()) {
            case "size" -> "admin.err.upload.size";
            case "type" -> "admin.err.upload.type";
            case "sku" -> "admin.err.upload.sku";
            default -> "admin.err.upload.empty";
        };
    }

    @PostMapping("/{imageId}")
    public String update(@PathVariable long id, @PathVariable long imageId,
                         HttpServletRequest request,
                         @RequestParam(required = false) String kind,
                         @RequestParam(required = false) String path,
                         @RequestParam(required = false) String credit,
                         @RequestParam(required = false) String alt,
                         @RequestParam(required = false) Integer sortOrder,
                         @RequestParam(required = false) String moved,
                         RedirectAttributes ra) {
        try {
            albumService.update(id, imageId, kind, path, credit, alt, sortOrder);
            ra.addFlashAttribute("notice", t(moved != null ? "admin.ok.album.moved" : "admin.ok.album.updated"));
        } catch (BusinessException | NotFoundException e) {
            ra.addFlashAttribute("notice", e.getMessage());
            ra.addFlashAttribute("noticeKind", "err");
        }
        return "redirect:" + albumBase(id, request);
    }

    @PostMapping("/{imageId}/delete")
    public String delete(@PathVariable long id, @PathVariable long imageId,
                         HttpServletRequest request, RedirectAttributes ra) {
        try {
            albumService.delete(id, imageId);
            ra.addFlashAttribute("notice", t("admin.ok.album.deleted"));
        } catch (BusinessException | NotFoundException e) {
            ra.addFlashAttribute("notice", e.getMessage());
            ra.addFlashAttribute("noticeKind", "err");
        }
        return "redirect:" + albumBase(id, request);
    }

    @PostMapping("/{imageId}/cover")
    public String cover(@PathVariable long id, @PathVariable long imageId,
                        HttpServletRequest request, RedirectAttributes ra) {
        try {
            albumService.setCover(id, imageId);
            ra.addFlashAttribute("notice", t("admin.ok.album.cover"));
        } catch (BusinessException | NotFoundException e) {
            ra.addFlashAttribute("notice", e.getMessage());
            ra.addFlashAttribute("noticeKind", "err");
        }
        return "redirect:" + albumBase(id, request);
    }
}
