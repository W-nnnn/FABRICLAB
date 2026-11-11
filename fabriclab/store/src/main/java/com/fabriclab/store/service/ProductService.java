package com.fabriclab.store.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fabriclab.store.entity.Category;
import com.fabriclab.store.entity.Product;
import com.fabriclab.store.exception.BusinessException;
import com.fabriclab.store.mapper.CategoryMapper;
import com.fabriclab.store.mapper.ProductMapper;
import com.fabriclab.store.model.SingleSkuText;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Set;

/**
 * 单 SKU 商品模型：款号 sku 全局唯一且创建后不可修改；
 * 商品必须归属二级分类；写操作显式递增 version，防止旧值覆盖。
 */
@Service
public class ProductService {

    public static final String STATUS_DRAFT = "DRAFT";
    public static final String STATUS_ON_SALE = "ON_SALE";
    public static final String DEFAULT_COVER = "images/logo.png";

    private static final Set<String> VALID_STATUSES = Set.of(STATUS_DRAFT, STATUS_ON_SALE);

    private final ProductMapper productMapper;
    private final CategoryMapper categoryMapper;

    public ProductService(ProductMapper productMapper, CategoryMapper categoryMapper) {
        this.productMapper = productMapper;
        this.categoryMapper = categoryMapper;
    }

    public Product create(Product product) {
        String sku = requireText(product.getSku(), "款号不能为空");
        requirePrice(product.getPrice());
        requireStock(product.getStock());
        if (product.getSortOrder() != null && product.getSortOrder() < 0) {
            throw new BusinessException("排序值不能为负");
        }
        String status = product.getStatus() == null ? STATUS_DRAFT : product.getStatus().trim();
        if (!VALID_STATUSES.contains(status)) {
            throw new BusinessException("商品状态只能是 DRAFT 或 ON_SALE");
        }
        requireSecondLevelCategory(product.getCategoryId());
        requireSkuUnique(sku, null);

        product.setSku(sku);
        product.setStatus(status);
        if (product.getCoverImage() == null || product.getCoverImage().isBlank()) {
            product.setCoverImage(DEFAULT_COVER);
        }
        if (product.getSortOrder() == null) {
            product.setSortOrder(0);
        }
        if (product.getVersion() == null) {
            product.setVersion(0L);
        }
        productMapper.insert(product);
        return product;
    }

    /** 款号与分类归属创建后不可变；写操作 version 加一。 */
    public Product update(Product patch) {
        Long id = patch.getId();
        Product existing = requireExisting(id);
        if (patch.getSku() != null && !patch.getSku().trim().equals(existing.getSku())) {
            throw new BusinessException("款号创建后不可修改");
        }
        if (patch.getCategoryId() != null && !patch.getCategoryId().equals(existing.getCategoryId())) {
            throw new BusinessException("分类归属创建后不可修改");
        }
        if (patch.getPrice() != null) {
            requirePrice(patch.getPrice());
            existing.setPrice(patch.getPrice());
        }
        if (patch.getStock() != null) {
            requireStock(patch.getStock());
            existing.setStock(patch.getStock());
        }
        if (patch.getName() != null) {
            existing.setName(SingleSkuText.variantName(patch.getName(), null, null));
        }
        if (patch.getSummary() != null) {
            existing.setSummary(SingleSkuText.summary(patch.getSummary()));
        }
        if (patch.getDescription() != null) {
            existing.setDescription(patch.getDescription().trim());
        }
        if (patch.getCoverImage() != null && !patch.getCoverImage().isBlank()) {
            existing.setCoverImage(patch.getCoverImage().trim());
        }
        if (patch.getStatus() != null) {
            String status = patch.getStatus().trim();
            if (!VALID_STATUSES.contains(status)) {
                throw new BusinessException("商品状态只能是 DRAFT 或 ON_SALE");
            }
            existing.setStatus(status);
        }
        if (patch.getSortOrder() != null) {
            if (patch.getSortOrder() < 0) {
                throw new BusinessException("排序值不能为负");
            }
            existing.setSortOrder(patch.getSortOrder());
        }
        existing.setVersion(existing.getVersion() + 1);
        productMapper.updateById(existing);
        return existing;
    }

    private void requireSecondLevelCategory(Long categoryId) {
        if (categoryId == null) {
            throw new BusinessException("缺少商品分类");
        }
        Category category = categoryMapper.selectById(categoryId);
        if (category == null) {
            throw new BusinessException("分类不存在");
        }
        if (category.getParentId() == null) {
            throw new BusinessException("商品必须归属二级分类");
        }
    }

    private void requireSkuUnique(String sku, Long excludeId) {
        LambdaQueryWrapper<Product> query = new LambdaQueryWrapper<Product>()
                .eq(Product::getSku, sku)
                .ne(excludeId != null, Product::getId, excludeId);
        if (productMapper.selectCount(query) > 0) {
            throw new BusinessException("款号已存在：" + sku);
        }
    }

    private void requirePrice(BigDecimal price) {
        if (price == null || price.signum() <= 0) {
            throw new BusinessException("价格必须大于 0");
        }
    }

    private void requireStock(Integer stock) {
        if (stock == null || stock < 0) {
            throw new BusinessException("库存不能为负");
        }
    }

    private Product requireExisting(Long id) {
        if (id == null) {
            throw new BusinessException("缺少商品 ID");
        }
        Product existing = productMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException("商品不存在");
        }
        return existing;
    }

    private String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(message);
        }
        return value.trim();
    }
}
