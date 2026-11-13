package com.fabriclab.store.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fabriclab.store.entity.Category;
import com.fabriclab.store.entity.Product;
import com.fabriclab.store.entity.ProductImage;
import com.fabriclab.store.exception.BusinessException;
import com.fabriclab.store.exception.NotFoundException;
import com.fabriclab.store.mapper.CategoryMapper;
import com.fabriclab.store.mapper.ProductImageMapper;
import com.fabriclab.store.mapper.ProductMapper;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

/**
 * 商品详情（FR-G-03）：仅上架商品可查看；浏览不扣库存。
 * 相册按 sort_order、id 升序；无相册回退封面，封面缺失再用缺省封面。
 * 分类名称取数据库，用于面包屑；下架或不存在统一不可用提示，不渲染可购买详情。
 */
@Service
public class DetailService {

    /** 相册图：路径与替代文案。 */
    public record GalleryImage(String path, String alt) {
    }

    /** 详情视图：商品、一级与二级分类、有序相册。 */
    public record ProductDetailView(Product product, Category line, Category second,
                                    List<GalleryImage> gallery) {

        /** 纯文本详情按换行拆段，模板逐段转义输出，不执行富文本脚本。 */
        public List<String> descriptionLines() {
            String description = product.getDescription();
            if (description == null || description.isBlank()) {
                return List.of();
            }
            return Arrays.stream(description.split("\\R"))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .toList();
        }

        public boolean soldOut() {
            return product.getStock() == null || product.getStock() == 0;
        }
    }

    private final ProductMapper productMapper;
    private final CategoryMapper categoryMapper;
    private final ProductImageMapper productImageMapper;

    public DetailService(ProductMapper productMapper, CategoryMapper categoryMapper,
                         ProductImageMapper productImageMapper) {
        this.productMapper = productMapper;
        this.categoryMapper = categoryMapper;
        this.productImageMapper = productImageMapper;
    }

    public ProductDetailView detail(String rawId) {
        long id = parseId(rawId);
        Product product = productMapper.selectById(id);
        if (product == null || !ProductService.STATUS_ON_SALE.equals(product.getStatus())) {
            throw new NotFoundException("商品不存在或已下架", "该商品不存在或已下架，可返回商品列表浏览其他商品");
        }
        Category second = categoryMapper.selectById(product.getCategoryId());
        Category line = second == null ? null : categoryMapper.selectById(second.getParentId());
        if (line == null || second == null) {
            throw new NotFoundException("商品分类不可用");
        }
        return new ProductDetailView(product, line, second, galleryOf(product));
    }

    /**
     * 搭配推荐：同二级分类的在售商品，按排序位与 ID 升序，排除当前款。
     * 数量取不足时返回空列表，由模板跳过整段。
     */
    public List<Product> related(Product product, int limit) {
        if (product == null || product.getCategoryId() == null) {
            return List.of();
        }
        List<Product> rows = productMapper.selectList(new LambdaQueryWrapper<Product>()
                .eq(Product::getCategoryId, product.getCategoryId())
                .eq(Product::getStatus, ProductService.STATUS_ON_SALE)
                .ne(Product::getId, product.getId())
                .orderByAsc(Product::getSortOrder)
                .orderByAsc(Product::getId)
                .last("LIMIT " + Math.max(1, limit)));
        return rows == null ? List.of() : rows;
    }

    /** 相册按 sort_order、id 升序；无相册回退封面，封面缺失再用缺省封面。 */    private List<GalleryImage> galleryOf(Product product) {
        List<GalleryImage> album = productImageMapper.selectList(
                        new LambdaQueryWrapper<ProductImage>()
                                .eq(ProductImage::getProductId, product.getId())
                                .orderByAsc(ProductImage::getSortOrder)
                                .orderByAsc(ProductImage::getId))
                .stream()
                .map(image -> new GalleryImage(image.getImagePath(), image.getAltText()))
                .toList();
        if (!album.isEmpty()) {
            return album;
        }
        String cover = product.getCoverImage();
        String path = cover == null || cover.isBlank() ? ProductService.DEFAULT_COVER : cover;
        return List.of(new GalleryImage(path, product.getName()));
    }

    /** 商品 ID 定位：未传、非整数或溢出返回参数错误；格式合法但不存在走 404。 */
    static long parseId(String rawId) {
        if (rawId == null || rawId.isBlank()) {
            throw new BusinessException("缺少商品 ID");
        }
        try {
            return Long.parseLong(rawId.trim());
        } catch (NumberFormatException e) {
            throw new BusinessException("商品参数必须是整数 ID");
        }
    }
}
