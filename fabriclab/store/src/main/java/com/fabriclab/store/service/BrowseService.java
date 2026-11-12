package com.fabriclab.store.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fabriclab.store.entity.Category;
import com.fabriclab.store.entity.Product;
import com.fabriclab.store.exception.NotFoundException;
import com.fabriclab.store.mapper.CategoryMapper;
import com.fabriclab.store.mapper.ProductMapper;
import com.fabriclab.store.model.BrowseQuery;
import com.fabriclab.store.model.CategoryType;
import com.fabriclab.store.model.SortOption;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 游客浏览：首页两栏（新品上架 ≤3、精选推荐 ≤4，仅上架商品，两栏允许重复不去重）
 * 与商品列表（分类作用域、名称包含匹配、排序白名单、后端分页）。
 * 类型三值由二级分类编码后缀映射，不新增表字段。
 */
@Service
public class BrowseService {

    public static final int HOME_NEW_LIMIT = 3;
    public static final int HOME_FEATURED_LIMIT = 4;

    /** 一级系列线及其二级分类（按 sort_order、id 升序）。 */
    public record CategoryBranch(Category line, List<Category> children) {
    }

    /** 首页数据：分类导航、新品上架、精选推荐。 */
    public record HomeView(List<CategoryBranch> lines, List<Product> newArrivals, List<Product> featured) {
    }

    /** 列表页分类作用域：categoryIds 为空表示不限分类。 */
    public record CategoryScope(Category selectedLine, Category entry, CategoryType selectedType,
                                boolean entryIsSecondLevel, List<Long> categoryIds) {

        /** 类型按钮文案：全部 / 成衣 / 配饰。 */
        public String typeLabel() {
            return switch (selectedType) {
                case ALL -> "全部";
                case GARMENTS -> "成衣";
                case ACCESSORIES -> "配饰";
            };
        }
    }

    /** 列表查询结果。 */
    public record BrowseResult(CategoryScope scope, Page<Product> page) {
    }

    private final ProductMapper productMapper;
    private final CategoryMapper categoryMapper;

    public BrowseService(ProductMapper productMapper, CategoryMapper categoryMapper) {
        this.productMapper = productMapper;
        this.categoryMapper = categoryMapper;
    }

    /** 首页：分类树 + 新品上架（created_at、id 倒序 ≤3）+ 精选推荐（sort_order、id 升序 ≤4）。 */
    public HomeView homeView() {
        List<Product> newArrivals = productMapper.selectPage(
                new Page<>(1, HOME_NEW_LIMIT, false),
                onSale().orderByDesc(Product::getCreatedAt).orderByDesc(Product::getId)).getRecords();
        List<Product> featured = productMapper.selectPage(
                new Page<>(1, HOME_FEATURED_LIMIT, false),
                onSale().orderByAsc(Product::getSortOrder).orderByAsc(Product::getId)).getRecords();
        return new HomeView(categoryTree(), newArrivals, featured);
    }

    /** 全部分类树：一级系列线列表，各带二级列表。无分类时返回空列表，不渲染伪分类。 */
    public List<CategoryBranch> categoryTree() {
        List<Category> all = categoryMapper.selectList(new LambdaQueryWrapper<Category>()
                .orderByAsc(Category::getSortOrder).orderByAsc(Category::getId));
        return all.stream()
                .filter(c -> c.getParentId() == null)
                .map(line -> new CategoryBranch(line, childrenOf(line.getId())))
                .toList();
    }

    /** 商品列表：仅上架商品，零库存仍出现在列表中。 */
    public BrowseResult browse(BrowseQuery query) {
        CategoryScope scope = resolveScope(query);
        LambdaQueryWrapper<Product> wrapper = onSale();
        if (!scope.categoryIds().isEmpty()) {
            wrapper.in(Product::getCategoryId, scope.categoryIds());
        }
        if (query.keyword() != null) {
            wrapper.apply("name LIKE CONCAT('%', {0}, '%') ESCAPE '\\\\'", escapeLike(query.keyword()));
        }
        applySort(wrapper, query.sort());
        Page<Product> page = productMapper.selectPage(new Page<>(query.page(), query.size()), wrapper);
        return new BrowseResult(scope, page);
    }

    /**
     * 分类作用域：无分类时类型筛选跨五条系列线生效；一级限定其二级范围；
     * 二级入口缺省限定该二级并选中相应类型；显式切换类型时在该大分类内查询，
     * 不与旧二级 ID 形成互斥条件。
     */
    CategoryScope resolveScope(BrowseQuery query) {
        if (query.categoryId() == null) {
            return new CategoryScope(null, null, query.productType(), false,
                    query.productType() == CategoryType.ALL
                            ? List.of()
                            : secondLevelIdsByType(query.productType()));
        }
        Category entry = categoryMapper.selectById(query.categoryId());
        if (entry == null) {
            throw new NotFoundException("分类不可用");
        }
        if (entry.getParentId() == null) {
            return new CategoryScope(entry, entry, query.productType(), false,
                    filterIds(childrenOf(entry.getId()), query.productType()));
        }
        Category parent = categoryMapper.selectById(entry.getParentId());
        if (parent == null) {
            throw new NotFoundException("分类不可用");
        }
        CategoryType entryType = CategoryType.fromCode(entry.getCode());
        if (!query.productTypeExplicit() || query.productType() == entryType) {
            return new CategoryScope(parent, entry, entryType, true, List.of(entry.getId()));
        }
        return new CategoryScope(parent, entry, query.productType(), true,
                filterIds(childrenOf(parent.getId()), query.productType()));
    }

    /** 名称包含匹配：百分号、下划线、反斜杠按普通字符处理，不作为通配符。 */
    static String escapeLike(String keyword) {
        return keyword.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private LambdaQueryWrapper<Product> onSale() {
        return new LambdaQueryWrapper<Product>().eq(Product::getStatus, ProductService.STATUS_ON_SALE);
    }

    private void applySort(LambdaQueryWrapper<Product> wrapper, SortOption sort) {
        switch (sort) {
            case PRICE_ASC -> wrapper.orderByAsc(Product::getPrice).orderByAsc(Product::getId);
            case PRICE_DESC -> wrapper.orderByDesc(Product::getPrice).orderByAsc(Product::getId);
            case NEWEST -> wrapper.orderByDesc(Product::getCreatedAt).orderByDesc(Product::getId);
            case DEFAULT -> wrapper.orderByAsc(Product::getSortOrder).orderByAsc(Product::getId);
        }
    }

    private List<Long> secondLevelIdsByType(CategoryType type) {
        List<Category> secondLevels = categoryMapper.selectList(new LambdaQueryWrapper<Category>()
                .isNotNull(Category::getParentId));
        return filterIds(secondLevels, type);
    }

    private List<Long> filterIds(List<Category> categories, CategoryType type) {
        if (type == CategoryType.ALL) {
            return categories.stream().map(Category::getId).toList();
        }
        return categories.stream()
                .filter(c -> CategoryType.fromCode(c.getCode()) == type)
                .map(Category::getId)
                .toList();
    }

    private List<Category> childrenOf(Long parentId) {
        return categoryMapper.selectList(new LambdaQueryWrapper<Category>()
                .eq(Category::getParentId, parentId)
                .orderByAsc(Category::getSortOrder).orderByAsc(Category::getId));
    }
}
