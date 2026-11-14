package com.fabriclab.store.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fabriclab.store.entity.Category;
import com.fabriclab.store.entity.Product;
import com.fabriclab.store.exception.BusinessException;
import com.fabriclab.store.exception.NotFoundException;
import com.fabriclab.store.mapper.CategoryMapper;
import com.fabriclab.store.mapper.ProductMapper;
import com.fabriclab.store.model.CareGuide;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 服装类扩展（T17，可选交付）：面料档案、工艺说明、系列专题页。
 * 全部只读在售商品与分类，不新增表、不改 DDL、不触碰任何交易路径；
 * 页面缺失或空数据只影响扩展页自身，不影响 AC-01~32。
 */
@Service
public class SeriesService {

    /** 面料档案：按纤维四类归组的在售款。 */
    public record FabricGroup(String titleKey, String introKey, String careKey, List<Product> products) {
    }

    /** 系列专题：一个一级系列下，按二级分类归组的在售款。 */
    public record SeriesGroup(Category second, List<Product> products) {
    }

    /** 系列专题视图：系列名与分组、在售总数。 */
    public record SeriesView(Category line, List<SeriesGroup> groups, int total) {
    }

    private final ProductMapper productMapper;
    private final CategoryMapper categoryMapper;

    public SeriesService(ProductMapper productMapper, CategoryMapper categoryMapper) {
        this.productMapper = productMapper;
        this.categoryMapper = categoryMapper;
    }

    /** 面料档案：在售商品按详情描述归到四类纤维，与详情页洗护建议同一套判定。 */
    public List<FabricGroup> fabrics() {
        List<Product> onSale = onSaleProducts();
        List<Product> silk = new ArrayList<>(), wool = new ArrayList<>(),
                cotton = new ArrayList<>(), other = new ArrayList<>();
        for (Product p : onSale) {
            switch (CareGuide.keyOf(p)) {
                case CareGuide.KEY_SILK -> silk.add(p);
                case CareGuide.KEY_WOOL -> wool.add(p);
                case CareGuide.KEY_COTTON -> cotton.add(p);
                default -> other.add(p);
            }
        }
        List<FabricGroup> groups = new ArrayList<>();
        groups.add(new FabricGroup("fabrics.silk.t", "fabrics.silk.d", CareGuide.KEY_SILK, silk));
        groups.add(new FabricGroup("fabrics.wool.t", "fabrics.wool.d", CareGuide.KEY_WOOL, wool));
        groups.add(new FabricGroup("fabrics.cotton.t", "fabrics.cotton.d", CareGuide.KEY_COTTON, cotton));
        groups.add(new FabricGroup("fabrics.other.t", "fabrics.other.d", CareGuide.KEY_OTHER, other));
        return groups;
    }

    /** 工艺说明页的在售款条：排序位与 ID 升序，取前 8 件。 */
    public List<Product> craftSamples(int limit) {
        return onSaleProducts().stream().limit(limit).toList();
    }

    /** 系列专题：只认一级系列；非数字 400，不存在或二级分类 404。 */
    public SeriesView series(String rawId) {
        long id;
        try {
            id = Long.parseLong(rawId == null ? "" : rawId.trim());
        } catch (NumberFormatException e) {
            throw new BusinessException("系列参数不合法");
        }
        Category line = categoryMapper.selectById(id);
        if (line == null || line.getParentId() != null) {
            throw new NotFoundException("系列不存在", "该系列不存在，可从页脚「选购」进入现有系列");
        }
        List<Category> seconds = categoryMapper.selectList(new LambdaQueryWrapper<Category>()
                .eq(Category::getParentId, id)
                .orderByAsc(Category::getSortOrder).orderByAsc(Category::getId));
        List<SeriesGroup> groups = new ArrayList<>();
        int total = 0;
        for (Category second : seconds) {
            List<Product> products = productMapper.selectList(new LambdaQueryWrapper<Product>()
                    .eq(Product::getCategoryId, second.getId())
                    .eq(Product::getStatus, ProductService.STATUS_ON_SALE)
                    .orderByAsc(Product::getSortOrder).orderByAsc(Product::getId));
            groups.add(new SeriesGroup(second, products));
            total += products.size();
        }
        return new SeriesView(line, groups, total);
    }

    private List<Product> onSaleProducts() {
        return productMapper.selectList(new LambdaQueryWrapper<Product>()
                .eq(Product::getStatus, ProductService.STATUS_ON_SALE)
                .orderByAsc(Product::getSortOrder).orderByAsc(Product::getId));
    }
}
