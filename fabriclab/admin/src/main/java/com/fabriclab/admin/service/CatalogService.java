package com.fabriclab.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fabriclab.admin.entity.Category;
import com.fabriclab.admin.entity.Product;
import com.fabriclab.admin.exception.BusinessException;
import com.fabriclab.admin.exception.NotFoundException;
import com.fabriclab.admin.mapper.CategoryMapper;
import com.fabriclab.admin.mapper.ProductMapper;
import com.fabriclab.admin.model.CategoryForm;
import com.fabriclab.admin.model.CategoryNode;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 两级分类维护（UC-A3、FR-A-02、AC-25/AC-26）。
 * 编码全局唯一、编码与父级创建后不可变、同级同名冲突、禁止三级分类；
 * 有子分类或有商品引用时拒绝删除——删除保护是 AC-26 的落点。
 */
@Service
public class CatalogService {

    private final CategoryMapper categoryMapper;
    private final ProductMapper productMapper;
    private final MessageSource messages;

    public CatalogService(CategoryMapper categoryMapper, ProductMapper productMapper,
                          MessageSource messages) {
        this.categoryMapper = categoryMapper;
        this.productMapper = productMapper;
        this.messages = messages;
    }

    private String t(String key) {
        return messages.getMessage(key, null, key, LocaleContextHolder.getLocale());
    }

    /** 分类树：一级携带二级；款数为当次查询的真实计数，无数据时为 0。 */
    public List<CategoryNode> tree() {
        List<Category> all = categoryMapper.selectList(new LambdaQueryWrapper<Category>()
                .orderByAsc(Category::getSortOrder).orderByAsc(Category::getId));
        List<Product> refs = productMapper.selectList(
                new LambdaQueryWrapper<Product>().select(Product::getCategoryId));
        Map<Long, Long> counts = new HashMap<>();
        for (Product p : refs) {
            if (p.getCategoryId() != null) counts.merge(p.getCategoryId(), 1L, Long::sum);
        }
        Map<Long, CategoryNode> byId = new LinkedHashMap<>();
        for (Category c : all) {
            byId.put(c.getId(), new CategoryNode(c.getId(), c.getCode(), c.getName(),
                    c.getSortOrder() == null ? 0 : c.getSortOrder(),
                    counts.getOrDefault(c.getId(), 0L), c.getParentId() == null));
        }
        List<CategoryNode> roots = new ArrayList<>();
        for (Category c : all) {
            CategoryNode node = byId.get(c.getId());
            if (c.getParentId() == null) {
                roots.add(node);
            } else {
                CategoryNode parent = byId.get(c.getParentId());
                if (parent != null) parent.getChildren().add(node);
            }
        }
        return roots;
    }

    /** 商品表单可选分类：只返回二级。 */
    public List<Category> secondLevels() {
        return categoryMapper.selectList(new LambdaQueryWrapper<Category>()
                .isNotNull(Category::getParentId)
                .orderByAsc(Category::getSortOrder).orderByAsc(Category::getId));
    }

    public Category require(long id) {
        Category c = categoryMapper.selectById(id);
        if (c == null) throw new NotFoundException(t("admin.err.cat.notFound"));
        return c;
    }

    public Category create(CategoryForm f) {
        String code = text(f.code(), "admin.err.cat.codeRequired", 32, "admin.err.cat.codeLength");
        String name = text(f.name(), "admin.err.cat.nameRequired", 50, "admin.err.cat.nameLength");
        int sort = sort(f.sortOrder());
        Long parentId = f.parentId();
        if (parentId != null) {
            Category parent = categoryMapper.selectById(parentId);
            if (parent == null) throw new BusinessException(t("admin.err.cat.parentMissing"));
            if (parent.getParentId() != null) {
                throw new BusinessException(t("admin.err.cat.parentLevel"));
            }
        }
        if (codeExists(code, null)) throw new BusinessException(t("admin.err.cat.codeExists"));
        if (siblingNameExists(name, parentId, null)) {
            throw new BusinessException(t("admin.err.cat.nameExists"));
        }
        Category c = new Category();
        c.setCode(code);
        c.setParentId(parentId);
        c.setName(name);
        c.setSortOrder(sort);
        categoryMapper.insert(c);
        return c;
    }

    public Category update(long id, CategoryForm f) {
        Category existing = require(id);
        String code = text(f.code(), "admin.err.cat.codeRequired", 32, "admin.err.cat.codeLength");
        String name = text(f.name(), "admin.err.cat.nameRequired", 50, "admin.err.cat.nameLength");
        if (!code.equals(existing.getCode()) || !Objects.equals(f.parentId(), existing.getParentId())) {
            throw new BusinessException(t("admin.err.cat.immutable"));
        }
        int sort = sort(f.sortOrder());
        if (siblingNameExists(name, existing.getParentId(), id)) {
            throw new BusinessException(t("admin.err.cat.nameExists"));
        }
        existing.setName(name);
        existing.setSortOrder(sort);
        categoryMapper.updateById(existing);
        return existing;
    }

    /** AC-26：有子分类或有商品引用时拒绝删除，不级联、不静默迁移。 */
    public void delete(long id) {
        require(id);
        Long children = categoryMapper.selectCount(new LambdaQueryWrapper<Category>()
                .eq(Category::getParentId, id));
        if (children != null && children > 0) {
            throw new BusinessException(t("admin.err.cat.hasChildren"));
        }
        Long products = productMapper.selectCount(new LambdaQueryWrapper<Product>()
                .eq(Product::getCategoryId, id));
        if (products != null && products > 0) {
            throw new BusinessException(t("admin.err.cat.hasProducts"));
        }
        categoryMapper.deleteById(id);
    }

    private boolean codeExists(String code, Long excludeId) {
        Long n = categoryMapper.selectCount(new LambdaQueryWrapper<Category>()
                .eq(Category::getCode, code)
                .ne(excludeId != null, Category::getId, excludeId));
        return n != null && n > 0;
    }

    private boolean siblingNameExists(String name, Long parentId, Long excludeId) {
        LambdaQueryWrapper<Category> w = new LambdaQueryWrapper<Category>()
                .eq(Category::getName, name);
        if (parentId == null) w.isNull(Category::getParentId);
        else w.eq(Category::getParentId, parentId);
        w.ne(excludeId != null, Category::getId, excludeId);
        Long n = categoryMapper.selectCount(w);
        return n != null && n > 0;
    }

    private String text(String raw, String requiredKey, int max, String lengthKey) {
        String value = raw == null ? "" : raw.trim();
        if (value.isEmpty()) throw new BusinessException(t(requiredKey));
        if (value.length() > max) throw new BusinessException(t(lengthKey));
        return value;
    }

    private int sort(Integer value) {
        int v = value == null ? 0 : value;
        if (v < 0) throw new BusinessException(t("admin.err.cat.sortNegative"));
        return v;
    }
}
