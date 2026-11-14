package com.fabriclab.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fabriclab.admin.entity.Product;
import com.fabriclab.admin.entity.ProductImage;
import com.fabriclab.admin.exception.BusinessException;
import com.fabriclab.admin.exception.NotFoundException;
import com.fabriclab.admin.mapper.ProductImageMapper;
import com.fabriclab.admin.mapper.ProductMapper;
import com.fabriclab.admin.model.AlbumImage;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 服装相册维护（UC-A6、FR-A-07）。DDL 不新增表字段：图类型与来源许可编进
 * {@code product_image.alt_text}（见 {@link AlbumText}），用户端仍按 sort_order、id 升序出图。
 * 相册为空时用户端回退封面，封面缺失再用缺省图；后台在页面上把这个回退写在明处。
 */
@Service
public class ProductAlbumService {

    public static final List<String> KINDS = List.of(AlbumText.FLAT, AlbumText.WORN, AlbumText.DETAIL);

    private final ProductMapper productMapper;
    private final ProductImageMapper imageMapper;
    private final MessageSource messages;

    public ProductAlbumService(ProductMapper productMapper, ProductImageMapper imageMapper,
                               MessageSource messages) {
        this.productMapper = productMapper;
        this.imageMapper = imageMapper;
        this.messages = messages;
    }

    private String t(String key) {
        return messages.getMessage(key, null, key, LocaleContextHolder.getLocale());
    }

    public Product require(long productId) {
        Product p = productMapper.selectById(productId);
        if (p == null) throw new NotFoundException(t("admin.err.prod.notFound"));
        return p;
    }

    public List<AlbumImage> images(long productId) {
        Product p = require(productId);
        List<ProductImage> rows = imageMapper.selectList(new LambdaQueryWrapper<ProductImage>()
                .eq(ProductImage::getProductId, productId)
                .orderByAsc(ProductImage::getSortOrder)
                .orderByAsc(ProductImage::getId));
        if (rows == null) return List.of();
        String cover = p.getCoverImage() == null ? "" : p.getCoverImage();
        List<AlbumImage> out = new ArrayList<>(rows.size());
        boolean coverUsed = false;
        for (ProductImage row : rows) {
            AlbumText.Meta meta = AlbumText.parse(row.getAltText());
            boolean isCover = !coverUsed && !cover.isEmpty() && cover.equals(row.getImagePath());
            if (isCover) coverUsed = true;
            out.add(new AlbumImage(row.getId(), row.getImagePath(), meta.kind(), meta.credit(),
                    meta.alt(), row.getSortOrder() == null ? 0 : row.getSortOrder(), isCover));
        }
        return out;
    }

    /** 三组固定顺序，空组也返回空列表，模板据此出空槽而不是缺段。 */
    public Map<String, List<AlbumImage>> grouped(long productId) {
        Map<String, List<AlbumImage>> map = new LinkedHashMap<>();
        for (String kind : KINDS) map.put(kind, new ArrayList<>());
        for (AlbumImage image : images(productId)) {
            map.computeIfAbsent(image.kind(), k -> new ArrayList<>()).add(image);
        }
        return map;
    }

    public int count(long productId) {
        Long n = imageMapper.selectCount(new LambdaQueryWrapper<ProductImage>()
                .eq(ProductImage::getProductId, productId));
        return n == null ? 0 : n.intValue();
    }

    public ProductImage add(long productId, String kind, String path, String credit, String alt,
                            Integer sortOrder) {
        require(productId);
        String k = kind(kind);
        String p = path(path);
        int sort = sort(sortOrder);
        ProductImage image = new ProductImage();
        image.setProductId(productId);
        image.setImagePath(p);
        image.setAltText(AlbumText.compose(k, clean(credit), clean(alt)));
        image.setSortOrder(sort);
        imageMapper.insert(image);
        return image;
    }

    public void update(long productId, long imageId, String kind, String path, String credit,
                       String alt, Integer sortOrder) {
        requireOwned(productId, imageId);
        ProductImage image = new ProductImage();
        image.setId(imageId);
        image.setImagePath(path(path));
        image.setAltText(AlbumText.compose(kind(kind), clean(credit), clean(alt)));
        image.setSortOrder(sort(sortOrder));
        imageMapper.updateById(image);
    }

    public void delete(long productId, long imageId) {
        requireOwned(productId, imageId);
        imageMapper.deleteById(imageId);
    }

    /** 设为封面：只改 product.cover_image 并推 version，与商品保存同一套条件更新。 */
    public void setCover(long productId, long imageId) {
        Product current = require(productId);
        ProductImage image = requireOwned(productId, imageId);
        if (image.getImagePath().equals(current.getCoverImage())) return;
        LambdaUpdateWrapper<Product> uw = new LambdaUpdateWrapper<Product>()
                .eq(Product::getId, productId)
                .eq(Product::getVersion, current.getVersion() == null ? 0L : current.getVersion())
                .set(Product::getCoverImage, image.getImagePath())
                .setSql("version = version + 1");
        if (productMapper.update(null, uw) == 0) {
            throw new BusinessException(t("admin.err.prod.versionConflict"));
        }
    }

    private ProductImage requireOwned(long productId, long imageId) {
        ProductImage image = imageMapper.selectById(imageId);
        if (image == null || !image.getProductId().equals(productId)) {
            throw new NotFoundException(t("admin.err.album.notFound"));
        }
        return image;
    }

    private String kind(String raw) {
        String k = AlbumText.kind(raw);
        if (k == null) throw new BusinessException(t("admin.err.album.kind"));
        return k;
    }

    private String path(String raw) {
        String v = raw == null ? "" : raw.trim().replace('\\', '/');
        if (v.isEmpty()) throw new BusinessException(t("admin.err.album.pathRequired"));
        if (v.length() > 255) throw new BusinessException(t("admin.err.album.pathLength"));
        String lower = v.toLowerCase();
        if (v.startsWith("/") || v.contains("..") || lower.startsWith("http:") || lower.startsWith("https:")
                || lower.startsWith("//")) {
            throw new BusinessException(t("admin.err.album.pathInvalid"));
        }
        return v;
    }

    private int sort(Integer value) {
        int v = value == null ? 0 : value;
        if (v < 0) throw new BusinessException(t("admin.err.album.sortNegative"));
        return v;
    }

    private String clean(String raw) {
        return raw == null ? "" : raw.trim();
    }

    /** 相册路径对应的站内预览地址：后台没有商品图本体，用同名缩略图目录。 */
    public String preview(String path) {
        return "/" + path;
    }
}
